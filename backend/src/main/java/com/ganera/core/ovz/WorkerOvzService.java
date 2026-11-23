package com.ganera.core.ovz;

import com.ganera.core.ganadero.CredencialOvzDeOtraGestoriaException;
import com.ganera.core.ganadero.CredencialOvzEnClaro;
import com.ganera.core.ganadero.DescifradoCredencialOvz;
import com.ganera.core.ganadero.DescifradoCredencialOvzException;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.tramite.DatosPresentacionInvalidosException;
import com.ganera.core.tramite.ModoWorkerOvz;
import com.ganera.core.tramite.TipoErrorOvz;
import com.ganera.core.tramite.TipoTrabajoOvz;
import com.ganera.core.tramite.TrabajoOvz;
import com.ganera.core.tramite.TrabajoOvzService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Orquestador del worker de OVZ (escalón 2, T4; plan 2026-11-17): presenta UNA baja por muerte cada vez. Solo existe
 * en el perfil {@code worker} ({@code ganera.ovz.worker.activo=true}).
 *
 * <p><b>Reglas que no se negocian</b>:
 * <ul>
 *   <li>El modo por defecto es {@link ModoWorkerOvz#ENSAYO}: recorre todo hasta ANTES de «Aceptar», sale con «Salir» y
 *       NUNCA llama a {@link NavegadorOvz#pulsarAceptar()}.</li>
 *   <li>Nunca se abre OVZ dentro de una transacción ni con el bloqueo de fila del trámite: cada escritura de estado es
 *       una transacción corta en {@link TrabajoOvzService}; este servicio NO es {@code @Transactional}.</li>
 *   <li>La contraseña se descifra solo a través de {@link DescifradoCredencialOvz} (fuera de toda transacción).</li>
 *   <li>Un fallo ANTES de «Aceptar» es {@code SEGURO} (no se presentó nada). Un fallo DESPUÉS es {@code INCIERTO}
 *       salvo que Documentos confirme la baja; la presentación NUNCA se reintenta (anti-duplicado).</li>
 *   <li>Logs: solo ids del trabajo, pasos y códigos; nunca crotal, REGA, usuario, contraseña ni contenido. El motivo
 *       del error va al trabajo (texto para el gestor), jamás a logs ni a auditoría.</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ganera.ovz.worker.activo", havingValue = "true")
public class WorkerOvzService {

    static final String MOTIVO_FALTAN_CREDENCIALES = "Faltan las credenciales de OVZ del ganadero.";
    static final String MOTIVO_CREDENCIAL_ILEGIBLE = "No se pudieron descifrar las credenciales de OVZ del ganadero.";

    // Motivos de cara al gestor, POR TIPO de trámite (H1): un alta nunca debe decir «la baja». «el alta»/«la baja»
    // ("el alta": sustantivo femenino con artículo «el» por empezar por /a/ tónica).
    static String motivoGenerico(TipoTrabajoOvz tipo) {
        return "Error al presentar " + nombreTramite(tipo) + " en OVZ.";
    }

    static String motivoIncierto(TipoTrabajoOvz tipo) {
        return (tipo == TipoTrabajoOvz.ALTA_NACIMIENTO ? "El alta" : "La baja")
                + " pudo presentarse en OVZ pero no se pudo confirmar. Comprueba en OVZ si está presentada antes de reencolar.";
    }

    static String motivoNoRegistrada(TipoTrabajoOvz tipo) {
        return (tipo == TipoTrabajoOvz.ALTA_NACIMIENTO ? "El alta" : "La baja")
                + " se presentó en OVZ pero no se pudo registrar el resultado. Comprueba el estado en OVZ.";
    }

    private static String nombreTramite(TipoTrabajoOvz tipo) {
        return tipo == TipoTrabajoOvz.ALTA_NACIMIENTO ? "el alta" : "la baja";
    }

    /** Tolerancia por si el reloj de OVZ y el nuestro no coinciden al filtrar los documentos creados tras el clic. */
    private static final Duration TOLERANCIA_RELOJ = Duration.ofMinutes(2);

    private final NavegadorOvz navegador;
    private final TrabajoOvzService trabajoOvzService;
    private final GanaderoRepository ganaderoRepository;
    private final DescifradoCredencialOvz descifradoCredencialOvz;
    private final Clock clock;
    private final String urlOvz;
    private final ModoWorkerOvz modo;
    private final int intentosDescarga;
    private final Duration pausaDescarga;

    public WorkerOvzService(NavegadorOvz navegador,
                            TrabajoOvzService trabajoOvzService,
                            GanaderoRepository ganaderoRepository,
                            DescifradoCredencialOvz descifradoCredencialOvz,
                            Clock clock,
                            @Value("${ganera.ovz.worker.url:}") String urlOvz,
                            @Value("${ganera.ovz.worker.modo:ENSAYO}") String modo,
                            @Value("${ganera.ovz.worker.descarga-intentos:3}") int intentosDescarga,
                            @Value("${ganera.ovz.worker.descarga-pausa:PT3S}") Duration pausaDescarga) {
        this.navegador = navegador;
        this.trabajoOvzService = trabajoOvzService;
        this.ganaderoRepository = ganaderoRepository;
        this.descifradoCredencialOvz = descifradoCredencialOvz;
        this.clock = clock;
        this.urlOvz = urlOvz;
        this.modo = ModoWorkerOvz.desde(modo);
        this.intentosDescarga = Math.max(1, intentosDescarga);
        this.pausaDescarga = pausaDescarga == null ? Duration.ZERO : pausaDescarga;
        log.info("Worker OVZ iniciado en modo {}", this.modo);
    }

    /** Procesa como mucho un trabajo PENDIENTE. Sin trabajo, no hace nada. Despacha por tipo (baja/alta). */
    public void procesarUno() {
        Optional<TrabajoOvz> reclamado = trabajoOvzService.reclamarSiguiente();
        if (reclamado.isEmpty()) {
            return;
        }
        Long trabajoId = reclamado.get().getId();
        Long gestoriaId = reclamado.get().getGestoria().getId();
        TipoTrabajoOvz tipo = reclamado.get().getTipo();
        log.info("Worker OVZ: trabajo {} reclamado ({})", trabajoId, tipo);
        if (tipo == TipoTrabajoOvz.ALTA_NACIMIENTO) {
            presentarAlta(trabajoId, gestoriaId);
        } else {
            presentarBaja(trabajoId, gestoriaId);
        }
    }

    private void presentarBaja(Long trabajoId, Long gestoriaId) {
        // 1) Datos y credenciales: todo ANTES de abrir OVZ; cualquier fallo aquí es SEGURO (no se tocó OVZ).
        DatosPresentacionBaja datos;
        try {
            datos = trabajoOvzService.datosPresentacion(trabajoId, gestoriaId);
        } catch (DatosPresentacionInvalidosException e) {
            // Motivo pensado para el gestor (texto fijo, sin datos): se muestra tal cual.
            errorSeguro(trabajoId, gestoriaId, e.getMessage());
            return;
        } catch (RuntimeException e) {
            // Fallo técnico inesperado (BD, lazy-init...): su mensaje podría ser técnico; se usa el genérico.
            log.warn("Worker OVZ: trabajo {} no se pudieron preparar los datos ({})", trabajoId,
                    e.getClass().getSimpleName());
            errorSeguro(trabajoId, gestoriaId, motivoGenerico(TipoTrabajoOvz.BAJA_MUERTE));
            return;
        }
        if (datos.usaCertificado()) {
            errorSeguro(trabajoId, gestoriaId, new LoginCertificadoException().getMessage());
            return;
        }
        CredencialOvzEnClaro clave = descifrar(datos.ganaderoId(), datos.usuarioOvz(), trabajoId, gestoriaId);
        if (clave == null) {
            return;
        }

        // 2) Sesión OVZ (fuera de toda transacción).
        boolean aceptarPulsado = false;
        boolean cerrada = false;
        Instant instanteClic = null;
        try {
            navegador.iniciarSesion(urlOvz, datos.usuarioOvz(), clave);
            navegador.prepararBaja(datos.crotal());
            ResultadoVerificacion verificacion = VerificacionBaja.verificar(
                    navegador.leerCodigoRiia(), datos.crotal(), navegador.leerExplotacionUbicacion(), datos.rega());
            if (!verificacion.ok()) {
                throw new NavegacionOvzException(verificacion.motivo());
            }
            navegador.rellenarFechaMuerte(datos.fechaMuerteDdMmAaaa());
            navegador.rellenarMer(datos.numeroMer());

            if (modo == ModoWorkerOvz.ENSAYO) {
                // ENSAYO: hasta aquí y nada más. NUNCA se llega a pulsarAceptar. La sesión de OVZ SIEMPRE se cierra,
                // igual que en real: dejarla abierta con las credenciales del ganadero sería una fuga.
                navegador.salir();
                cerrada = true;
                // ENSAYO: tras PASO 7 (MER) se sale sin pasar por «Aceptar» (PASO 8 en real) ni Documentos (PASO 9).
                log.info("PASO 8 OK: trabajo {} sesión de OVZ cerrada (ensayo, no se presentó nada)", trabajoId);
                trabajoOvzService.finalizarEnsayo(trabajoId, gestoriaId);
                return;
            }

            aceptarPulsado = true;
            instanteClic = clock.instant().minus(TOLERANCIA_RELOJ);
            navegador.pulsarAceptar();
            Optional<Justificante> justificante =
                    buscarJustificante(navegador::listarDocumentosBajaAnimal, datos.crotal(), instanteClic).justificante();
            navegador.salir();
            cerrada = true;
            // REAL: «Aceptar» (PASO 8) y Documentos (PASO 9) ya han ocurrido; el cierre de sesión es el PASO 10.
            log.info("PASO 10 OK: trabajo {} sesión de OVZ cerrada", trabajoId);
            registrarEjecutado(trabajoId, gestoriaId, TipoTrabajoOvz.BAJA_MUERTE, justificante);
        } catch (AceptarException e) {
            // «Aceptar» no cuadró con lo esperado y NO se pulsó (la excepción se lanza antes del clic).
            cerrarSinFallar();
            cerrada = true;
            errorSeguro(trabajoId, gestoriaId, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Worker OVZ: trabajo {} falló ({})", trabajoId, e.getClass().getSimpleName());
            if (!aceptarPulsado) {
                // ANTES de «Aceptar»: no se presentó nada.
                cerrarSinFallar();
                cerrada = true;
                errorSeguro(trabajoId, gestoriaId, motivoDe(e, TipoTrabajoOvz.BAJA_MUERTE));
            } else {
                // DESDE «Aceptar»: la presentación NO se reintenta. Se consulta Documentos para saber si ocurrió.
                resolverIncierto(trabajoId, gestoriaId, TipoTrabajoOvz.BAJA_MUERTE,
                        navegador::listarDocumentosBajaAnimal, datos.crotal(), instanteClic);
                cerrada = true;
            }
        } finally {
            if (!cerrada) {
                cerrarSinFallar();
            }
        }
    }

    /**
     * Presenta UN alta por nacimiento (plan 2026-11-23, T4). Mismo esqueleto que la baja: datos y credenciales
     * ANTES de abrir OVZ (fallo aquí = SEGURO); sesión fuera de toda transacción; ENSAYO por defecto (recorre hasta
     * ANTES de «Aceptar» y sale); REAL pulsa «Aceptar» una sola vez y NO reintenta ante respuesta incierta. El
     * justificante «Alta Bovino» es best-effort: su ausencia NO marca el alta como fallida (D3).
     */
    private void presentarAlta(Long trabajoId, Long gestoriaId) {
        DatosPresentacionAlta datos;
        try {
            datos = trabajoOvzService.datosPresentacionAlta(trabajoId, gestoriaId);
        } catch (DatosPresentacionInvalidosException e) {
            errorSeguro(trabajoId, gestoriaId, e.getMessage());
            return;
        } catch (RuntimeException e) {
            log.warn("Worker OVZ: trabajo {} no se pudieron preparar los datos del alta ({})", trabajoId,
                    e.getClass().getSimpleName());
            errorSeguro(trabajoId, gestoriaId, motivoGenerico(TipoTrabajoOvz.ALTA_NACIMIENTO));
            return;
        }
        if (datos.usaCertificado()) {
            errorSeguro(trabajoId, gestoriaId, new LoginCertificadoException().getMessage());
            return;
        }
        CredencialOvzEnClaro clave = descifrar(datos.ganaderoId(), datos.usuarioOvz(), trabajoId, gestoriaId);
        if (clave == null) {
            return;
        }

        boolean aceptarPulsado = false;
        boolean cerrada = false;
        Instant instanteClic = null;
        try {
            navegador.iniciarSesion(urlOvz, datos.usuarioOvz(), clave);
            // prepararAlta localiza el crotal del ternero «Libre» en la tabla de repartidos y lo pulsa; si no está o
            // no está libre lanza NavegacionOvzException (SEGURO, no se presentó nada).
            navegador.prepararAlta(datos.crotalTernero());
            ResultadoVerificacion verificacion =
                    VerificacionAlta.verificar(navegador.leerCrotalSeleccionado(), datos.crotalTernero());
            if (!verificacion.ok()) {
                throw new NavegacionOvzException(verificacion.motivo());
            }
            navegador.rellenarFormularioAlta(new DatosFormularioAlta(datos.crotalMadre(),
                    datos.fechaNacimientoDdMmAaaa(), datos.sexoCodigo(), datos.tipoIdentificadorCodigo(),
                    datos.fechaIdentificacionDdMmAaaa(), datos.razaCodigo(), datos.emisionDiCodigo(),
                    datos.oficinaDiCodigo()));

            if (modo == ModoWorkerOvz.ENSAYO) {
                // ENSAYO: hasta aquí y nada más. NUNCA se pulsa el «Aceptar» del alta. La sesión siempre se cierra.
                navegador.salir();
                cerrada = true;
                log.info("Worker OVZ: trabajo {} sesión cerrada (ensayo del alta, no se presentó nada)", trabajoId);
                trabajoOvzService.finalizarEnsayo(trabajoId, gestoriaId);
                return;
            }

            aceptarPulsado = true;
            instanteClic = clock.instant().minus(TOLERANCIA_RELOJ);
            navegador.pulsarAceptarAlta();
            Optional<Justificante> justificante =
                    buscarJustificante(navegador::listarDocumentosAltaBovino, datos.crotalTernero(), instanteClic)
                            .justificante();
            navegador.salir();
            cerrada = true;
            log.info("Worker OVZ: trabajo {} alta presentada, sesión cerrada", trabajoId);
            registrarEjecutado(trabajoId, gestoriaId, TipoTrabajoOvz.ALTA_NACIMIENTO, justificante);
        } catch (AceptarException e) {
            cerrarSinFallar();
            cerrada = true;
            errorSeguro(trabajoId, gestoriaId, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Worker OVZ: trabajo {} (alta) falló ({})", trabajoId, e.getClass().getSimpleName());
            if (!aceptarPulsado) {
                cerrarSinFallar();
                cerrada = true;
                errorSeguro(trabajoId, gestoriaId, motivoDe(e, TipoTrabajoOvz.ALTA_NACIMIENTO));
            } else {
                resolverIncierto(trabajoId, gestoriaId, TipoTrabajoOvz.ALTA_NACIMIENTO,
                        navegador::listarDocumentosAltaBovino, datos.crotalTernero(), instanteClic);
                cerrada = true;
            }
        } finally {
            if (!cerrada) {
                cerrarSinFallar();
            }
        }
    }

    private void registrarEjecutado(Long trabajoId, Long gestoriaId, TipoTrabajoOvz tipo,
                                    Optional<Justificante> justificante) {
        try {
            trabajoOvzService.finalizarEjecutado(trabajoId, gestoriaId, justificante);
            log.info("Worker OVZ: trabajo {} presentado", trabajoId);
        } catch (RuntimeException e) {
            // OVZ ya presentó el trámite pero no pudimos guardarlo: nunca se reintenta; se deja INCIERTO si se puede.
            log.error("Worker OVZ: trabajo {} presentado pero no se pudo registrar ({})", trabajoId,
                    e.getClass().getSimpleName());
            intentarFinalizarError(trabajoId, gestoriaId, TipoErrorOvz.INCIERTO, motivoNoRegistrada(tipo));
        }
    }

    /** Tras un fallo posterior al clic: ¿está el trámite en Documentos? Sí -> EJECUTADO; no (o no se sabe) -> INCIERTO. */
    private void resolverIncierto(Long trabajoId, Long gestoriaId, TipoTrabajoOvz tipo,
                                  Supplier<List<DocumentoCandidato>> listarDocumentos, String crotal, Instant instanteClic) {
        IdentificadorJustificante.Resultado r = null;
        try {
            r = buscarJustificante(listarDocumentos, crotal, instanteClic);
        } catch (RuntimeException e) {
            log.warn("Worker OVZ: trabajo {} no pudo consultar Documentos ({})", trabajoId, e.getClass().getSimpleName());
        } finally {
            cerrarSinFallar();
        }
        if (r != null && r.resultado() == ResultadoBusquedaJustificante.ENCONTRADO) {
            registrarEjecutado(trabajoId, gestoriaId, tipo, r.justificante());
        } else {
            intentarFinalizarError(trabajoId, gestoriaId, TipoErrorOvz.INCIERTO, motivoIncierto(tipo));
        }
    }

    /** Lista Documentos (del tipo que toque) y busca el justificante; reintenta SOLO la consulta (nunca la presentación). */
    private IdentificadorJustificante.Resultado buscarJustificante(Supplier<List<DocumentoCandidato>> listarDocumentos,
                                                                    String crotal, Instant instanteClic) {
        IdentificadorJustificante.Resultado resultado = null;
        for (int intento = 1; intento <= intentosDescarga; intento++) {
            resultado = IdentificadorJustificante.identificar(listarDocumentos.get(), instanteClic,
                    crotal, navegador::descargarDocumento);
            if (resultado.resultado() != ResultadoBusquedaJustificante.NO_ENCONTRADO) {
                return resultado;
            }
            if (intento < intentosDescarga) {
                dormir(pausaDescarga);
            }
        }
        return resultado;
    }

    private CredencialOvzEnClaro descifrar(Long ganaderoId, String usuarioOvz, Long trabajoId, Long gestoriaId) {
        Ganadero ganadero = ganaderoRepository.findByIdAndGestoriaId(ganaderoId, gestoriaId).orElse(null);
        if (ganadero == null || ganadero.isUsaCertificado() || usuarioOvz == null
                || usuarioOvz.isBlank() || ganadero.getOvzPasswordCifrada() == null) {
            errorSeguro(trabajoId, gestoriaId, MOTIVO_FALTAN_CREDENCIALES);
            return null;
        }
        try {
            return descifradoCredencialOvz.descifrarPasswordParaWorker(ganadero, gestoriaId);
        } catch (CredencialOvzDeOtraGestoriaException | DescifradoCredencialOvzException e) {
            errorSeguro(trabajoId, gestoriaId, MOTIVO_CREDENCIAL_ILEGIBLE);
        } catch (RuntimeException e) {
            // Por ejemplo, la fila de auditoría no se pudo escribir: no se descifró, no se abre OVZ.
            errorSeguro(trabajoId, gestoriaId, MOTIVO_CREDENCIAL_ILEGIBLE);
        }
        return null;
    }

    private void errorSeguro(Long trabajoId, Long gestoriaId, String motivo) {
        intentarFinalizarError(trabajoId, gestoriaId, TipoErrorOvz.SEGURO, motivo);
    }

    private void intentarFinalizarError(Long trabajoId, Long gestoriaId, TipoErrorOvz clase, String motivo) {
        try {
            trabajoOvzService.finalizarError(trabajoId, gestoriaId, clase, motivo);
            log.info("Worker OVZ: trabajo {} terminó en error {}", trabajoId, clase);
        } catch (RuntimeException e) {
            log.error("Worker OVZ: trabajo {} no se pudo marcar en error ({})", trabajoId, e.getClass().getSimpleName());
        }
    }

    /** Solo los motivos pensados para el gestor (excepciones del navegador OVZ) se muestran; el resto, genérico. */
    private static String motivoDe(RuntimeException e, TipoTrabajoOvz tipo) {
        if (e instanceof LoginCertificadoException || e instanceof CredencialesOvzInvalidasException
                || e instanceof SesionCaducadaException || e instanceof NavegacionOvzException) {
            return e.getMessage();
        }
        return motivoGenerico(tipo);
    }

    private void cerrarSinFallar() {
        try {
            navegador.salir();
        } catch (RuntimeException e) {
            log.warn("Worker OVZ: no se pudo cerrar la sesión de OVZ ({})", e.getClass().getSimpleName());
        }
    }

    private static void dormir(Duration pausa) {
        if (pausa.isZero() || pausa.isNegative()) {
            return;
        }
        try {
            Thread.sleep(pausa.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
