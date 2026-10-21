package com.ganera.core.postgres;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Despliegue en Clever, T2: con el perfil {@code clever} (produccion) la API manda
 * {@code Strict-Transport-Security} en TODAS las respuestas. Clever termina TLS en su proxy y llega a la app por
 * HTTP; por defecto Spring Security solo la manda en peticiones que cree seguras, asi que detras del proxy no la
 * mandaria nunca. No depende de {@code X-Forwarded-Proto} (ni de que la IP del proxy este entre las de confianza
 * de Tomcat). Por HTTP el navegador la ignora (RFC 6797, 8.1), y Clever redirige HTTP a HTTPS ("Force HTTPS").
 */
class HstsPerfilCleverPostgresTest {

    private static final String HSTS = "max-age=31536000 ; includeSubDomains";

    @Test
    void conElPerfilCleverLaApiMandaHstsEnTodasLasRespuestas() throws Exception {
        try (ConfigurableApplicationContext contexto =
                PostgresEmbebido.arrancarPerfilClever(PostgresEmbebido.nuevaBaseDeDatos())) {

            String base = "http://localhost:" + contexto.getEnvironment().getProperty("local.server.port");
            HttpClient cliente = HttpClient.newHttpClient();

            // Ruta protegida sin JWT (401), sin X-Forwarded-Proto: como llega del proxy si no lo pusiera.
            HttpResponse<String> sinJwt = cliente.send(
                    HttpRequest.newBuilder(URI.create(base + "/auth/me")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(sinJwt.statusCode()).isEqualTo(401);
            assertThat(sinJwt.headers().allValues("Strict-Transport-Security")).containsExactly(HSTS);

            // Ruta publica (login con credenciales malas, 401) con X-Forwarded-Proto: https, como la manda Clever.
            HttpResponse<String> login = cliente.send(
                    HttpRequest.newBuilder(URI.create(base + "/auth/login"))
                            .header("Content-Type", "application/json")
                            .header("X-Forwarded-Proto", "https")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "{\"email\":\"nadie@ejemplo.invalid\",\"password\":\"incorrecta\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(login.statusCode()).isEqualTo(401);
            assertThat(login.headers().allValues("Strict-Transport-Security")).containsExactly(HSTS);
        }
    }
}
