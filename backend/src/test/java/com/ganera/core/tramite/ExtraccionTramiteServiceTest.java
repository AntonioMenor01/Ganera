package com.ganera.core.tramite;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoExplotacionRepository;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.explotacion.Animal;
import com.ganera.core.explotacion.AnimalRepository;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.tramite.IaDePruebaConfig.IaProgramable;
import com.ganera.core.tramite.IaDePruebaConfig.RelojAjustable;
import com.ganera.core.tramite.TramiteExtractionService.TramiteExtraido;
import com.ganera.core.whatsapp.MensajeCampo;
import com.ganera.core.whatsapp.MensajeCampoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.ganera.core.tramite.IaDePruebaConfig.AHORA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extraccion en segundo plano (B1, T2) con transacciones REALES: el test no abre ninguna (nada de
 * @DataJpaTest), igual que el planificador, que corre fuera de cualquier peticion. La IA es falsa y
 * programable y el reloj se mueve a mano. Los datos se crean con repositorios y se leen con los
 * finders con gestoriaId.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(IaDePruebaConfig.class)
class ExtraccionTramiteServiceTest {

    private static final String TEXTO = "se ha muerto la vaca 1234, llama al 600111222";

    @Autowired
    private ExtraccionTramiteService servicio;
    @Autowired
    private TramiteRevisionService revisionService;
    @Autowired
    private IaProgramable ia;
    @Autowired
    private RelojAjustable reloj;
    @Autowired
    private ObjectProvider<ExtraccionTramiteScheduler> planificador;

    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private TramiteCrotalRepository tramiteCrotalRepository;
    @Autowired
    private MensajeCampoRepository mensajeCampoRepository;
    @Autowired
    private AnimalRepository animalRepository;
    @Autowired
    private ContactoExplotacionRepository contactoExplotacionRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    private Gestoria gestoriaA;
    private Gestoria gestoriaB;
    private Contacto contactoA;
    private Contacto contactoB;
    private Explotacion explotacionA;
    private Explotacion explotacionB;

    @BeforeEach
    void preparar() {
        esperasDelEmpleadoAgotadas = 0;
        ia.reiniciar();
        reloj.fijar(AHORA);
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria extraccion A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria extraccion B"));
        contactoA = contacto(gestoriaA, "+34600970001");
        contactoB = contacto(gestoriaB, "+34600970101");
        explotacionA = explotacion(gestoriaA, "ES970000000001");
        explotacionB = explotacion(gestoriaB, "ES970000000101");
    }

    @AfterEach
    void limpiar() {
        mensajeCampoRepository.deleteAll();
        tramiteCrotalRepository.deleteAll();
        tramiteRepository.deleteAll();
        animalRepository.deleteAll();
        contactoExplotacionRepository.deleteAll();
        contactoRepository.deleteAll();
        explotacionRepository.deleteAll();
        ganaderoRepository.deleteAll();
        gestoriaRepository.deleteAll();
        ia.reiniciar();
    }

    // ------------------------------------------------------------------ exito

