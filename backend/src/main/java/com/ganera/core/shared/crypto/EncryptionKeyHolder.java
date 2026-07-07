package com.ganera.core.shared.crypto;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Base64;
import java.util.function.Supplier;

/** Deriva la SecretKey AES-256 desde la variable de entorno ganera.encryption.key en base64. */
public class EncryptionKeyHolder {

    private final Supplier<String> keySource;

    public EncryptionKeyHolder(Supplier<String> keySource) {
        this.keySource = keySource;
    }

    public Key requireKey() {
        String raw = keySource.get();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY no está configurada. La aplicación no puede arrancar sin ella.");
        }
        byte[] bytes = Base64.getDecoder().decode(raw);
        if (bytes.length != 32) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY debe decodificar a 32 bytes (AES-256), tiene " + bytes.length + ".");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
