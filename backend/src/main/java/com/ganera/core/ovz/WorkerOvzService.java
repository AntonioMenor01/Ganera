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
import com.ganera.core.tramite.TrabajoOvz;
import com.ganera.core.tramite.TrabajoOvzService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

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
    static final String MOTIVO_GENERICO = "Error al presentar la baja en OVZ.";
    static final String MOTIVO_INCIERTO =
            "La baja pudo presentarse en OVZ pero no se pudo confirmar. Comprueba en OVZ si está presentada antes de reencolar.";
    static final String MOTIVO_NO_REGISTRADA =
            "La baja se presentó en OVZ pero no se pudo registrar el resultado. Comprueba el estado en OVZ.";

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

    /** Procesa como mucho un trabajo PENDIENTE. Sin trabajo, no hace nada. */
    public void procesarUno() {
        Optional<TrabajoOvz> reclamado = trabajoOvzService.reclamarSiguiente();
        if (reclamado.isEmpty()) {
            return;
        }
        Long trabajoId = reclamado.get().getId();
        Long gestoriaId = reclamado.get().getGestoria().getId();
        log.info("Worker OVZ: trabajo {} reclamado", trabajoId);

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
            errorSeguro(trabajoId, gestoriaId, MOTIVO_GENERICO);
            return;
        }
        if (datos.usaCertificado()) {
            errorSeguro(trabajoId, gestoriaId, new LoginCertificadoException().getMessage());
            return;
        }
        CredencialOvzEnClaro clave = descifrar(datos, trabajoId, gestoriaId);
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
            Optional<Justificante> justificante = buscarJustificante(datos.crotal(), instanteClic).justificante();
            navegador.salir();
            cerrada = true;
            // REAL: «Aceptar» (PASO 8) y Documentos (PASO 9) ya han ocurrido; el cierre de sesión es el PASO 10.
            log.info("PASO 10 OK: trabajo {} sesión de OVZ cerrada", trabajoId);
            registrarEjecutado(trabajoId, gestoriaId, justificante);
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
                errorSeguro(trabajoId, gestoriaId, motivoDe(e));
            } else {
                // DESDE «Aceptar»: la presentación NO se reintenta. Se consulta Documentos para saber si ocurrió.
                resolverIncierto(trabajoId, gestoriaId, datos.crotal(), instanteClic);
                cerrada = true;
            }
        } finally {
            if (!cerrada) {
                cerrarSinFallar();
            }
        }
    }

    private void registrarEjecutado(Long trabajoId, Long gestoriaId, Optional<Justificante> justificante) {
        try {
            trabajoOvzService.finalizarEjecutado(trabajoId, gestoriaId, justificante);
            log.info("Worker OVZ: trabajo {} presentado", trabajoId);
        } catch (RuntimeException e) {
            // OVZ ya presentó la baja pero no pudimos guardarlo: nunca se reintenta; se deja INCIERTO si se puede.
            log.error("Worker OVZ: trabajo {} presentado pero no se pudo registrar ({})", trabajoId,
                    e.getClass().getSimpleName());
            intentarFinalizarError(trabajoId, gestoriaId, TipoErrorOvz.INCIERTO, MOTIVO_NO_REGISTRADA);
        }
    }

    /** Tras un fallo posterior al clic: ¿está la baja en Documentos? Sí -> EJECUTADO; no (o no se sabe) -> INCIERTO. */
    private void resolverIncierto(Long trabajoId, Long gestoriaId, String crotal, Instant instanteClic) {
        IdentificadorJustificante.Resultado r = null;
        try {
            r = buscarJustificante(crotal, instanteClic);
        } catch (RuntimeException e) {
            log.warn("Worker OVZ: trabajo {} no pudo consultar Documentos ({})", trabajoId, e.getClass().getSimpleName());
        } finally {
            cerrarSinFallar();
        }
        if (r != null && r.resultado() == ResultadoBusquedaJustificante.ENCONTRADO) {
            registrarEjecutado(trabajoId, gestoriaId, r.justificante());
        } else {
            intentarFinalizarError(trabajoId, gestoriaId, TipoErrorOvz.INCIERTO, MOTIVO_INCIERTO);
        }
    }

    /** Lista Documentos y busca el justificante; reintenta SOLO la consulta (nunca la presentación). */
    private IdentificadorJustificante.Resultado buscarJustificante(String crotal, Instant instanteClic) {
        IdentificadorJustificante.Resultado resultado = null;
        for (int intento = 1; intento <= intentosDescarga; intento++) {
            resultado = IdentificadorJustificante.identificar(navegador.listarDocumentosBajaAnimal(), instanteClic,
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

    private CredencialOvzEnClaro descifrar(DatosPresentacionBaja datos, Long trabajoId, Long gestoriaId) {
        Ganadero ganadero = ganaderoRepository.findByIdAndGestoriaId(datos.ganaderoId(), gestoriaId).orElse(null);
        if (ganadero == null || ganadero.isUsaCertificado() || datos.usuarioOvz() == null
                || datos.usuarioOvz().isBlank() || ganadero.getOvzPasswordCifrada() == null) {
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
    private static String motivoDe(RuntimeException e) {
        if (e instanceof LoginCertificadoException || e instanceof CredencialesOvzInvalidasException
                || e instanceof SesionCaducadaException || e instanceof NavegacionOvzException) {
            return e.getMessage();
        }
        return MOTIVO_GENERICO;
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
