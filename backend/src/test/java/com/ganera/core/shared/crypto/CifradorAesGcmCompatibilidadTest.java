package com.ganera.core.shared.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T2 de M2: compatibilidad de formato. El ciphertext que quede en reposo (generado por el
 * {@code EncryptedStringConverter} actual) debe seguir descifrandose con {@link CifradorAesGcm}, porque el
 * formato es el MISMO: base64 de (IV de 12 bytes || AES-256-GCM con tag de 128 bits). Por tanto la columna
 * {@code ganadero.ovz_password_cifrada} no cambia de contenido ni de tipo: NO hace falta migracion Flyway.
 *
 * <p>El ciphertext de referencia se construye aqui de forma DETERMINISTA (IV fijo) con JCE puro, replicando
 * exactamente el formato del converter, para no depender del IV aleatorio. Clave y texto INVENTADOS.
 */
class CifradorAesGcmCompatibilidadTest {

    /** Base64 de 32 bytes ASCII. Falsa a proposito; nunca la del entorno. */
    private static final String CLAVE_VALIDA =
            Base64.getEncoder().encodeToString("clave-de-test-compat-32-bytes!!!".getBytes(StandardCharsets.UTF_8));

    private static final String PLANO = "password-ovz-del-formato-antiguo";

    @Test
    void descifraUnCiphertextEnElFormatoDelConverterActual() throws Exception {
        String ciphertextEnReposo = cifradoEnFormatoConverter(PLANO, CLAVE_VALIDA);

        String descifrado = new CifradorAesGcm(() -> CLAVE_VALIDA).descifrar(ciphertextEnReposo);

        assertThat(descifrado).isEqualTo(PLANO);
    }

    /**
     * Replica EXACTAMENTE el formato de {@code EncryptedStringConverter.convertToDatabaseColumn}: IV de 12
     * bytes (aqui fijo para determinismo) prefijado al texto cifrado AES/GCM/NoPadding (tag 128), todo en base64.
     */
    private static String cifradoEnFormatoConverter(String plano, String claveBase64) throws Exception {
        byte[] iv = new byte[12]; // IV fijo: el formato es identico, solo el valor del IV seria aleatorio en produccion
        for (int i = 0; i < iv.length; i++) {
            iv[i] = (byte) (i + 1);
        }
        SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(claveBase64), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] cipherText = cipher.doFinal(plano.getBytes(StandardCharsets.UTF_8));

        byte[] ivAndCipherText = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, ivAndCipherText, 0, iv.length);
        System.arraycopy(cipherText, 0, ivAndCipherText, iv.length, cipherText.length);
        return Base64.getEncoder().encodeToString(ivAndCipherText);
    }
}
