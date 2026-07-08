package com.ganera.core.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class TwilioWebhookIdempotencyTest {

    @Autowired
    private MensajeCampoRepository mensajeCampoRepository;

    @Test
    void unMessageSidYaGuardadoNoDebeVolverAGuardarse() {
        TwilioWebhookController controller = new TwilioWebhookController("", mensajeCampoRepository);

        assertThat(controller.debeGuardarNuevoMensaje("SM999")).isTrue();

        MensajeCampo existente = new MensajeCampo();
        existente.setMessageSid("SM999");
        existente.setTelefonoOrigen("+34600000000");
        existente.setCuerpo("mensaje original");
        mensajeCampoRepository.saveAndFlush(existente);

        assertThat(controller.debeGuardarNuevoMensaje("SM999")).isFalse();
    }
}
