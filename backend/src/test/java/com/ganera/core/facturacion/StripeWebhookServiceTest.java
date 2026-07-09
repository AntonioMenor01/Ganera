package com.ganera.core.facturacion;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Segun la convencion test-sin-mocks-externos: la maquina de estados
 * (calcularEstadoTrasActualizacion, esEventoObsoleto) se testea pura, sin
 * ningun tipo del SDK de Stripe. Los procesarXxx se testean con repos reales
 * via @DataJpaTest. Los manejarXxx (que si deserializan Event/Session/
 * Invoice/Subscription) quedan sin cobertura unitaria directa -- se validan
 * en el smoke test (Task 4).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({SuscripcionService.class, StripeWebhookService.class})
class StripeWebhookServiceTest {

    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private StripeWebhookService stripeWebhookService;

    // ---- calcularEstadoTrasActualizacion: los 8+1 casos del mapa (Decision de diseño 4) ----

    @Test
    void activeMapeaAActiva() {
        assertThat(StripeWebhookService.calcularEstadoTrasActualizacion("active", null, EstadoSuscripcion.TRIAL))
                .isEqualTo(EstadoSuscripcion.ACTIVA);
    }

    @Test
    void pastDueConPreviousTrialingMapeaATrialExpiradoSinPago() {
        assertThat(StripeWebhookService.calcularEstadoTrasActualizacion("past_due", "trialing", EstadoSuscripcion.TRIAL))
                .isEqualTo(EstadoSuscripcion.TRIAL_EXPIRADO_SIN_PAGO);
    }

    @Test
    void pastDueConPreviousNoTrialingMapeaAImpagoGracia() {
        assertThat(StripeWebhookService.calcularEstadoTrasActualizacion("past_due", "active", EstadoSuscripcion.ACTIVA))
                .isEqualTo(EstadoSuscripcion.IMPAGO_GRACIA);
    }

    @Test
    void pastDueSinPreviousYEstadoActualTrialMapeaATrialExpiradoSinPago() {
        assertThat(StripeWebhookService.calcularEstadoTrasActualizacion("past_due", null, EstadoSuscripcion.TRIAL))
                .isEqualTo(EstadoSuscripcion.TRIAL_EXPIRADO_SIN_PAGO);
    }

    @Test
    void pastDueSinPreviousYEstadoActualOtroMapeaAImpagoGracia() {
        assertThat(StripeWebhookService.calcularEstadoTrasActualizacion("past_due", null, EstadoSuscripcion.ACTIVA))
                .isEqualTo(EstadoSuscripcion.IMPAGO_GRACIA);
    }

    @Test
    void unpaidMapeaASuspendida() {
        assertThat(StripeWebhookService.calcularEstadoTrasActualizacion("unpaid", null, EstadoSuscripcion.ACTIVA))
                .isEqualTo(EstadoSuscripcion.SUSPENDIDA);
    }

    @Test
    void canceledMapeaACancelada() {
        assertThat(StripeWebhookService.calcularEstadoTrasActualizacion("canceled", null, EstadoSuscripcion.ACTIVA))
                .isEqualTo(EstadoSuscripcion.CANCELADA);
    }

    @Test
    void trialingMapeaATrial() {
        assertThat(StripeWebhookService.calcularEstadoTrasActualizacion("trialing", null, EstadoSuscripcion.ACTIVA))
                .isEqualTo(EstadoSuscripcion.TRIAL);
    }

    @Test
    void statusDesconocidoMapeaANullONoCambiarNada() {
        assertThat(StripeWebhookService.calcularEstadoTrasActualizacion("incomplete_expired", null, EstadoSuscripcion.ACTIVA))
                .isNull();
    }

    // ---- esEventoObsoleto: guard monotonico ----

    @Test
    void esEventoObsoletoCuandoEventCreatedEsMenorQueElAlmacenado() {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setStripeUltimoEventoEpoch(200L);

        assertThat(StripeWebhookService.esEventoObsoleto(100L, suscripcion)).isTrue();
    }

    @Test
    void noEsObsoletoCuandoElAlmacenadoEsNullPrimerEvento() {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setStripeUltimoEventoEpoch(null);

        assertThat(StripeWebhookService.esEventoObsoleto(100L, suscripcion)).isFalse();
    }

    @Test
    void noEsObsoletoCuandoEventCreatedEsIgualOMayorQueElAlmacenado() {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setStripeUltimoEventoEpoch(200L);

        assertThat(StripeWebhookService.esEventoObsoleto(200L, suscripcion)).isFalse();
        assertThat(StripeWebhookService.esEventoObsoleto(300L, suscripcion)).isFalse();
    }

    // ---- procesarCheckoutCompletado ----

    @Test
    void procesarCheckoutCompletadoCreaLaFilaConIdsYTrial() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria checkout"));

        stripeWebhookService.procesarCheckoutCompletado(gestoria.getId(), "cus_123", "sub_123", 1_000L);

