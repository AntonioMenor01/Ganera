package com.ganera.core.shared.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
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
 * Cifra/descifra de forma transparente un campo String con AES-256-GCM.
 * IV aleatorio de 12 bytes por valor, prefijado al texto cifrado antes de codificar en base64.
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final EncryptionKeyHolder keyHolder;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptedStringConverter(@Value("${ganera.encryption.key:}") String encryptionKey) {
        this.keyHolder = new EncryptionKeyHolder(() -> encryptionKey);
    }

    EncryptedStringConverter(Supplier<String> keySource) {
        this.keyHolder = new EncryptionKeyHolder(keySource);
    }

    @jakarta.annotation.PostConstruct
    void verificarClaveAlArrancar() {
        keyHolder.requireKey();
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            Key key = keyHolder.requireKey();
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] ivAndCipherText = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, ivAndCipherText, 0, iv.length);
            System.arraycopy(cipherText, 0, ivAndCipherText, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(ivAndCipherText);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar el valor", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            Key key = keyHolder.requireKey();
            byte[] ivAndCipherText = Base64.getDecoder().decode(dbData);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(ivAndCipherText, 0, iv, 0, IV_LENGTH_BYTES);
            byte[] cipherText = new byte[ivAndCipherText.length - IV_LENGTH_BYTES];
            System.arraycopy(ivAndCipherText, IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainText = cipher.doFinal(cipherText);

            return new String(plainText, StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar el valor", e);
        }
    }
}
