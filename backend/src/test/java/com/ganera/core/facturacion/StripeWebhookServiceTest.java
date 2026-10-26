package com.ganera.core.facturacion;

import com.ganera.core.facturacion.StripeWebhookService.DatosCheckout;
import com.ganera.core.facturacion.StripeWebhookService.PlanPeriodo;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.tiempo.RelojConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
@Import({SuscripcionService.class, StripeWebhookService.class, RelojConfig.class})
class StripeWebhookServiceTest {

    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private AltaPendienteRepository altaPendienteRepository;
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

    // ---- Bug 1 (CLAUDE.md > Stripe): payment_failed sobre TRIAL_EXPIRADO_SIN_PAGO NO re-habilita ----

    @Test
    void procesarPagoFallidoDesdeTrialExpiradoSinPagoSeQuedaIgualYNoPasaAImpagoGracia() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria bug1"));
        nuevaSuscripcion(gestoria, EstadoSuscripcion.TRIAL_EXPIRADO_SIN_PAGO, "sub_bug1");

        stripeWebhookService.procesarPagoFallido("sub_bug1", 1_000L);

        Suscripcion recargada = suscripcionRepository.findByStripeSubscriptionId("sub_bug1").orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoSuscripcion.TRIAL_EXPIRADO_SIN_PAGO);
    }

    // ---- parsearMapaPrecios (STRIPE_PRICE_PLAN_MAP) ----

    @Test
    void parsearMapaPreciosVacioDaMapaVacio() {
        assertThat(StripeWebhookService.parsearMapaPrecios("")).isEmpty();
        assertThat(StripeWebhookService.parsearMapaPrecios(null)).isEmpty();
        assertThat(StripeWebhookService.parsearMapaPrecios("   ")).isEmpty();
    }

    @Test
    void parsearMapaPreciosLeeVariasEntradasYRecortaEspacios() {
        Map<String, PlanPeriodo> mapa = StripeWebhookService.parsearMapaPrecios(
                "price_abc=base:mensual, price_def = gestion:anual ");

        assertThat(mapa).containsOnlyKeys("price_abc", "price_def");
        assertThat(mapa.get("price_abc")).isEqualTo(new PlanPeriodo("base", "mensual"));
        assertThat(mapa.get("price_def")).isEqualTo(new PlanPeriodo("gestion", "anual"));
    }

    @Test
    void parsearMapaPreciosMalFormadoLanza() {
        assertThatThrownBy(() -> StripeWebhookService.parsearMapaPrecios("price_abc=base"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StripeWebhookService.parsearMapaPrecios("=base:mensual"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Extractores del JSON crudo (formato endive de la API 2026-09-30) ----

    @Test
    void extraerCheckoutLeeIdsMetadataYDatosDeFacturacion() {
        String json = """
                {
                  "id": "cs_test_123",
                  "object": "checkout.session",
                  "customer": "cus_abc",
                  "subscription": "sub_abc",
                  "client_reference_id": null,
                  "metadata": {"plan": "gestion", "periodo": "anual", "prueba": "true", "source": "ganera-web"},
                  "customer_details": {
                    "email": "gestoria@example.com",
                    "name": "Gestoría Ejemplo S.L.",
                    "address": {"line1": "Calle Mayor 1", "line2": null, "postal_code": "06001",
                                "city": "Badajoz", "state": "BA", "country": "ES"},
                    "tax_ids": [{"type": "es_cif", "value": "B12345678"}]
                  }
                }
                """;

        DatosCheckout d = StripeWebhookService.extraerCheckout(json);

        assertThat(d.sessionId()).isEqualTo("cs_test_123");
        assertThat(d.clientReferenceId()).isNull();
        assertThat(d.customerId()).isEqualTo("cus_abc");
        assertThat(d.subscriptionId()).isEqualTo("sub_abc");
        assertThat(d.source()).isEqualTo("ganera-web");
        assertThat(d.planMeta()).isEqualTo("gestion");
        assertThat(d.periodoMeta()).isEqualTo("anual");
        assertThat(d.prueba()).isTrue();
        assertThat(d.email()).isEqualTo("gestoria@example.com");
        assertThat(d.nombre()).isEqualTo("Gestoría Ejemplo S.L.");
        assertThat(d.nif()).isEqualTo("B12345678");
        assertThat(d.direccion()).isEqualTo("Calle Mayor 1, 06001, Badajoz, BA, ES");
    }

    @Test
    void extraerCheckoutSinMetadataNiDetallesNoRompe() {
        DatosCheckout d = StripeWebhookService.extraerCheckout(
                "{\"id\":\"cs_x\",\"customer\":\"cus_x\",\"subscription\":\"sub_x\"}");

        assertThat(d.sessionId()).isEqualTo("cs_x");
        assertThat(d.source()).isNull();
        assertThat(d.planMeta()).isNull();
        assertThat(d.prueba()).isNull();
        assertThat(d.email()).isNull();
        assertThat(d.nif()).isNull();
        assertThat(d.direccion()).isNull();
    }

    @Test
    void extraerSubscriptionIdDeInvoiceFormatoViejoLeeElCampoDirecto() {
        assertThat(StripeWebhookService.extraerSubscriptionIdDeInvoice(
                "{\"id\":\"in_1\",\"subscription\":\"sub_viejo\"}")).isEqualTo("sub_viejo");
    }

    @Test
    void extraerSubscriptionIdDeInvoiceFormatoEndiveLeeParentSubscriptionDetails() {
        String endive = """
                {
                  "id": "in_2",
                  "object": "invoice",
                  "subscription": null,
                  "parent": {"type": "subscription_details",
                             "subscription_details": {"subscription": "sub_endive"}}
                }
                """;

        assertThat(StripeWebhookService.extraerSubscriptionIdDeInvoice(endive)).isEqualTo("sub_endive");
    }

    @Test
    void extraerSubscriptionIdDeInvoiceSinNingunaDaNull() {
        assertThat(StripeWebhookService.extraerSubscriptionIdDeInvoice("{\"id\":\"in_3\"}")).isNull();
    }

    @Test
    void extraerSuscripcionLeeIdYStatus() {
        var d = StripeWebhookService.extraerSuscripcion(
                "{\"id\":\"sub_s\",\"object\":\"subscription\",\"status\":\"active\"}");

        assertThat(d.id()).isEqualTo("sub_s");
        assertThat(d.status()).isEqualTo("active");
    }

    // ---- registrarAltaPendiente: idempotente por stripe_session_id ----

    @Test
    void registrarAltaPendienteGuardaLaFilaEnPendiente() {
        DatosCheckout d = new DatosCheckout("cs_alta", null, "cus_1", "sub_1", "ganera-web",
                "plus", "mensual", true, "a@b.com", "Gestoría X", "B99999999", "Calle 1, Badajoz, ES");

        stripeWebhookService.registrarAltaPendiente(d, "plus", "mensual");

        AltaPendiente alta = altaPendienteRepository.findByStripeSessionId("cs_alta").orElseThrow();
        assertThat(alta.getEstado()).isEqualTo(EstadoAltaPendiente.PENDIENTE);
        assertThat(alta.getSubscriptionId()).isEqualTo("sub_1");
        assertThat(alta.getPlan()).isEqualTo("plus");
        assertThat(alta.getPeriodo()).isEqualTo("mensual");
        assertThat(alta.getNif()).isEqualTo("B99999999");
        assertThat(alta.getPrueba()).isTrue();
        assertThat(alta.getCreadoEn()).isNotNull();
    }

    @Test
    void registrarAltaPendienteEsIdempotenteEnRedeliveryDeStripe() {
        DatosCheckout d = new DatosCheckout("cs_dup", null, "cus_1", "sub_1", "ganera-web",
                "base", "anual", false, "a@b.com", "Gestoría X", null, null);

        stripeWebhookService.registrarAltaPendiente(d, "base", "anual");
        stripeWebhookService.registrarAltaPendiente(d, "base", "anual");

        assertThat(altaPendienteRepository.findAll())
                .filteredOn(a -> a.getStripeSessionId().equals("cs_dup"))
                .hasSize(1);
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
