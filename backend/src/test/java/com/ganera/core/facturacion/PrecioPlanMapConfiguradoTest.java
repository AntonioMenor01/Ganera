package com.ganera.core.facturacion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Con STRIPE_PRICE_PLAN_MAP puesta como propiedad del entorno del contexto, el bean
 * StripeWebhookService la lee (via @Value("${ganera.stripe.precio-plan-map:${STRIPE_PRICE_PLAN_MAP:}}"))
 * y el mapa se carga con los 6 pares (base/gestion/plus x mensual/anual). Prueba el CABLEADO: sin esto,
 * en Clever el mapa llegaria vacio en silencio. Price ids de ejemplo (no los reales) a proposito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "STRIPE_PRICE_PLAN_MAP=price_base_m=base:mensual,price_base_a=base:anual,"
                + "price_ges_m=gestion:mensual,price_ges_a=gestion:anual,"
                + "price_plus_m=plus:mensual,price_plus_a=plus:anual")
class PrecioPlanMapConfiguradoTest {

    @Autowired
    private StripeWebhookService stripeWebhookService;

    @Test
    void elMapaSeCargaConLos6ParesDesdeLaVariableDeEntorno() {
        assertThat(stripeWebhookService.mapaPrecios()).hasSize(6);
        assertThat(stripeWebhookService.mapaPrecios().get("price_base_m"))
                .isEqualTo(new StripeWebhookService.PlanPeriodo("base", "mensual"));
        assertThat(stripeWebhookService.mapaPrecios().get("price_plus_a"))
                .isEqualTo(new StripeWebhookService.PlanPeriodo("plus", "anual"));
    }
}