        Suscripcion guardada = suscripcionRepository.findByGestoriaId(gestoria.getId()).orElseThrow();
        assertThat(guardada.getEstado()).isEqualTo(EstadoSuscripcion.TRIAL);
        assertThat(guardada.getStripeCustomerId()).isEqualTo("cus_123");
        assertThat(guardada.getStripeSubscriptionId()).isEqualTo("sub_123");
        assertThat(guardada.getStripeUltimoEventoEpoch()).isEqualTo(1_000L);
    }

    @Test
    void procesarCheckoutCompletadoEsIdempotenteEnRedeliveryDeStripe() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria redelivery"));

        stripeWebhookService.procesarCheckoutCompletado(gestoria.getId(), "cus_123", "sub_123", 1_000L);
        stripeWebhookService.procesarCheckoutCompletado(gestoria.getId(), "cus_123", "sub_123", 1_000L);

        assertThat(suscripcionRepository.findAll())
                .filteredOn(s -> s.getGestoria().getId().equals(gestoria.getId()))
                .hasSize(1);
        Suscripcion guardada = suscripcionRepository.findByGestoriaId(gestoria.getId()).orElseThrow();
        assertThat(guardada.getEstado()).isEqualTo(EstadoSuscripcion.TRIAL);
        assertThat(guardada.getStripeSubscriptionId()).isEqualTo("sub_123");
    }

    // ---- procesarPagoExitoso ----

    @Test
    void procesarPagoExitosoMarcaActiva() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria pago exitoso"));
        Suscripcion suscripcion = nuevaSuscripcion(gestoria, EstadoSuscripcion.TRIAL, "sub_ok");

        stripeWebhookService.procesarPagoExitoso("sub_ok", 1_000L);

        Suscripcion recargada = suscripcionRepository.findById(suscripcion.getId()).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(recargada.getStripeUltimoEventoEpoch()).isEqualTo(1_000L);
    }

    @Test
    void procesarPagoExitosoConSubscriptionIdDesconocidoNoLanza() {
        assertThat(catchThrowable(() -> stripeWebhookService.procesarPagoExitoso("sub_no_existe", 1_000L)))
                .isNull();
    }

    // ---- procesarPagoFallido ----

    @Test
    void procesarPagoFallidoDesdeTrialVaATrialExpiradoSinPago() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria pago fallido trial"));
        nuevaSuscripcion(gestoria, EstadoSuscripcion.TRIAL, "sub_trial");

        stripeWebhookService.procesarPagoFallido("sub_trial", 1_000L);

        Suscripcion recargada = suscripcionRepository.findByStripeSubscriptionId("sub_trial").orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoSuscripcion.TRIAL_EXPIRADO_SIN_PAGO);
    }

    @Test
    void procesarPagoFallidoDesdeActivaVaAImpagoGracia() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria pago fallido activa"));
        nuevaSuscripcion(gestoria, EstadoSuscripcion.ACTIVA, "sub_activa");

        stripeWebhookService.procesarPagoFallido("sub_activa", 1_000L);

        Suscripcion recargada = suscripcionRepository.findByStripeSubscriptionId("sub_activa").orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoSuscripcion.IMPAGO_GRACIA);
    }

    // ---- procesarSuscripcionActualizada ----

    @Test
    void procesarSuscripcionActualizadaAplicaElMapaYPersiste() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria actualizada"));
        nuevaSuscripcion(gestoria, EstadoSuscripcion.ACTIVA, "sub_upd");

        stripeWebhookService.procesarSuscripcionActualizada("sub_upd", "unpaid", null, 1_000L);

        Suscripcion recargada = suscripcionRepository.findByStripeSubscriptionId("sub_upd").orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoSuscripcion.SUSPENDIDA);
    }

    @Test
    void procesarSuscripcionActualizadaConStatusDesconocidoEsNoOp() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria status desconocido"));
        nuevaSuscripcion(gestoria, EstadoSuscripcion.ACTIVA, "sub_unknown");

        stripeWebhookService.procesarSuscripcionActualizada("sub_unknown", "incomplete_expired", null, 1_000L);

        Suscripcion recargada = suscripcionRepository.findByStripeSubscriptionId("sub_unknown").orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
    }

    // ---- Guard monotonico: efecto de integracion completo (re-entrega tardia) ----

    @Test
    void reentregaTardiaDeCheckoutCompletadoNoRegresaElEstadoNiElEpoch() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria guard integracion"));
        nuevaSuscripcion(gestoria, EstadoSuscripcion.TRIAL, "sub_guard");

        stripeWebhookService.procesarPagoExitoso("sub_guard", 2_000L);
        Suscripcion trasPagoExitoso = suscripcionRepository.findByStripeSubscriptionId("sub_guard").orElseThrow();
        assertThat(trasPagoExitoso.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);

        // Re-entrega tardia de un checkout.session.completed anterior (created menor):
        // debe descartarse por el guard y NO regresar la Suscripcion a TRIAL.
        stripeWebhookService.procesarCheckoutCompletado(gestoria.getId(), "cus_guard", "sub_guard", 1_000L);

        Suscripcion trasReentrega = suscripcionRepository.findByStripeSubscriptionId("sub_guard").orElseThrow();
        assertThat(trasReentrega.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(trasReentrega.getStripeUltimoEventoEpoch()).isEqualTo(2_000L);
    }

    private Suscripcion nuevaSuscripcion(Gestoria gestoria, EstadoSuscripcion estado, String stripeSubscriptionId) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(estado);
        suscripcion.setStripeSubscriptionId(stripeSubscriptionId);
        return suscripcionRepository.save(suscripcion);
    }

    private Throwable catchThrowableFrom(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
