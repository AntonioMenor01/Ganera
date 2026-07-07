package com.ganera.core.shared.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-jwt-secret-de-al-menos-32-bytes-de-longitud";

    @Test
    void generaYParseaUnTokenValidoConservandoLosClaims() {
        JwtService jwtService = new JwtService(SECRET, 480);
        GaneraUserPrincipal principal = new GaneraUserPrincipal(1L, 2L, "empleado@gestoria.com");

        String token = jwtService.generarToken(principal);
        GaneraUserPrincipal parseado = jwtService.parsearToken(token);

        assertThat(parseado.usuarioId()).isEqualTo(1L);
        assertThat(parseado.gestoriaId()).isEqualTo(2L);
        assertThat(parseado.email()).isEqualTo("empleado@gestoria.com");
    }

    @Test
    void unTokenManipuladoSeRechaza() {
        JwtService jwtService = new JwtService(SECRET, 480);
        String token = jwtService.generarToken(new GaneraUserPrincipal(1L, 2L, "a@b.com"));
        String tokenManipulado = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.parsearToken(tokenManipulado)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void unTokenFirmadoConOtroSecretSeRechaza() {
        JwtService emisor = new JwtService(SECRET, 480);
        JwtService verificador = new JwtService("otro-secreto-completamente-distinto-de-32-bytes", 480);

        String token = emisor.generarToken(new GaneraUserPrincipal(1L, 2L, "a@b.com"));

        assertThatThrownBy(() -> verificador.parsearToken(token)).isInstanceOf(RuntimeException.class);
    }
}
