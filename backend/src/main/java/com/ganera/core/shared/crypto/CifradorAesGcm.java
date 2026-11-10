package com.ganera.core.shared.crypto;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Supplier;

/**
 * Cifra/descifra credenciales OVZ con AES-256-GCM (IV aleatorio de 12 bytes prefijado al texto cifrado y
 * codificado en base64). Extraido de {@code EncryptedStringConverter} (M2): el ciphertext viaja EN REPOSO en la
 * entidad y solo se descifra bajo demanda (ver {@code DescifradoCredencialOvz}), nunca al materializar el Ganadero.
 *
 * <p>{@link #cifrar(String)} devuelve un {@link CredencialOvzCifrada}: el unico tipo que el setter de {@code Ganadero}
 * acepta, de forma que un texto plano no pueda llegar a la columna por descuido.
 *
 * <p>Mantiene la validacion de la clave al arrancar (antes en el converter): el bean falla rapido si
 * {@code ENCRYPTION_KEY} no esta configurada. Los mensajes de error son genericos: nunca el ciphertext ni el plano.
 */
@Component
public class CifradorAesGcm {

    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final EncryptionKeyHolder keyHolder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public CifradorAesGcm(@Value("${ganera.encryption.key:}") String encryptionKey) {
        this.keyHolder = new EncryptionKeyHolder(() -> encryptionKey);
    }

    CifradorAesGcm(Supplier<String> keySource) {
        this.keyHolder = new EncryptionKeyHolder(keySource);
    }

    @PostConstruct
    public void verificarClaveAlArrancar() {
        keyHolder.requireKey();
    }

    public CredencialOvzCifrada cifrar(String plano) {
        if (plano == null) {
            throw new IllegalArgumentException("No se puede cifrar una credencial nula");
        }
        try {
            Key key = keyHolder.requireKey();
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plano.getBytes(StandardCharsets.UTF_8));

            byte[] ivAndCipherText = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, ivAndCipherText, 0, iv.length);
            System.arraycopy(cipherText, 0, ivAndCipherText, iv.length, cipherText.length);

            return new CredencialOvzCifradaImpl(Base64.getEncoder().encodeToString(ivAndCipherText));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar la credencial", e);
        }
    }

    public String descifrar(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        try {
            Key key = keyHolder.requireKey();
            byte[] ivAndCipherText = Base64.getDecoder().decode(ciphertext);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(ivAndCipherText, 0, iv, 0, IV_LENGTH_BYTES);
            byte[] cipherText = new byte[ivAndCipherText.length - IV_LENGTH_BYTES];
            System.arraycopy(ivAndCipherText, IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // Mensaje generico: nunca el ciphertext ni el plano.
            throw new IllegalStateException("No se pudo descifrar la credencial", e);
        }
    }
}
