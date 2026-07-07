package com.ganera.core.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class MensajeCampoRepositoryTest {

    @Autowired
    private MensajeCampoRepository mensajeCampoRepository;

    @Test
    void unMessageSidDuplicadoNuncaCreaUnSegundoRegistro() {
        MensajeCampo primero = new MensajeCampo();
        primero.setMessageSid("SM123");
        primero.setTelefonoOrigen("+34600777888");
        primero.setCuerpo("Alta de 3 becerros crotal 1234");
        mensajeCampoRepository.saveAndFlush(primero);

        assertThat(mensajeCampoRepository.existsByMessageSid("SM123")).isTrue();

        MensajeCampo duplicado = new MensajeCampo();
        duplicado.setMessageSid("SM123");
        duplicado.setTelefonoOrigen("+34600777888");
        duplicado.setCuerpo("reintento de Twilio");

        assertThrows(DataIntegrityViolationException.class,
                () -> mensajeCampoRepository.saveAndFlush(duplicado));
    }
}
