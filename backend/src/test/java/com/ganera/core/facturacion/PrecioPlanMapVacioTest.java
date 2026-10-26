package com.ganera.core.facturacion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sin STRIPE_PRICE_PLAN_MAP (ni la propiedad ni la variable de entorno), el contexto arranca y el mapa
 * de precios del bean queda vacio (las altas de la landing se reconocen igual por source=ganera-web; el
 * mapa solo hace falta para Payment Links).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PrecioPlanMapVacioTest {

    @Autowired
    private StripeWebhookService stripeWebhookService;

    @Test
    void sinLaVariableElMapaArrancaVacio() {
        assertThat(stripeWebhookService.mapaPrecios()).isEmpty();
    }
}
