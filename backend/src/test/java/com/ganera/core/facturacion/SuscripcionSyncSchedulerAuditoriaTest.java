package com.ganera.core.facturacion;

import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.ActorTipo;
import com.ganera.core.shared.auditoria.Auditoria;
import com.ganera.core.shared.auditoria.AuditoriaRepository;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.auditoria.LimpiezaAuditoria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoria del planificador nocturno de suscripciones (T4, accion SUSCRIPCION_CANTIDAD_SINCRONIZADA). La unica
 * pieza de red (pushCantidadAStripe) se sobreescribe a no-op (convencion test-sin-mocks-externos, el mismo seam
 * que valida el smoke manual); el resto -- repositorios, AuditoriaService, transacciones -- es real sobre H2.
 *
 * <p>Actor PLANIFICADOR (sin peticion -> ip/request_id NULL); la gestoria de la fila sale de la propia suscripcion,
 * nunca de un contexto. Con dos gestorias cruzadas: la que cambia deja su fila en SU gestoria; la que ya coincide,
 * ninguna.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SuscripcionSyncSchedulerAuditoriaTest {

    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private com.ganera.core.shared.auditoria.AuditoriaService auditoriaService;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void limpiar() {
        LimpiezaAuditoria.borrar(jdbcTemplate);
        suscripcionRepository.deleteAll();
        gestoriaRepository.deleteAll();
    }

    /** Un planificador con Stripe "configurado" (apiKey no vacia) pero sin tocar la red. */
    private SuscripcionSyncScheduler planificadorSinRed() {
        return new SuscripcionSyncScheduler(suscripcionRepository, explotacionRepository, auditoriaService,
                transactionManager, "sk_test_inventada") {
            @Override
            void pushCantidadAStripe(String stripeSubscriptionId, long nuevaCantidad) {
                // no-op: el push real a Stripe es la unica linea de red y no se ejerce aqui.
            }
        };
    }

    private Gestoria gestoria(String nombre) {
        return gestoriaRepository.save(new Gestoria(nombre));
    }

    private void suscripcion(Gestoria gestoria, String stripeSubscriptionId, int explotacionesContratadas) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcion.setStripeSubscriptionId(stripeSubscriptionId);
        suscripcion.setExplotacionesContratadas(explotacionesContratadas);
        suscripcionRepository.save(suscripcion);
    }

    @Test
    void laSincronizacionDejaUnaFilaEnLaGestoriaCuyaCantidadCambiaYNingunaEnLaQueYaCoincide() {
        // A: contratadas=5, 0 explotaciones -> nueva cantidad max(1,0)=1 -> cambia. B: contratadas=1, 0 -> no cambia.
        Gestoria gestoriaA = gestoria("Gestoria sync A");
        Gestoria gestoriaB = gestoria("Gestoria sync B");
        suscripcion(gestoriaA, "sub_A", 5);
        suscripcion(gestoriaB, "sub_B", 1);

        planificadorSinRed().reconciliarCantidadesConStripe();

        Suscripcion suscripcionA = suscripcionRepository.findByGestoriaId(gestoriaA.getId()).orElseThrow();
        assertThat(suscripcionA.getExplotacionesContratadas()).isEqualTo(1);

        List<Auditoria> filasA = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaA.getId());
        assertThat(filasA).extracting(Auditoria::getAccion)
                .containsExactly(AccionAuditoria.SUSCRIPCION_CANTIDAD_SINCRONIZADA);
        Auditoria fila = filasA.get(0);
        assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.PLANIFICADOR);
        assertThat(fila.getUsuarioId()).isNull();
        assertThat(fila.getIp()).isNull();
        assertThat(fila.getRequestId()).isNull();
        assertThat(fila.getEntidadTipo()).isEqualTo(EntidadAuditada.SUSCRIPCION);
        assertThat(fila.getEntidadId()).isEqualTo(suscripcionA.getId());
        assertThat(fila.getAntes()).contains("\"explotaciones_contratadas\":5");
        assertThat(fila.getDespues()).contains("\"explotaciones_contratadas\":1");

        assertThat(auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoriaB.getId())).isEmpty();
    }
}
