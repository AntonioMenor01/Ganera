package com.ganera.core.shared.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedStringConverterTest {

    private static final String VALID_KEY_BASE64 =
            java.util.Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes());

    @Test
    void cifraYDescifraDevuelveElValorOriginal() {
        EncryptedStringConverter converter = new EncryptedStringConverter(() -> VALID_KEY_BASE64);

        String original = "mi-password-secreto-de-ovz";
        String cifrado = converter.convertToDatabaseColumn(original);

        assertThat(cifrado).isNotNull().isNotEqualTo(original);
        assertThat(converter.convertToEntityAttribute(cifrado)).isEqualTo(original);
    }

    @Test
    void cadaLlamadaProduceUnCifradoDistintoPorElIvAleatorio() {
        EncryptedStringConverter converter = new EncryptedStringConverter(() -> VALID_KEY_BASE64);

        String cifrado1 = converter.convertToDatabaseColumn("mismo-valor");
        String cifrado2 = converter.convertToDatabaseColumn("mismo-valor");

        assertThat(cifrado1).isNotEqualTo(cifrado2);
    }

    @Test
    void nullSeMantieneNull() {
        EncryptedStringConverter converter = new EncryptedStringConverter(() -> VALID_KEY_BASE64);

        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void claveAusenteFallaExplicitamenteAlUsarla() {
        EncryptedStringConverter converter = new EncryptedStringConverter(() -> "");

        assertThatThrownBy(() -> converter.convertToDatabaseColumn("valor"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void claveDeTamanoIncorrectoFallaExplicitamente() {
        String claveCorta = java.util.Base64.getEncoder().encodeToString("demasiado-corta".getBytes());
        EncryptedStringConverter converter = new EncryptedStringConverter(() -> claveCorta);

        assertThatThrownBy(() -> converter.convertToDatabaseColumn("valor"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void verificacionAlArrancarFallaSiLaClaveFaltaOEsInvalida() {
        EncryptedStringConverter converter = new EncryptedStringConverter(() -> "");
        assertThatThrownBy(converter::verificarClaveAlArrancar).isInstanceOf(IllegalStateException.class);
    }
}
