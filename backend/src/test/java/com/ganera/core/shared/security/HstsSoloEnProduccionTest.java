package com.ganera.core.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Despliegue en Clever, T2: fuera del perfil {@code clever} (local, tests, la demo del portatil por HTTP) la API
 * NO manda {@code Strict-Transport-Security} en una peticion HTTP, aunque traiga {@code X-Forwarded-Proto: https}
 * (en local nadie la pone de verdad y un navegador no debe quedarse con HSTS para localhost). En produccion, ver
 * {@code HstsPerfilCleverPostgresTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HstsSoloEnProduccionTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void sinElPerfilCleverNoSeMandaHsts() {
        ResponseEntity<String> sinCabecera = rest.getForEntity("/auth/me", String.class);
        assertThat(sinCabecera.getStatusCode().value()).isEqualTo(401);
        assertThat(sinCabecera.getHeaders().get("Strict-Transport-Security")).isNull();

        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.add("X-Forwarded-Proto", "https");
        ResponseEntity<String> conCabecera = rest.exchange(
                "/auth/me", HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);
        assertThat(conCabecera.getStatusCode().value()).isEqualTo(401);
        assertThat(conCabecera.getHeaders().get("Strict-Transport-Security")).isNull();
    }
}
