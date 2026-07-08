package com.ganera.core.facturacion;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class StripeWebhookControllerTest {

    @Test
    void unaFirmaInvalidaEsRechazadaPorElSdkRealDeStripe() {
        String payload = "{\"id\":\"evt_test\"}";
        String secreto = "whsec_test_secret";
        String firmaInvalida = "t=1,v1=firma-que-no-encaja";

        assertThrows(SignatureVerificationException.class,
                () -> Webhook.constructEvent(payload, firmaInvalida, secreto));
    }
}