    @Test
    void exitoDesdePendienteAplicaTipoCrotalesYDescartadosConUnSoloIncrementoDeVersion() {
        animal(gestoriaA, explotacionA, "ES010000001234");
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        long versionInicial = tramite.getVersion();
        ia.devolver(TipoTramite.BAJA_MUERTE, "1234", "ES 9999 9999 9999", "12", "ES*1");

        servicio.procesarPendientes();

        assertThat(ia.textos()).containsExactly(TEXTO);
        assertThat(ia.algunaLlamadaDentroDeTransaccion()).isFalse();
        Tramite despues = recargar(tramite);
        assertThat(despues.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(despues.getEstadoExtraccion()).isEqualTo(EstadoExtraccion.COMPLETADA);
        assertThat(despues.getTipoTramite()).isEqualTo(TipoTramite.BAJA_MUERTE);
        assertThat(despues.getCrotalesDescartados()).isEqualTo(2);
        assertThat(despues.getProximoIntentoExtraccion()).isNull();
        assertThat(despues.getIntentosExtraccion()).isZero();
        assertThat(despues.getVersion()).isEqualTo(versionInicial + 1);

        List<TramiteCrotal> crotales = crotales(despues);
        assertThat(crotales).extracting(TramiteCrotal::getCrotalIndicado).containsExactly("1234", "ES999999999999");
        assertThat(crotales.get(0).getResolucion()).isEqualTo(ResolucionCrotal.EN_INVENTARIO);
        assertThat(crotales.get(0).getCrotal()).isEqualTo("ES010000001234");
        assertThat(crotales.get(1).getResolucion()).isEqualTo(ResolucionCrotal.NO_ENCONTRADO);
        assertThat(servicio.pendientes(AHORA.plus(Duration.ofDays(1)), 10)).isEmpty();
    }

    @Test
    void noIdentificadoDejaElTipoNuloYLaExtraccionCompletada() {
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        ia.responder(texto -> new TramiteExtraido(null, List.of()));

        servicio.procesarPendientes();

        Tramite despues = recargar(tramite);
        assertThat(despues.getTipoTramite()).isNull();
        assertThat(despues.getEstadoExtraccion()).isEqualTo(EstadoExtraccion.COMPLETADA);
        assertThat(despues.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(crotales(despues)).isEmpty();
    }

    /** Decision 23 de A1: si varios crotales resuelven al mismo Animal, se queda el primero. */
    @Test
    void losCrotalesQueResuelvenAlMismoAnimalSeDeduplicanQuedandoElPrimero() {
        Animal vaca = animal(gestoriaA, explotacionA, "ES010000001234");
        animal(gestoriaA, explotacionA, "ES010000005678");
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        long versionInicial = tramite.getVersion();
        ia.devolver(TipoTramite.BAJA_MUERTE, "001234", "5678", "ES010000001234", "1234");

        servicio.procesarPendientes();

        Tramite despues = recargar(tramite);
        List<TramiteCrotal> crotales = crotales(despues);
        assertThat(crotales).extracting(TramiteCrotal::getCrotalIndicado).containsExactly("001234", "5678");
        assertThat(crotales.get(0).getAnimal().getId()).isEqualTo(vaca.getId());
        assertThat(despues.getCrotalesDescartados()).isZero();
        assertThat(despues.getVersion()).isEqualTo(versionInicial + 1);
    }

    @Test
    void sinExplotacionLosCrotalesQuedanSinExplotacion() {
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, null, TEXTO);
        ia.devolver(TipoTramite.ALTA_NACIMIENTO, "1234");

        servicio.procesarPendientes();

        List<TramiteCrotal> crotales = crotales(recargar(tramite));
        assertThat(crotales).singleElement()
                .extracting(TramiteCrotal::getResolucion).isEqualTo(ResolucionCrotal.SIN_EXPLOTACION);
    }

    // ------------------------------------------------------------------ fallos y reintentos

    @Test
    void elPrimerFalloPasaARevisionConFallidaVersionTrasFalloYReintentoEnUnMinuto() {
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        long versionInicial = tramite.getVersion();
        ia.lanzar(new ExtraccionFallidaException("caida simulada"));

        servicio.procesarPendientes();

        Tramite despues = recargar(tramite);
        assertThat(despues.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(despues.getEstadoExtraccion()).isEqualTo(EstadoExtraccion.FALLIDA);
        assertThat(despues.getIntentosExtraccion()).isEqualTo(1);
        assertThat(despues.getVersion()).isEqualTo(versionInicial + 1);
        assertThat(despues.getVersionTrasFallo()).isEqualTo(despues.getVersion());
        assertThat(despues.getProximoIntentoExtraccion()).isEqualTo(AHORA.plus(Duration.ofMinutes(1)));
        assertThat(despues.getTipoTramite()).isNull();
        assertThat(crotales(despues)).isEmpty();
        assertThat(mensajeDe(despues).getCuerpo()).isEqualTo(TEXTO);
    }

    @Test
    void cualquierRuntimeExceptionDeLaIaCuentaComoFallo() {
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        ia.lanzar(new IllegalArgumentException("fallo inesperado del SDK"));

        servicio.procesarPendientes();

        assertThat(recargar(tramite).getEstadoExtraccion()).isEqualTo(EstadoExtraccion.FALLIDA);
    }

    @Test
    void elReintentoRespetaElPlazoDelRelojYNoUsaLaHoraDeLaBaseDeDatos() {
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        ia.lanzar(new ExtraccionFallidaException("caida"));
        servicio.procesarPendientes();

        assertThat(servicio.pendientes(AHORA.plusSeconds(59), 10)).isEmpty();
        assertThat(servicio.pendientes(AHORA.plusSeconds(60), 10))
                .containsExactly(new TramitePendienteExtraccion(tramite.getId(), gestoriaA.getId()));

        // Sin mover el reloj no se reintenta: una llamada en total.
        servicio.procesarPendientes();
        assertThat(ia.llamadas()).isEqualTo(1);
    }

    @Test
    void unReintentoQueAciertaSobreUnTramiteSinTocarLoCompleta() {
        animal(gestoriaA, explotacionA, "ES010000001234");
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        ia.lanzar(new ExtraccionFallidaException("caida"));
        servicio.procesarPendientes();
        long versionTrasFallo = recargar(tramite).getVersion();

        reloj.avanzar(Duration.ofMinutes(1));
        ia.devolver(TipoTramite.BAJA_MUERTE, "1234");
        servicio.procesarPendientes();

        Tramite despues = recargar(tramite);
        assertThat(ia.llamadas()).isEqualTo(2);
        assertThat(despues.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(despues.getEstadoExtraccion()).isEqualTo(EstadoExtraccion.COMPLETADA);
        assertThat(despues.getTipoTramite()).isEqualTo(TipoTramite.BAJA_MUERTE);
        assertThat(despues.getProximoIntentoExtraccion()).isNull();
        assertThat(despues.getIntentosExtraccion()).isEqualTo(1);
        assertThat(despues.getVersion()).isEqualTo(versionTrasFallo + 1);
        assertThat(crotales(despues)).singleElement()
                .extracting(TramiteCrotal::getResolucion).isEqualTo(ResolucionCrotal.EN_INVENTARIO);
    }

    @Test
    void losReintentosFallidosNoCambianLaVersion() {
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        ia.lanzar(new ExtraccionFallidaException("caida"));
        servicio.procesarPendientes();
        Tramite trasPrimerFallo = recargar(tramite);

        reloj.avanzar(Duration.ofMinutes(1));
        servicio.procesarPendientes();

        Tramite despues = recargar(tramite);
        assertThat(despues.getIntentosExtraccion()).isEqualTo(2);
        assertThat(despues.getVersion()).isEqualTo(trasPrimerFallo.getVersion());
        assertThat(despues.getVersionTrasFallo()).isEqualTo(trasPrimerFallo.getVersion());
        assertThat(despues.getProximoIntentoExtraccion()).isEqualTo(reloj.instant().plus(Duration.ofMinutes(5)));
        assertThat(despues.getEstadoExtraccion()).isEqualTo(EstadoExtraccion.FALLIDA);
    }

    /** Con la version del primer fallo, un empleado que abrio el modal puede guardar tras un reintento fallido. */
    @Test
    void unEmpleadoConLaVersionDelPrimerFalloPuedeEditarTrasUnReintentoFallido() {
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        ia.lanzar(new ExtraccionFallidaException("caida"));
        servicio.procesarPendientes();
        long versionVista = recargar(tramite).getVersion();
        reloj.avanzar(Duration.ofMinutes(1));
        servicio.procesarPendientes();

        TramiteDetalleResponse respuesta = revisionService.actualizar(gestoriaA.getId(), tramite.getId(), versionVista,
                null, TipoTramite.ALTA_NACIMIENTO, null);

        assertThat(respuesta.version()).isEqualTo(versionVista + 1);
    }

    @Test
    void trasCuatroFallosNoHayMasIntentos() {
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        ia.lanzar(new ExtraccionFallidaException("caida"));

        servicio.procesarPendientes();
        assertThat(recargar(tramite).getProximoIntentoExtraccion()).isEqualTo(reloj.instant().plus(Duration.ofMinutes(1)));
        reloj.avanzar(Duration.ofMinutes(1));
        servicio.procesarPendientes();
        assertThat(recargar(tramite).getProximoIntentoExtraccion()).isEqualTo(reloj.instant().plus(Duration.ofMinutes(5)));
        reloj.avanzar(Duration.ofMinutes(5));
        servicio.procesarPendientes();
        assertThat(recargar(tramite).getProximoIntentoExtraccion()).isEqualTo(reloj.instant().plus(Duration.ofMinutes(30)));
        reloj.avanzar(Duration.ofMinutes(30));
        servicio.procesarPendientes();

        Tramite despues = recargar(tramite);
        assertThat(despues.getIntentosExtraccion()).isEqualTo(4);
        assertThat(despues.getProximoIntentoExtraccion()).isNull();
        assertThat(despues.getEstadoExtraccion()).isEqualTo(EstadoExtraccion.FALLIDA);
        assertThat(despues.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(ia.llamadas()).isEqualTo(4);

        reloj.avanzar(Duration.ofDays(30));
        servicio.procesarPendientes();
        assertThat(ia.llamadas()).isEqualTo(4);
    }

    // ------------------------------------------------------------------ no pisar lo humano

    @Test
    void unReintentoTrasUnaEdicionNoLlamaALaIaYDejaDeReintentar() {
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        ia.lanzar(new ExtraccionFallidaException("caida"));
        servicio.procesarPendientes();
        long version = recargar(tramite).getVersion();
        revisionService.actualizar(gestoriaA.getId(), tramite.getId(), version, null, TipoTramite.ALTA_NACIMIENTO, List.of("9876"));
        long versionEditada = recargar(tramite).getVersion();

        reloj.avanzar(Duration.ofMinutes(1));
        ia.devolver(TipoTramite.BAJA_MUERTE, "1234");
        servicio.procesarPendientes();

        Tramite despues = recargar(tramite);
        assertThat(ia.llamadas()).isEqualTo(1);
        assertThat(despues.getProximoIntentoExtraccion()).isNull();
        assertThat(despues.getTipoTramite()).isEqualTo(TipoTramite.ALTA_NACIMIENTO);
        assertThat(despues.getVersion()).isEqualTo(versionEditada);
        assertThat(crotales(despues)).extracting(TramiteCrotal::getCrotalIndicado).containsExactly("9876");
    }

    /*
     * Los tests "mientras la IA responde": la IA falsa ejecuta la accion del empleado en OTRO hilo
     * (empleado(...)) y espera como mucho 5 s. Si la IA se llamara con el bloqueo de fila tomado,
     * el otro hilo se quedaria esperando ese bloqueo (LOCK_TIMEOUT de H2: 10 s) y el test fallaria
     * por timeout. En el mismo hilo no se veria: TramiteRevisionService (@Transactional REQUIRED) se
     * uniria a la transaccion que tiene el bloqueo, que es reentrante.
     */

    /** El empleado aprueba MIENTRAS la IA responde: el resultado se descarta. */
    @Test
    void unExitoSobreUnTramiteAprobadoMientrasLaIaRespondiaSeDescarta() {
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        ia.lanzar(new ExtraccionFallidaException("caida"));
        servicio.procesarPendientes();
        long versionTrasFallo = recargar(tramite).getVersion();
        reloj.avanzar(Duration.ofMinutes(1));
        ia.responder(texto -> {
            empleado(() -> {
                revisionService.actualizar(gestoriaA.getId(), tramite.getId(), versionTrasFallo, null, TipoTramite.ALTA_NACIMIENTO, null);
                revisionService.aprobar(gestoriaA.getId(), tramite.getId(), versionTrasFallo + 1);
            });
            return new TramiteExtraido(TipoTramite.BAJA_MUERTE, List.of("1234"));
        });

        servicio.procesarPendientes();

        assertThat(esperasDelEmpleadoAgotadas).isZero();
        Tramite despues = recargar(tramite);
        assertThat(despues.getEstado()).isEqualTo(EstadoTramite.APROBADO);
        assertThat(despues.getTipoTramite()).isEqualTo(TipoTramite.ALTA_NACIMIENTO);
        assertThat(despues.getEstadoExtraccion()).isEqualTo(EstadoExtraccion.FALLIDA);
        assertThat(despues.getProximoIntentoExtraccion()).isNull();
        assertThat(crotales(despues)).isEmpty();
        assertThat(despues.getVersion()).isEqualTo(versionTrasFallo + 2); // PATCH + aprobar, nada mas
    }

    @Test
    void unExitoSobreUnTramiteRechazadoMientrasLaIaRespondiaSeDescarta() {
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        ia.lanzar(new ExtraccionFallidaException("caida"));
        servicio.procesarPendientes();
        long version = recargar(tramite).getVersion();
        reloj.avanzar(Duration.ofMinutes(1));
        ia.responder(texto -> {
            empleado(() -> revisionService.rechazar(gestoriaA.getId(), tramite.getId(), version));
            return new TramiteExtraido(TipoTramite.BAJA_MUERTE, List.of("1234"));
        });

        servicio.procesarPendientes();

        assertThat(esperasDelEmpleadoAgotadas).isZero();
        Tramite despues = recargar(tramite);
        assertThat(despues.getEstado()).isEqualTo(EstadoTramite.RECHAZADO);
        assertThat(despues.getTipoTramite()).isNull();
        assertThat(despues.getVersion()).isEqualTo(version + 1);
        assertThat(despues.getProximoIntentoExtraccion()).isNull();
        assertThat(crotales(despues)).isEmpty();
    }

    /**
     * Revision I1: el empleado CORRIGE (PATCH, sin aprobar) mientras la IA responde y la IA acierta.
     * El Tramite sigue en PENDIENTE_REVISION + FALLIDA, asi que solo la version distingue "lo ha
     * tocado alguien": el resultado de la IA se descarta y quedan los datos del empleado.
     */
    @Test
    void unExitoSobreUnTramiteEditadoSinAprobarMientrasLaIaRespondiaSeDescarta() {
        animal(gestoriaA, explotacionA, "ES010000001234");
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        ia.lanzar(new ExtraccionFallidaException("caida"));
        servicio.procesarPendientes();
        long versionVista = recargar(tramite).getVersion();
        reloj.avanzar(Duration.ofMinutes(1));
        ia.responder(texto -> {
            empleado(() -> revisionService.actualizar(gestoriaA.getId(), tramite.getId(), versionVista, null,
                    TipoTramite.DECLARACION_CENSO, List.of("9876")));
            return new TramiteExtraido(TipoTramite.BAJA_MUERTE, List.of("1234"));
        });

        servicio.procesarPendientes();

        assertThat(esperasDelEmpleadoAgotadas).isZero();
        assertThat(ia.llamadas()).isEqualTo(2);
        Tramite despues = recargar(tramite);
        assertThat(despues.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(despues.getEstadoExtraccion()).isEqualTo(EstadoExtraccion.FALLIDA);
        assertThat(despues.getTipoTramite()).isEqualTo(TipoTramite.DECLARACION_CENSO);
        assertThat(crotales(despues)).extracting(TramiteCrotal::getCrotalIndicado).containsExactly("9876");
        assertThat(despues.getVersion()).isEqualTo(versionVista + 1);
        assertThat(despues.getProximoIntentoExtraccion()).isNull();
        assertThat(despues.getCrotalesDescartados()).isZero();
    }

    @Test
    void unFalloSobreUnTramiteEditadoMientrasLaIaRespondiaNoLoTocaYDejaDeReintentar() {
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        ia.lanzar(new ExtraccionFallidaException("caida"));
        servicio.procesarPendientes();
        long version = recargar(tramite).getVersion();
        reloj.avanzar(Duration.ofMinutes(1));
        ia.responder(texto -> {
            empleado(() -> revisionService.actualizar(gestoriaA.getId(), tramite.getId(), version, null,
                    TipoTramite.DECLARACION_CENSO, null));
            throw new ExtraccionFallidaException("caida");
        });

        servicio.procesarPendientes();

        assertThat(esperasDelEmpleadoAgotadas).isZero();
        Tramite despues = recargar(tramite);
        assertThat(despues.getVersion()).isEqualTo(version + 1);
        assertThat(despues.getTipoTramite()).isEqualTo(TipoTramite.DECLARACION_CENSO);
        assertThat(despues.getProximoIntentoExtraccion()).isNull();
        assertThat(despues.getEstadoExtraccion()).isEqualTo(EstadoExtraccion.FALLIDA);
    }

    // ------------------------------------------------------------------ sin texto

    @Test
    void unMensajeVacioQuedaSinTextoSinLlamarALaIa() {
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, "   ");
        long versionInicial = tramite.getVersion();

        servicio.procesarPendientes();

        Tramite despues = recargar(tramite);
        assertThat(ia.llamadas()).isZero();
        assertThat(despues.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
        assertThat(despues.getEstadoExtraccion()).isEqualTo(EstadoExtraccion.SIN_TEXTO);
        assertThat(despues.getProximoIntentoExtraccion()).isNull();
        assertThat(despues.getVersion()).isEqualTo(versionInicial + 1);
    }

    @Test
    void sinMensajeQuedaSinTextoSinLlamarALaIa() {
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, null);

        servicio.procesarPendientes();

        assertThat(ia.llamadas()).isZero();
        assertThat(recargar(tramite).getEstadoExtraccion()).isEqualTo(EstadoExtraccion.SIN_TEXTO);
    }

    /** Un mensaje enlazado al Tramite de A pero con gestoria_id de B (datos inconsistentes) no se usa:
     * el texto se busca con el gestoriaId del Tramite. */
    @Test
    void unMensajeDeOtraGestoriaEnlazadoAlTramiteNoSeEnviaALaIa() {
        Tramite tramite = tramitePendiente(gestoriaA, contactoA, explotacionA, null);
        mensaje(tramite, gestoriaB, TEXTO);

        servicio.procesarPendientes();

        assertThat(ia.llamadas()).isZero();
        assertThat(recargar(tramite).getEstadoExtraccion()).isEqualTo(EstadoExtraccion.SIN_TEXTO);
    }

    // ------------------------------------------------------------------ lote

    @Test
    void unFalloAlAplicarUnTramiteNoAbortaElLoteYCuentaComoFallo() {
        // Datos inconsistentes: Tramite de A con la explotacion de B -> TramiteCrotalService da 404.
        Tramite roto = tramitePendiente(gestoriaA, contactoA, explotacionB, "roto 1234");
        Tramite bueno = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        reloj.avanzar(Duration.ofSeconds(1));
        ia.devolver(TipoTramite.BAJA_MUERTE, "1234");

        servicio.procesarPendientes();

        assertThat(ia.llamadas()).isEqualTo(2);
        Tramite rotoDespues = recargar(roto);
        assertThat(rotoDespues.getEstadoExtraccion()).isEqualTo(EstadoExtraccion.FALLIDA);
        assertThat(rotoDespues.getIntentosExtraccion()).isEqualTo(1);
        assertThat(crotales(rotoDespues)).isEmpty();
        assertThat(recargar(bueno).getEstadoExtraccion()).isEqualTo(EstadoExtraccion.COMPLETADA);
    }

    @Test
    void elLoteRespetaElLimiteYElOrdenPorProximoIntento() {
        Tramite primero = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        Tramite segundo = tramitePendiente(gestoriaB, contactoB, explotacionB, TEXTO);
        Tramite tercero = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);

        assertThat(servicio.pendientes(AHORA, 2)).extracting(TramitePendienteExtraccion::tramiteId)
                .containsExactly(primero.getId(), segundo.getId());
        assertThat(servicio.pendientes(AHORA, 10)).extracting(TramitePendienteExtraccion::gestoriaId)
                .containsExactly(gestoriaA.getId(), gestoriaB.getId(), gestoriaA.getId());
        assertThat(servicio.pendientes(AHORA.minusSeconds(1), 10)).isEmpty();
        assertThat(tercero.getId()).isNotNull();
    }

    @Test
    void losCompletadosYSinTextoNoSonPendientes() {
        Tramite completado = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        completado.setEstadoExtraccion(EstadoExtraccion.COMPLETADA);
        tramiteRepository.save(completado);
        Tramite sinTexto = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        sinTexto.setEstadoExtraccion(EstadoExtraccion.SIN_TEXTO);
        tramiteRepository.save(sinTexto);
        Tramite sinProximo = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        sinProximo.setProximoIntentoExtraccion(null);
        tramiteRepository.save(sinProximo);

        assertThat(servicio.pendientes(AHORA.plus(Duration.ofDays(1)), 10)).isEmpty();
    }

    // ------------------------------------------------------------------ aislamiento

    /**
     * Mismo texto y mismos ultimos digitos en A y en B, procesados en la misma pasada: el Animal de B
     * nunca se enlaza ni completa el Tramite de A, y cada Tramite queda en su Gestoria.
     */
    @Test
    void unAnimalDeBConLosMismosUltimosDigitosNuncaSeEnlazaAlTramiteDeA() {
        Animal vacaB = animal(gestoriaB, explotacionB, "ES020000001234");
        Tramite tramiteA = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        Tramite tramiteB = tramitePendiente(gestoriaB, contactoB, explotacionB, TEXTO);
        ia.devolver(TipoTramite.BAJA_MUERTE, "1234");

        servicio.procesarPendientes();

        assertThat(ia.llamadas()).isEqualTo(2);
        Tramite aDespues = tramiteRepository.findByIdAndGestoriaId(tramiteA.getId(), gestoriaA.getId()).orElseThrow();
        Tramite bDespues = tramiteRepository.findByIdAndGestoriaId(tramiteB.getId(), gestoriaB.getId()).orElseThrow();
        assertThat(tramiteRepository.findByIdAndGestoriaId(tramiteA.getId(), gestoriaB.getId())).isEmpty();
        assertThat(aDespues.getEstadoExtraccion()).isEqualTo(EstadoExtraccion.COMPLETADA);
        assertThat(bDespues.getEstadoExtraccion()).isEqualTo(EstadoExtraccion.COMPLETADA);

        List<TramiteCrotal> deA = tramiteCrotalRepository.findByTramiteIdAndGestoriaIdOrderByIdAsc(tramiteA.getId(), gestoriaA.getId());
        assertThat(deA).singleElement().satisfies(fila -> {
            assertThat(fila.getAnimal()).isNull();
            assertThat(fila.getCrotal()).isEqualTo("1234");
            assertThat(fila.getResolucion()).isEqualTo(ResolucionCrotal.NO_ENCONTRADO);
        });
        assertThat(tramiteCrotalRepository.findByTramiteIdAndGestoriaIdOrderByIdAsc(tramiteA.getId(), gestoriaB.getId())).isEmpty();

        List<TramiteCrotal> deB = tramiteCrotalRepository.findByTramiteIdAndGestoriaIdOrderByIdAsc(tramiteB.getId(), gestoriaB.getId());
        assertThat(deB).singleElement().satisfies(fila -> {
            assertThat(fila.getAnimal().getId()).isEqualTo(vacaB.getId());
            assertThat(fila.getCrotal()).isEqualTo("ES020000001234");
            assertThat(fila.getResolucion()).isEqualTo(ResolucionCrotal.EN_INVENTARIO);
        });
    }

    // ------------------------------------------------------------------ logs y planificador

    @Test
    void niElTextoNiElTelefonoSalenEnLosLogsAlProcesar(CapturedOutput salida) {
        Tramite exito = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        servicio.procesarPendientes(); // sin programar: la IA lanza
        ia.devolver(TipoTramite.BAJA_MUERTE, "1234");
        reloj.avanzar(Duration.ofMinutes(1));
        servicio.procesarPendientes();
        Tramite fallo = tramitePendiente(gestoriaA, contactoA, explotacionA, TEXTO);
        ia.lanzar(new ExtraccionFallidaException("respuesta del modelo: " + TEXTO + " " + contactoA.getTelefono()));
        servicio.procesarPendientes();

        assertThat(recargar(exito).getEstadoExtraccion()).isEqualTo(EstadoExtraccion.COMPLETADA);
        assertThat(recargar(fallo).getEstadoExtraccion()).isEqualTo(EstadoExtraccion.FALLIDA);
        assertThat(salida.getAll())
                .contains("tramiteId=" + exito.getId())
                .contains("ExtraccionFallidaException")
                .doesNotContain("muerto la vaca")
                .doesNotContain("600111222")
                .doesNotContain("600970001")
                .doesNotContain("respuesta del modelo");
    }

    @Test
    void enLosTestsElPlanificadorEstaDesactivado() {
        assertThat(planificador.getIfAvailable()).isNull();
    }

    // ------------------------------------------------------------------ ayudas

    /** Cuantas veces la accion del empleado no termino en 5 s (un bloqueo retenido durante la IA). */
    private volatile int esperasDelEmpleadoAgotadas;

    /** La accion del empleado en OTRO hilo, como una peticion HTTP concurrente; espera 5 s como mucho. */
    private void empleado(Runnable accion) {
        CompletableFuture<Void> futuro = CompletableFuture.runAsync(accion);
        try {
            futuro.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            esperasDelEmpleadoAgotadas++;
            throw new IllegalStateException("El empleado ha esperado mas de 5 s: hay un bloqueo tomado durante la IA");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("La accion del empleado ha fallado", e.getCause());
        }
    }

    private Tramite tramitePendiente(Gestoria gestoria, Contacto contacto, Explotacion explotacion, String texto) {
        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setExplotacion(explotacion);
        tramite.setOrigen(OrigenTramite.WHATSAPP);
        tramite.setEstado(EstadoTramite.PENDIENTE_EXTRACCION);
        tramite.setEstadoExtraccion(EstadoExtraccion.PENDIENTE);
        tramite.setProximoIntentoExtraccion(reloj.instant());
        tramite = tramiteRepository.save(tramite);
        if (texto != null) {
            mensaje(tramite, gestoria, texto);
        }
        return tramite;
    }

    private void mensaje(Tramite tramite, Gestoria gestoria, String texto) {
        MensajeCampo mensaje = new MensajeCampo();
        mensaje.setMessageSid("SM-" + UUID.randomUUID());
        mensaje.setTelefonoOrigen("+34600970001");
        mensaje.setCuerpo(texto);
        mensaje.setGestoria(gestoria);
        mensaje.setTramite(tramite);
        mensaje.setCreatedAt(reloj.instant());
        mensajeCampoRepository.save(mensaje);
    }

    private Tramite recargar(Tramite tramite) {
        return tramiteRepository.findByIdAndGestoriaId(tramite.getId(), tramite.getGestoria().getId()).orElseThrow();
    }

    private List<TramiteCrotal> crotales(Tramite tramite) {
        return tramiteCrotalRepository.findByTramiteIdAndGestoriaIdOrderByIdAsc(tramite.getId(), tramite.getGestoria().getId());
    }

    private MensajeCampo mensajeDe(Tramite tramite) {
        return mensajeCampoRepository.findFirstByTramiteIdAndGestoriaIdOrderByCreatedAtDesc(
                tramite.getId(), tramite.getGestoria().getId()).orElseThrow();
    }

    private Contacto contacto(Gestoria gestoria, String telefono) {
        Contacto contacto = new Contacto();
        contacto.setGestoria(gestoria);
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto extraccion");
        contacto.setActivo(true);
        return contactoRepository.save(contacto);
    }

    private Explotacion explotacion(Gestoria gestoria, String codigoRega) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero " + codigoRega);
        ganaderoRepository.save(ganadero);
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(codigoRega);
        explotacion.setNombre("Finca " + codigoRega);
        return explotacionRepository.save(explotacion);
    }

    private Animal animal(Gestoria gestoria, Explotacion explotacion, String crotal) {
        Animal animal = new Animal();
        animal.setGestoria(gestoria);
        animal.setExplotacion(explotacion);
        animal.setCrotal(crotal);
        animal.setCrotalUltimosDigitos(crotal.substring(crotal.length() - 6));
        return animalRepository.save(animal);
    }
}
