package com.ganera.core.tramite;

import com.ganera.core.facturacion.SuscripcionService;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.web.MotivoErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traduccion a 409 { "motivo" } de los fallos de concurrencia que salen de la transaccion de
 * TramiteRevisionService (decision 26): nunca un 500. Sin Spring ni BD: el servicio es un doble
 * que lanza la excepcion indicada (en H2 no se puede provocar de forma fiable un lock timeout sin
 * que Hikari cierre la conexion -- ver TramiteRevisionEndToEndTest).
 */
class TramiteControllerErroresTest {

    private static final GaneraUserPrincipal PRINCIPAL = new GaneraUserPrincipal(1L, 1L, "e@test.com");

    @Test
    void losFallosDeConcurrenciaEnPatchAprobarRechazarHechoEnOvzYDescarteSon409ConMotivo() {
        List<RuntimeException> fallos = List.of(
                new PessimisticLockingFailureException("bloqueo"),
                new CannotAcquireLockException("timeout"),
                new ObjectOptimisticLockingFailureException(Tramite.class, 1L),
                new DataIntegrityViolationException("unique"));

        for (RuntimeException fallo : fallos) {
            TramiteController controller = controllerQueLanza(() -> fallo);

            for (ResponseEntity<?> respuesta : List.of(
                    controller.actualizar(PRINCIPAL, 1L, new TramitePatchRequest(0L, null, null, List.of("1234"))),
                    controller.aprobar(PRINCIPAL, 1L, new TramiteAprobarRequest(0L)),
                    controller.rechazar(PRINCIPAL, 1L, new TramiteRechazarRequest(0L)),
                    controller.marcarHechoEnOvz(PRINCIPAL, 1L, new TramiteHechoEnOvzRequest(0L)),
                    controller.desmarcarHechoEnOvz(PRINCIPAL, 1L, new TramiteHechoEnOvzRequest(0L)),
                    controller.descartar(PRINCIPAL, 1L, new TramiteDescartarRequest(0L)),
                    controller.deshacerDescarte(PRINCIPAL, 1L, new TramiteDeshacerDescarteRequest(0L)))) {
                assertThat(respuesta.getStatusCode().value()).as(fallo.getClass().getSimpleName()).isEqualTo(409);
                assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse(TramiteController.MOTIVO_CONCURRENCIA));
            }
        }
    }

    /** Descartar y deshacer: sin version (cuerpo ausente, {} o version null) -> 400 con el motivo de siempre
     * ANTES de llamar al servicio (aqui un doble que falla si se le llama: no se toca la BD). */
    @Test
    void descartarYDeshacerSinVersionSon400SinLlamarAlServicio() {
        TramiteController controller = controllerQueLanza(
                () -> new IllegalStateException("no debe llamarse al servicio sin version"));

        for (ResponseEntity<?> respuesta : List.of(
                controller.descartar(PRINCIPAL, 1L, null),
                controller.descartar(PRINCIPAL, 1L, new TramiteDescartarRequest(null)),
                controller.deshacerDescarte(PRINCIPAL, 1L, null),
                controller.deshacerDescarte(PRINCIPAL, 1L, new TramiteDeshacerDescarteRequest(null)))) {
            assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
            assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse(TramiteController.MOTIVO_FALTA_VERSION));
        }
    }

    /** Los 409 de estado y de «no descartable» llegan con el motivo exacto del servicio. */
    @Test
    void losConflictosDeDescartarYDeshacerSon409ConSuMotivo() {
        for (String motivo : List.of(TramiteRevisionService.MOTIVO_DESCARTAR_SOLO_PENDIENTE,
                TramiteRevisionService.MOTIVO_NO_DESCARTABLE, TramiteRevisionService.MOTIVO_DESHACER_NO_DESCARTADO)) {
            TramiteController controller = controllerQueLanza(() -> new TramiteConflictoException(motivo));

            for (ResponseEntity<?> respuesta : List.of(
                    controller.descartar(PRINCIPAL, 1L, new TramiteDescartarRequest(3L)),
                    controller.deshacerDescarte(PRINCIPAL, 1L, new TramiteDeshacerDescarteRequest(3L)))) {
                assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
                assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse(motivo));
            }
        }
        assertThat(TramiteRevisionService.MOTIVO_DESCARTAR_SOLO_PENDIENTE)
                .isEqualTo("Solo se puede descartar un trámite pendiente de revisión.");
        assertThat(TramiteRevisionService.MOTIVO_NO_DESCARTABLE)
                .isEqualTo("Solo se descarta un mensaje sin trámite que nadie ha completado. Si no procede, recházalo.");
        assertThat(TramiteRevisionService.MOTIVO_DESHACER_NO_DESCARTADO)
                .isEqualTo("Este trámite no está descartado; no hay nada que deshacer.");
    }

    /** 404 sin cuerpo cuando el servicio no encuentra el tramite (de otra gestoria o inexistente). */
    @Test
    void unTramiteNoEncontradoAlDescartarODeshacerEs404SinCuerpo() {
        TramiteController controller = controllerQueLanza(com.ganera.core.contacto.RecursoNoEncontradoException::new);

        for (ResponseEntity<?> respuesta : List.of(
                controller.descartar(PRINCIPAL, 1L, new TramiteDescartarRequest(0L)),
                controller.deshacerDescarte(PRINCIPAL, 1L, new TramiteDeshacerDescarteRequest(0L)))) {
            assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
            assertThat(respuesta.getBody()).isNull();
        }
    }

    private static TramiteController controllerQueLanza(Supplier<RuntimeException> fallo) {
        TramiteRevisionService servicio = new TramiteRevisionService(
                null, null, null, null, null, null, null, null, null, null) {
            // El controlador llama a las sobrecargas CON usuarioId (T3): se sobreescriben esas.
            @Override
            public TramiteDetalleResponse actualizar(Long gestoriaId, Long usuarioId, Long tramiteId, Long versionVista,
                                                     Long explotacionId, TipoTramite tipoTramite, List<String> crotales) {
                throw fallo.get();
            }

            @Override
            public TramiteResponse aprobar(Long gestoriaId, Long usuarioId, Long tramiteId, Long versionVista) {
                throw fallo.get();
            }

            @Override
            public TramiteResponse rechazar(Long gestoriaId, Long usuarioId, Long tramiteId, Long versionVista) {
                throw fallo.get();
            }

            @Override
            public TramiteResponse marcarHechoEnOvz(Long gestoriaId, Long usuarioId, Long tramiteId, Long versionVista) {
                throw fallo.get();
            }

            @Override
            public TramiteResponse desmarcarHechoEnOvz(Long gestoriaId, Long usuarioId, Long tramiteId,
                                                       Long versionVista) {
                throw fallo.get();
            }

            @Override
            public TramiteDetalleResponse descartar(Long gestoriaId, Long usuarioId, Long tramiteId, Long versionVista) {
                throw fallo.get();
            }

            @Override
            public TramiteDetalleResponse deshacerDescarte(Long gestoriaId, Long usuarioId, Long tramiteId,
                                                           Long versionVista) {
                throw fallo.get();
            }
        };
        SuscripcionService suscripcionActiva = new SuscripcionService(null, null) {
            @Override
            public boolean puedeAprobarTramites(Long gestoriaId) {
                return true;
            }
        };
        return new TramiteController(null, suscripcionActiva, null, null, servicio, null,
                org.mockito.Mockito.mock(com.ganera.core.shared.auditoria.AuditoriaService.class),
                org.mockito.Mockito.mock(com.ganera.core.shared.auditoria.AccesoAjeno.class));
    }
}
