package com.ganera.core.postgres;

import com.ganera.core.GaneraApplication;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.postgresql.core.BaseConnection;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt C, T2: la aplicacion completa arranca con el perfil {@code clever} (el de la demo en Clever
 * Cloud) contra el PostgreSQL embebido de T1. Las variables que Clever inyecta al enlazar el add-on
 * PostgreSQL ({@code POSTGRESQL_ADDON_*}) se pasan como argumentos de linea de comandos que apuntan
 * a esa base de datos, no como variables de entorno reales. No se pasa ninguna propiedad
 * {@code spring.datasource.*}: la URL, el usuario y la contrasena salen de application-clever.yml.
 * El contexto es propio de este test (SpringApplicationBuilder), asi que el perfil no se activa en
 * el resto de la suite, que sigue en H2.
 */
class PerfilCleverPostgresTest {

    @Test
    void arrancaContraPostgresConLasVariablesDelAddOnYDejaElRegistroCerrado() throws Exception {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
        // jdbc:postgresql://localhost:<puerto>/<base>?user=postgres
        URI uri = URI.create(PostgresEmbebido.jdbcUrl(baseDeDatos).substring("jdbc:".length()));

        try (ConfigurableApplicationContext contexto = new SpringApplicationBuilder(GaneraApplication.class).run(
                "--spring.profiles.active=clever",
                "--POSTGRESQL_ADDON_HOST=" + uri.getHost(),
                "--POSTGRESQL_ADDON_PORT=" + uri.getPort(),
                "--POSTGRESQL_ADDON_DB=" + baseDeDatos,
                "--POSTGRESQL_ADDON_USER=" + PostgresEmbebido.USUARIO,
                "--POSTGRESQL_ADDON_PASSWORD=",
                "--server.port=0",
                "--spring.datasource.hikari.pool-name=pg-clever-" + baseDeDatos,
                "--spring.jmx.enabled=false")) {

            assertThat(contexto.getEnvironment().getActiveProfiles()).containsExactly("clever");

            // Conecta a ESA base de datos de PostgreSQL, con la URL del perfil.
            HikariDataSource dataSource = (HikariDataSource) contexto.getBean(DataSource.class);
            assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:postgresql://" + uri.getHost() + ":" + uri.getPort()
                    + "/" + baseDeDatos + "?logServerErrorDetail=false");
            JdbcTemplate jdbc = contexto.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo(baseDeDatos);
            assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL 16.");

            // El driver de verdad ha aplicado logServerErrorDetail=false (no solo esta en el texto).
            try (Connection conexion = dataSource.getConnection()) {
                assertThat(conexion.unwrap(BaseConnection.class).getLogServerErrorDetail()).isFalse();
            }

            // Flyway ha aplicado V1-V20 al arrancar.
            List<String> versiones = jdbc.queryForList(
                    "select version from flyway_schema_history where success order by installed_rank", String.class);
            assertThat(versiones).containsExactlyElementsOf(
                    IntStream.rangeClosed(1, 20).mapToObj(String::valueOf).toList());

            // Registro publico cerrado: 404 sin cuerpo y nada creado.
            int puerto = Integer.parseInt(contexto.getEnvironment().getProperty("local.server.port"));
            HttpResponse<String> respuesta = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/gestorias/registro"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                    {"nombreGestoria":"Gestoria Clever","nombreUsuario":"Empleado",
                                     "email":"clever@gestoria.com","password":"password123","rangoClientes":"UNO_A_DIEZ"}
                                    """))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(respuesta.statusCode()).isEqualTo(404);
            assertThat(respuesta.body()).isEmpty();
            assertThat(jdbc.queryForObject("select count(*) from gestoria", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from usuario", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from suscripcion", Long.class)).isZero();
        }
    }
}
