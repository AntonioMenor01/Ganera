package com.ganera.core.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Despliegue en Clever, T3: CORS solo admite el origen exacto del frontend ({@code ganera.frontend.origen}; en los
 * tests, {@code http://localhost:5173}). Se prueba sobre HTTP real (java.net.http manda la cabecera Origin tal
 * cual, como un navegador; curl/Postman no la mandan solos). El perfil {@code clever}, en
 * {@code CorsPerfilCleverPostgresTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsEndToEndTest {

    private static final String FRONTEND = "http://localhost:5173";

    @LocalServerPort
    private int puerto;

    private final HttpClient cliente = HttpClient.newHttpClient();

    @Test
    void elPreflightDelFrontendSeAdmiteConSuOrigenExactoYSinCredenciales() throws Exception {
        HttpResponse<String> respuesta = preflight(FRONTEND);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().allValues("Access-Control-Allow-Origin")).containsExactly(FRONTEND);
        assertThat(respuesta.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
        assertThat(respuesta.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase())
                .contains("authorization");
    }

    @Test
    void elPreflightDeCualquierOtroOrigenSeRechazaSinCabeceraCors() throws Exception {
        for (String ajeno : new String[] {
                "https://ajeno.example.com", "http://localhost:5174", "https://localhost:5173",
                "http://localhost:5173.ajeno.example.com", "null"}) {
            HttpResponse<String> respuesta = preflight(ajeno);
            assertThat(respuesta.statusCode()).as(ajeno).isEqualTo(403);
            assertThat(respuesta.headers().firstValue("Access-Control-Allow-Origin")).as(ajeno).isEmpty();
        }
    }

    @Test
    void unaPeticionNormalDesdeOtroOrigenSeRechazaSinCabeceraCors() throws Exception {
        HttpResponse<String> respuesta = cliente.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/auth/me"))
                        .header("Origin", "https://ajeno.example.com")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(respuesta.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    private HttpResponse<String> preflight(String origen) throws Exception {
        return cliente.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/tramites"))
                        .header("Origin", origen)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization")
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
