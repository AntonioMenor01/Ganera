package com.ganera.core.postgres;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.core.BaseConnection;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

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
 * Prompt C, T2, y migracion a Supabase, T3: la aplicacion completa arranca con el perfil {@code clever} contra el
 * PostgreSQL embebido como contra Supabase: SSL de verdad con {@code sslmode=verify-full} y la CA del classpath (la de
 * prueba tapa a la real en los tests), la app como {@code ganera_app} en el esquema {@code ganera} y Flyway como
 * {@code ganera_propietario} por una conexion aparte. Las siete variables {@code SUPABASE_*} se pasan como argumentos
 * de linea de comandos ({@link PostgresEmbebido#variablesSupabase}); ninguna propiedad {@code spring.datasource.*} ni
 * {@code spring.flyway.*}: salen de application-clever.yml.
 */
class PerfilCleverPostgresTest {

    @Test
    void arrancaContraPostgresComoEnSupabaseYDejaElRegistroCerrado() throws Exception {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();

        try (ConfigurableApplicationContext contexto = PostgresEmbebido.arrancarPerfilClever(baseDeDatos)) {

            assertThat(contexto.getEnvironment().getActiveProfiles()).containsExactly("clever");

            // El pool conecta a ESA base con la URL exacta del perfil, y es de 5.
            HikariDataSource dataSource = (HikariDataSource) contexto.getBean(DataSource.class);
            String urlEsperada = "jdbc:postgresql://localhost:" + PostgresEmbebido.puerto() + "/" + baseDeDatos
                    + "?sslmode=verify-full&sslfactory=org.postgresql.ssl.SingleCertValidatingFactory"
                    + "&sslfactoryarg=classpath:supabase/ca-raiz.crt&currentSchema=ganera&logServerErrorDetail=false";
            assertThat(dataSource.getJdbcUrl()).isEqualTo(urlEsperada);
            assertThat(dataSource.getUsername()).isEqualTo(PostgresEmbebido.APP);
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5);

            JdbcTemplate jdbc = contexto.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo(baseDeDatos);
            assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL 17.");
            assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo(PostgresEmbebido.APP);
            assertThat(jdbc.queryForObject("select current_schema()", String.class)).isEqualTo(PostgresEmbebido.ESQUEMA);
            // La conexion de la app va por SSL de verdad (no solo esta en la URL).
            assertThat(jdbc.queryForObject("select ssl from pg_stat_ssl where pid = pg_backend_pid()", Boolean.class))
                    .isTrue();

            // El driver de verdad ha aplicado logServerErrorDetail=false (no solo esta en el texto).
            try (Connection conexion = dataSource.getConnection()) {
                assertThat(conexion.unwrap(BaseConnection.class).getLogServerErrorDetail()).isFalse();
            }

            // Flyway: conexion aparte, sin pool, derivada del datasource (misma URL) y como el propietario.
            DataSource deFlyway = contexto.getBean(Flyway.class).getConfiguration().getDataSource();
            assertThat(deFlyway).isInstanceOf(SimpleDriverDataSource.class);
            assertThat(((SimpleDriverDataSource) deFlyway).getUrl()).isEqualTo(urlEsperada);
            assertThat(((SimpleDriverDataSource) deFlyway).getUsername()).isEqualTo(PostgresEmbebido.PROPIETARIO);

            // V1-V23 aplicadas en ganera, todas por ganera_propietario. La app no puede leer flyway_schema_history
            // (el callback se lo quita): se mira como superusuario.
            JdbcTemplate admin = new JdbcTemplate(PostgresEmbebido.dataSource(baseDeDatos));
            List<String> versiones = admin.queryForList("select version from ganera.flyway_schema_history "
                    + "where success order by installed_rank", String.class);
            assertThat(versiones).containsExactlyElementsOf(
                    IntStream.rangeClosed(1, 23).mapToObj(String::valueOf).toList());
            assertThat(admin.queryForList("select distinct installed_by from ganera.flyway_schema_history",
                    String.class)).containsExactly(PostgresEmbebido.PROPIETARIO);
            assertThat(admin.queryForObject("select count(*) from ganera.flyway_schema_history", Long.class))
                    .isEqualTo(23);

            // Las tablas de negocio son del propietario (ninguna de la app).
            List<String> duenos = admin.queryForList("select distinct tableowner from pg_tables "
                    + "where schemaname = 'ganera'", String.class);
            assertThat(duenos).containsExactly(PostgresEmbebido.PROPIETARIO);
            assertThat(admin.queryForObject("select count(*) from pg_tables where schemaname = 'ganera'", Long.class))
                    .isGreaterThan(10);
            // Y nada en public.
            assertThat(admin.queryForObject("select count(*) from pg_tables where schemaname = 'public'", Long.class))
                    .isZero();

            // La guardia del smoke (script 02) da 0 filas.
            assertThat(SeguridadEsquemaPostgresTest.problemas(baseDeDatos)).isEmpty();

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
