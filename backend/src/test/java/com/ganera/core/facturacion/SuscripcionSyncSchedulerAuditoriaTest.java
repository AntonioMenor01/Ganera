package com.ganera.core.facturacion;

import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.ActorTipo;
import com.ganera.core.shared.auditoria.Auditoria;
import com.ganera.core.shared.auditoria.AuditoriaRepository;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.tiempo.RelojConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Auditoria base — T4: la reconciliacion nocturna de cantidades (SuscripcionSyncScheduler) deja una fila
 * SUSCRIPCION_CANTIDAD_SINCRONIZADA por suscripcion ajustada, con la gestoria de CADA suscripcion (datos cruzados:
 * procesa las dos gestorias) y, por ser un planificador sin peticion, ip y request_id NULL. El push a Stripe se
 * anula (no hay red); la logica que decide los ajustes es SuscripcionSyncScheduler.calcularAjustes (su test aparte).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({AuditoriaService.class, RelojConfig.class})
class SuscripcionSyncSchedulerAuditoriaTest {

    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private AuditoriaService auditoriaService;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void cadaSuscripcionAjustadaDejaSuFilaEnSuGestoriaConIpYRequestIdNull() {
        Gestoria a = gestoriaRepository.save(new Gestoria("Sync A"));
        Gestoria b = gestoriaRepository.save(new Gestoria("Sync B"));
        suscripcion(a, "subA", 1);
        suscripcion(b, "subB", 5);
        explotaciones(a, 2); // count 2 != 1 -> ajuste a 2
        explotaciones(b, 3); // count 3 != 5 -> ajuste a 3

        schedulerSinRed().reconciliarCantidadesConStripe();

        Auditoria filaA = unica(a);
        assertThat(filaA.getAccion()).isEqualTo(AccionAuditoria.SUSCRIPCION_CANTIDAD_SINCRONIZADA);
        assertThat(filaA.getActorTipo()).isEqualTo(ActorTipo.PLANIFICADOR);
        assertThat(filaA.getUsuarioId()).isNull();
        assertThat(filaA.getIp()).isNull();
        assertThat(filaA.getRequestId()).isNull();
        assertThat(filaA.getAntes()).contains("\"explotaciones_contratadas\":1");
        assertThat(filaA.getDespues()).contains("\"explotaciones_contratadas\":2");

        Auditoria filaB = unica(b);
        assertThat(filaB.getActorTipo()).isEqualTo(ActorTipo.PLANIFICADOR);
        assertThat(filaB.getAntes()).contains("\"explotaciones_contratadas\":5");
        assertThat(filaB.getDespues()).contains("\"explotaciones_contratadas\":3");
    }

    /** Scheduler con apiKey no vacia (para que no se salte la reconciliacion) y el push a Stripe anulado. */
    private SuscripcionSyncScheduler schedulerSinRed() {
        return new SuscripcionSyncScheduler(suscripcionRepository, explotacionRepository, auditoriaService,
                transactionManager, "sk_test_123") {
            @Override
            void pushCantidadAStripe(String stripeSubscriptionId, long nuevaCantidad) {
                // sin red
            }
        };
    }

    private Auditoria unica(Gestoria gestoria) {
        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(gestoria.getId());
        assertThat(filas).hasSize(1);
        return filas.get(0);
    }

    private void suscripcion(Gestoria gestoria, String stripeSubscriptionId, int contratadas) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcion.setStripeSubscriptionId(stripeSubscriptionId);
        suscripcion.setExplotacionesContratadas(contratadas);
        suscripcionRepository.save(suscripcion);
    }

    private void explotaciones(Gestoria gestoria, int cuantas) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero " + gestoria.getNombre());
        ganaderoRepository.save(ganadero);
        for (int i = 0; i < cuantas; i++) {
            Explotacion explotacion = new Explotacion();
            explotacion.setGestoria(gestoria);
            explotacion.setGanadero(ganadero);
            explotacion.setCodigoRega("ES" + gestoria.getId() + String.format("%010d", i));
            explotacion.setNombre("Finca " + i);
            explotacionRepository.save(explotacion);
        }
    }
}
