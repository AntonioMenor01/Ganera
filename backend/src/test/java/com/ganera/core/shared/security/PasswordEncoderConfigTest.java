package com.ganera.core.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El {@link PasswordEncoder} vive en {@link PasswordEncoderConfig} (no en SecurityConfig, que es web y se excluye del
 * WORKER de OVZ). Este test fija que el encoder sigue siendo el mismo BCrypt de siempre: un hash YA GUARDADO (generado
 * con el encoder actual y escrito aqui como constante) tiene que seguir validando. Asi, si alguien cambiara el encoder
 * al mover el bean, las contrasenas existentes dejarian de validar y este test se pondria en rojo.
 */
class PasswordEncoderConfigTest {

    /** Hash BCrypt de {@link #CLAVE} generado con el encoder actual (PasswordEncoderConfig#passwordEncoder). */
    private static final String HASH_GUARDADO = "$2a$10$SUNAaJScIwQYSZPVBBAFYu9ou4KZXkLRXJX73f8LxUpOcCZsLLtju";
    private static final String CLAVE = "clave-de-prueba-del-encoder";

    @Test
    void unHashGuardadoSigueValidandoConElEncoder() {
        PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();
        assertThat(encoder.matches(CLAVE, HASH_GUARDADO))
                .as("un hash ya guardado debe seguir validando con el encoder actual").isTrue();
        assertThat(encoder.matches("una-clave-distinta", HASH_GUARDADO))
                .as("una clave equivocada no valida").isFalse();
    }
}
