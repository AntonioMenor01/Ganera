package com.ganera.core.postgres;

import com.ganera.core.GaneraApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

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
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
        URI uri = URI.create(PostgresEmbebido.jdbcUrl(baseDeDatos).substring("jdbc:".length()));
        List<String> argumentos = new ArrayList<>(List.of(
                "--spring.profiles.active=clever",
                "--POSTGRESQL_ADDON_HOST=" + uri.getHost(),
                "--POSTGRESQL_ADDON_PORT=" + uri.getPort(),
                "--POSTGRESQL_ADDON_DB=" + baseDeDatos,
                "--POSTGRESQL_ADDON_USER=" + PostgresEmbebido.USUARIO,
                "--POSTGRESQL_ADDON_PASSWORD=",
                "--server.port=0",
                "--spring.datasource.hikari.pool-name=pg-cors-" + baseDeDatos,
                "--spring.jmx.enabled=false"));
        argumentos.addAll(List.of(extra));
        return new SpringApplicationBuilder(GaneraApplication.class).run(argumentos.toArray(String[]::new));
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
