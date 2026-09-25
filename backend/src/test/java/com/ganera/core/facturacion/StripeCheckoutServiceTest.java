package com.ganera.core.facturacion;

import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Solo prueba la parte pura de StripeCheckoutService (configuracionCompleta,
 * construirParametros, cantidadAContratar), segun la convencion
 * test-sin-mocks-externos: nada de red, nada de mocks del SDK de Stripe.
 * El unico metodo que toca la red (crearSesionEnStripe) queda sin cobertura
 * unitaria directa; se valida en el smoke test manual con clave de test.
 */
class StripeCheckoutServiceTest {

    private StripeCheckoutService construirServicio(String apiKey, String priceIdExplotacion) {
        return new StripeCheckoutService(null, null, null, apiKey, priceIdExplotacion,
                "https://ganera.test/facturacion/exito", "https://ganera.test/facturacion/cancelado");
    }

    @Test
    void configuracionCompletaEsFalsaConApiKeyEnBlanco() {
        assertThat(construirServicio("", "price_explotacion_test").configuracionCompleta()).isFalse();
    }

    @Test
    void configuracionCompletaEsFalsaConPriceIdEnBlanco() {
        assertThat(construirServicio("sk_test_123", "").configuracionCompleta()).isFalse();
    }

    @Test
    void configuracionCompletaEsVerdaderaConApiKeyYPriceIdPresentes() {
        assertThat(construirServicio("sk_test_123", "price_explotacion_test").configuracionCompleta()).isTrue();
    }

    @Test
    void construirParametrosProduceLaFormaEsperadaParaCheckoutSubscription() {
        StripeCheckoutService service = construirServicio("sk_test_123", "price_explotacion_test");

        SessionCreateParams params = service.construirParametros(42L, 3L);

        assertThat(params.getMode()).isEqualTo(SessionCreateParams.Mode.SUBSCRIPTION);
        assertThat(params.getClientReferenceId()).isEqualTo("42");
        assertThat(params.getLineItems()).hasSize(1);
        assertThat(params.getLineItems().get(0).getPrice()).isEqualTo("price_explotacion_test");
        assertThat(params.getLineItems().get(0).getQuantity()).isEqualTo(3L);
        assertThat(params.getSubscriptionData()).isNotNull();
        assertThat(params.getSubscriptionData().getTrialPeriodDays()).isEqualTo(15L);
    }

    @Test
    void cantidadAContratarNuncaBajaDeUno() {
        assertThat(StripeCheckoutService.cantidadAContratar(0)).isEqualTo(1);
        assertThat(StripeCheckoutService.cantidadAContratar(1)).isEqualTo(1);
        assertThat(StripeCheckoutService.cantidadAContratar(5)).isEqualTo(5);
    }

    @Test
    void crearSesionCheckoutConCantidadEstimadaRespetaLaMismaGuardaDeConfiguracionCompleta() throws Exception {
        StripeCheckoutService servicioSinApiKey = construirServicio("", "price_explotacion_test");
        StripeCheckoutService servicioSinPriceId = construirServicio("sk_test_123", "");

        assertThat(servicioSinApiKey.crearSesionCheckoutConCantidadEstimada(42L, 15L)).isEmpty();
        assertThat(servicioSinPriceId.crearSesionCheckoutConCantidadEstimada(42L, 15L)).isEmpty();
    }
}
