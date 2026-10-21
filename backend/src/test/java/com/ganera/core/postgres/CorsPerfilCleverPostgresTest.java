package com.ganera.core.postgres;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Despliegue en Clever, T3: en produccion (perfil {@code clever}) CORS solo admite {@code FRONTEND_ORIGEN}, que es
 * obligatorio y tiene que ser un origen exacto {@code https}. Sin ella, o con un valor que no lo sea, la app NO
 * arranca (fail-closed): nunca se queda con el {@code http://localhost:5173} por defecto de desarrollo.
 */
class CorsPerfilCleverPostgresTest {

    private static final String FRONTEND = "https://app.ganera.es";

    @Test
    void soloSeAdmiteElOrigenConfigurado() throws Exception {
        try (ConfigurableApplicationContext contexto = arrancar("--FRONTEND_ORIGEN=" + FRONTEND)) {
            String base = "http://localhost:" + contexto.getEnvironment().getProperty("local.server.port");
            HttpClient cliente = HttpClient.newHttpClient();

            HttpResponse<String> admitido = preflight(cliente, base, FRONTEND);
            assertThat(admitido.statusCode()).isEqualTo(200);
            assertThat(admitido.headers().allValues("Access-Control-Allow-Origin")).containsExactly(FRONTEND);

            for (String ajeno : new String[] {
                    "http://app.ganera.es", "https://app.ganera.es.ajeno.example.com", "https://ganera.es",
                    "https://otra.ganera.es", "http://localhost:5173"}) {
                HttpResponse<String> rechazado = preflight(cliente, base, ajeno);
                assertThat(rechazado.statusCode()).as(ajeno).isEqualTo(403);
                assertThat(rechazado.headers().firstValue("Access-Control-Allow-Origin")).as(ajeno).isEmpty();
            }
        }
    }

    @Test
    void sinFrontendOrigenLaAppNoArranca() {
        assertThatThrownBy(() -> arrancar().close())
                .hasStackTraceContaining("Could not resolve placeholder 'FRONTEND_ORIGEN'");
    }

    @Test
    void conUnOrigenHttpLaAppNoArranca() {
        assertThatThrownBy(() -> arrancar("--FRONTEND_ORIGEN=http://app.ganera.es").close())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("FRONTEND_ORIGEN (ganera.frontend.origen) no es un origen valido: "
                        + "tiene que empezar por https://. Ejemplo: https://app.ganera.es");
    }

    @Test
    void conUnComodinLaAppNoArranca() {
        assertThatThrownBy(() -> arrancar("--FRONTEND_ORIGEN=*").close())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("no es un origen valido");
    }

    private static ConfigurableApplicationContext arrancar(String... extra) {
        return PostgresEmbebido.arrancarPerfilClever(
                PostgresEmbebido.variablesSupabase(PostgresEmbebido.nuevaBaseDeDatos()), extra);
    }

    private static HttpResponse<String> preflight(HttpClient cliente, String base, String origen) throws Exception {
        return cliente.send(
                HttpRequest.newBuilder(URI.create(base + "/tramites"))
                        .header("Origin", origen)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization")
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
