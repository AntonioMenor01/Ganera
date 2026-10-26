package com.ganera.core.facturacion;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Convencion test-sin-mocks-externos: estadoInicialDesdeStatus es logica pura (status de Stripe ->
 * EstadoSuscripcion). recuperarSubscription (Subscription.retrieve) queda sin cobertura unitaria
 * directa; se valida en el smoke test manual con clave de test.
 */
class EnlaceStripeServiceTest {

    @Test
    void mapeaLosStatusConocidos() {
        assertThat(EnlaceStripeService.estadoInicialDesdeStatus("trialing")).isEqualTo(EstadoSuscripcion.TRIAL);
        assertThat(EnlaceStripeService.estadoInicialDesdeStatus("active")).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(EnlaceStripeService.estadoInicialDesdeStatus("past_due")).isEqualTo(EstadoSuscripcion.IMPAGO_GRACIA);
        assertThat(EnlaceStripeService.estadoInicialDesdeStatus("unpaid")).isEqualTo(EstadoSuscripcion.SUSPENDIDA);
        assertThat(EnlaceStripeService.estadoInicialDesdeStatus("canceled")).isEqualTo(EstadoSuscripcion.CANCELADA);
    }

    @Test
    void unStatusNuloONoSoportadoLanza() {
        assertThatThrownBy(() -> EnlaceStripeService.estadoInicialDesdeStatus(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnlaceStripeService.estadoInicialDesdeStatus("incomplete"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- resolverPlan: plan de una suscripcion de plan fijo, NUNCA null ----

    @Test
    void resolverPlanDevuelveElConocidoCuandoLoHay() {
        assertThat(EnlaceStripeService.resolverPlan("gestion", "price_x", Map.of())).isEqualTo("gestion");
    }

    @Test
    void resolverPlanCaeEnElMapaDePreciosCuandoNoHayConocido() {
        Map<String, StripeWebhookService.PlanPeriodo> mapa =
                StripeWebhookService.parsearMapaPrecios("price_x=plus:mensual");

        assertThat(EnlaceStripeService.resolverPlan(null, "price_x", mapa)).isEqualTo("plus");
    }

    @Test
    void resolverPlanCaeEnDesconocidoSinConocidoNiPriceNiMapa() {
        assertThat(EnlaceStripeService.resolverPlan(null, null, Map.of())).isEqualTo("desconocido");
        assertThat(EnlaceStripeService.resolverPlan(null, "price_no_mapeado", Map.of())).isEqualTo("desconocido");
    }
}
