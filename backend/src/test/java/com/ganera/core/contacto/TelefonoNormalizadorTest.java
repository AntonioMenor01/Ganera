package com.ganera.core.contacto;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TelefonoNormalizadorTest {

    @ParameterizedTest
    @CsvSource({
            "'612 345 678',            +34612345678",
            "'612.345.678',            +34612345678",
            "'612-345-678',            +34612345678",
            "'whatsapp:+34612345678',  +34612345678",
            "'WhatsApp:612345678',     +34612345678",
            "'0034612345678',          +34612345678",
            "'34612345678',            +34612345678",
            "'+447911123456',          +447911123456",
            "'+44791112345',           +44791112345",
            "'+512345678',             +512345678",
            "'712345678',              +34712345678",
            "'912345678',              +34912345678",
            "'612\u00A0345\u00A0678',  +34612345678",
            "'612\u2007345\u202F678',  +34612345678"
    })
    void normalizaTelefonosValidosAE164(String entrada, String esperado) {
        assertThat(TelefonoNormalizador.normalizar(entrada)).contains(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345",
            "512345678",
            "+3451234567",
            "+34612345",
            "61234567a",
            "(612)345678",
            "+",
            "   ",
            "+1234567",
            "+1234567890123456",
            "+0034612345678",
            "+06123456789",
            "+612345678",
            "+912345678"
    })
    void rechazaTelefonosInvalidos(String entrada) {
        assertThat(TelefonoNormalizador.normalizar(entrada)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rechazaNuloYVacio(String entrada) {
        assertThat(TelefonoNormalizador.normalizar(entrada)).isEmpty();
    }
}
