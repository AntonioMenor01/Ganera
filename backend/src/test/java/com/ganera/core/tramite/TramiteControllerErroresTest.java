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
    void losFallosDeConcurrenciaEnPatchAprobarYRechazarSon409ConMotivo() {
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
                    controller.rechazar(PRINCIPAL, 1L, new TramiteRechazarRequest(0L)))) {
                assertThat(respuesta.getStatusCode().value()).as(fallo.getClass().getSimpleName()).isEqualTo(409);
                assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse(TramiteController.MOTIVO_CONCURRENCIA));
            }
        }
    }

    private static TramiteController controllerQueLanza(Supplier<RuntimeException> fallo) {
        TramiteRevisionService servicio = new TramiteRevisionService(null, null, null, null, null) {
            @Override
            public TramiteDetalleResponse actualizar(Long gestoriaId, Long tramiteId, Long versionVista,
                                                     Long explotacionId, TipoTramite tipoTramite, List<String> crotales) {
                throw fallo.get();
            }

            @Override
            public TramiteResponse aprobar(Long gestoriaId, Long tramiteId, Long versionVista) {
                throw fallo.get();
            }

            @Override
            public TramiteResponse rechazar(Long gestoriaId, Long tramiteId, Long versionVista) {
                throw fallo.get();
            }
        };
        SuscripcionService suscripcionActiva = new SuscripcionService(null, null) {
            @Override
            public boolean puedeAprobarTramites(Long gestoriaId) {
                return true;
            }
        };
        return new TramiteController(null, suscripcionActiva, null, null, servicio);
    }
}
