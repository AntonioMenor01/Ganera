package com.ganera.core.ganadero;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganera.core.shared.crypto.CifradorAesGcm;
import com.ganera.core.shared.crypto.CredencialOvzCifrada;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T10 de M2 (exposicion): el ciphertext de la contrasena OVZ no debe salir en la serializacion JSON de un
 * {@code Ganadero} (el getter Lombok lo serializaria si no se excluyera con {@code @JsonIgnore}), y como el
 * entity tampoco tiene {@code @ToString}/{@code @EqualsAndHashCode}, su {@code toString} es el de Object y no
 * lleva campos. Los DTOs ya estan cubiertos por los E2E que comprueban que las respuestas no contienen
 * "ovzPassword"/"ovzUsuario" ni los valores (CredencialesOvzDetalleEndToEndTest, GanaderoEndToEndTest,
 * TramiteRevisionEndToEndTest).
 *
 * <p>Clave y contrasena INVENTADAS solo para el test.
 */
class GanaderoSinExposicionCredencialTest {

    private static final String CLAVE_VALIDA =
            Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes());
    private static final String PLANO = "password-ovz-que-no-debe-salir";

    @Test
    void elJsonDeGanaderoNoIncluyeElCiphertextNiLaContrasena() throws Exception {
        CredencialOvzCifrada cifrada = new CifradorAesGcm(CLAVE_VALIDA).cifrar(PLANO);
        Ganadero ganadero = new Ganadero();
        ganadero.setNombre("Titular sin exposicion");
        ganadero.setNif("66666666L");
        ganadero.setOvzUsuario("usuario-ovz");
        ganadero.setOvzPasswordCifrada(cifrada);

        // findAndRegisterModules: como la ObjectMapper de Spring (maneja Instant de createdAt). Lo que se prueba
        // es que ovzPasswordCifrada queda fuera por @JsonIgnore, no la serializacion de las fechas.
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(ganadero);

        assertThat(json).doesNotContain("ovzPasswordCifrada");
        assertThat(json).doesNotContain(cifrada.valor());
        assertThat(json).doesNotContain(PLANO);
    }

    @Test
    void elToStringDeGanaderoNoLlevaCamposSensibles() {
        CredencialOvzCifrada cifrada = new CifradorAesGcm(CLAVE_VALIDA).cifrar(PLANO);
        Ganadero ganadero = new Ganadero();
        ganadero.setOvzPasswordCifrada(cifrada);

        String texto = ganadero.toString();

        assertThat(texto).doesNotContain(cifrada.valor());
        assertThat(texto).doesNotContain(PLANO);
        assertThat(texto).doesNotContain("ovzPassword");
    }
}
