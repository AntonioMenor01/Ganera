package com.ganera.core.whatsapp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Al construir el controller (arranque de la app) se avisa de como esta configurado el webhook,
 * para que una TWILIO_WEBHOOK_URL mal escrita no se confunda en el smoke con firmas falsas (ambas
 * dan 403). Solo el host: ni path ni query string ni el token.
 */
@ExtendWith(OutputCaptureExtension.class)
class TwilioWebhookConfiguracionLogTest {

    @Test
    void conUrlConfiguradaInformaSoloDelHost(CapturedOutput salida) {
        new TwilioWebhookController("token-inventado-xyz",
                "https://abc123.tunel.example/webhooks/twilio/whatsapp?clave=no-debe-salir", null, null, null);

        assertThat(salida.getOut()).contains("INFO").contains("abc123.tunel.example");
        assertThat(salida.getOut()).doesNotContain("/webhooks/twilio/whatsapp")
                .doesNotContain("no-debe-salir").doesNotContain("token-inventado-xyz");
    }

    @Test
    void sinUrlAvisaConWarn(CapturedOutput salida) {
        new TwilioWebhookController("token-inventado-xyz", "", null, null, null);

        assertThat(salida.getOut()).contains("WARN").contains("TWILIO_WEBHOOK_URL");
        assertThat(salida.getOut()).doesNotContain("token-inventado-xyz");
    }

    @Test
    void conUrlSinHostAvisaConWarnSinRepetirla(CapturedOutput salida) {
        new TwilioWebhookController("token-inventado-xyz", "no es una url secreto-raro", null, null, null);

        assertThat(salida.getOut()).contains("WARN").contains("TWILIO_WEBHOOK_URL");
        assertThat(salida.getOut()).doesNotContain("secreto-raro");
    }
}
