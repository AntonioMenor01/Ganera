package com.ganera.core.shared.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T1 de M2 (descifrado bajo demanda). La logica AES-256-GCM se extrae de EncryptedStringConverter a
 * CifradorAesGcm: cifra devolviendo un {@link CredencialOvzCifrada} (tipo que solo el cifrador crea) y
 * descifra el ciphertext en reposo. Mantiene la validacion de la clave al arrancar.
 *
 * <p>La clave es INVENTADA solo para el test (32 bytes ASCII, base64); nunca la del entorno.
 */
class CifradorAesGcmTest {

    /** Base64 de 32 bytes ASCII. Falsa a proposito. */
    private static final String CLAVE_VALIDA =
            Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes());

    private CifradorAesGcm cifrador(String clave) {
        return new CifradorAesGcm(() -> clave);
    }

    @Test
    void cifraYDescifraDevuelveElValorOriginal() {
        CifradorAesGcm cifrador = cifrador(CLAVE_VALIDA);
        String original = "mi-password-secreto-de-ovz";

        CredencialOvzCifrada cifrada = cifrador.cifrar(original);

        assertThat(cifrada.valor()).isNotNull().isNotEqualTo(original);
        assertThat(cifrador.descifrar(cifrada.valor())).isEqualTo(original);
    }

    @Test
    void cadaCifradoEsDistintoPorElIvAleatorio() {
        CifradorAesGcm cifrador = cifrador(CLAVE_VALIDA);

        assertThat(cifrador.cifrar("mismo-valor").valor())
                .isNotEqualTo(cifrador.cifrar("mismo-valor").valor());
    }

    @Test
    void descifrarNullDevuelveNull() {
        assertThat(cifrador(CLAVE_VALIDA).descifrar(null)).isNull();
    }

    @Test
    void cifrarNullLanza() {
        assertThatThrownBy(() -> cifrador(CLAVE_VALIDA).cifrar(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void claveAusenteLanzaAlCifrarDescifrarYValidar() {
        CifradorAesGcm cifrador = cifrador("   ");

        assertThatThrownBy(() -> cifrador.cifrar("x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cifrador.descifrar("x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(cifrador::verificarClaveAlArrancar).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void descifrarUnValorCorruptoLanzaSinElValorEnElMensaje() {
        CifradorAesGcm cifrador = cifrador(CLAVE_VALIDA);
        String corrupto = Base64.getEncoder().encodeToString(
                "texto-que-no-descifra-con-ninguna-clave".getBytes());

        assertThatThrownBy(() -> cifrador.descifrar(corrupto))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(corrupto));
    }

    @Test
    void verificarClaveAlArrancarConClaveValidaNoLanza() {
        assertThatCode(() -> cifrador(CLAVE_VALIDA).verificarClaveAlArrancar())
                .doesNotThrowAnyException();
    }
}
