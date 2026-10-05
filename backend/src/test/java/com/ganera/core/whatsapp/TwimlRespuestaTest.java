package com.ganera.core.whatsapp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TwimlRespuestaTest {

    @Test
    void elAcuseEsElTextoDeD7EnUnMessage() {
        assertThat(TwilioWebhookController.ACUSE_RECIBO).isEqualTo("Recibido, tu gestoría lo revisará.");
        assertThat(TwilioWebhookController.TWIML_ACUSE).isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Response><Message>Recibido, tu gestoría lo revisará.</Message></Response>");
        assertThat(TwilioWebhookController.TWIML_VACIO).isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response/>");
    }

    @Test
    void escaparXmlCambiaLosCaracteresEspeciales() {
        assertThat(TwilioWebhookController.escaparXml("a & b < c > d \"e\" 'f' ñá"))
                .isEqualTo("a &amp; b &lt; c &gt; d &quot;e&quot; &apos;f&apos; ñá");
    }
}
