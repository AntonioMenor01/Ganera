package com.ganera.core.facturacion;

import org.junit.jupiter.api.Test;

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
}
