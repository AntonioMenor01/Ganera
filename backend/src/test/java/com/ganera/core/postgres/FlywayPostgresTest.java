package com.ganera.core.postgres;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import com.ganera.core.migracion.ConfiguracionFlywayTest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Primera vez de Flyway contra PostgreSQL real (Prompt C, T1): V1-V23 (V18 es Java) en una base de
 * datos limpia de PostgreSQL 17, y despues Hibernate arrancado encima con ddl-auto=validate.
 */
class FlywayPostgresTest {

    @Test
    void aplicaLasVeintitresMigracionesEnUnPostgresLimpio() {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
        DataSource dataSource = PostgresEmbebido.dataSource(baseDeDatos);

        MigrateResult resultado = flyway(dataSource).migrate();

        assertThat(resultado.success).isTrue();
        assertThat(resultado.migrationsExecuted).isEqualTo(23);
        MigrationInfo[] aplicadas = flyway(dataSource).info().applied();
        assertThat(Arrays.stream(aplicadas).map(m -> m.getVersion().getVersion()).toList())
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 23).mapToObj(String::valueOf).toList());
        assertThat(aplicadas).allSatisfy(m -> assertThat(m.getState()).isEqualTo(MigrationState.SUCCESS));
        assertThat(aplicadas[17].getType().name()).isEqualTo("JDBC"); // V18, la migracion Java
        assertThat(new JdbcTemplate(dataSource).queryForObject("select version()", String.class))
                .startsWith("PostgreSQL 17.");
        assertThat(new JdbcTemplate(dataSource).queryForObject("show server_encoding", String.class))
                .isEqualTo("UTF8");
    }

    /**
     * La aplicacion entera arranca contra PostgreSQL con ddl-auto=validate: Flyway migra al
     * arrancar y Hibernate comprueba que cada entidad casa con el esquema real (tablas, columnas y
     * tipos). En H2 la suite usa ddl-auto=none, asi que esto no lo habia comprobado nadie.
     */
    @Test
    void hibernateValidaElEsquemaMigradoEnPostgres() {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();

        try (ConfigurableApplicationContext contexto = PostgresEmbebido.arrancarAplicacion(baseDeDatos, "validate")) {
            JdbcTemplate jdbc = contexto.getBean(JdbcTemplate.class);
            List<String> versiones = jdbc.queryForList(
                    "select version from flyway_schema_history where success order by installed_rank", String.class);
            assertThat(versiones).hasSize(23).last().isEqualTo("23");
        }
    }

    /**
     * Migracion a Supabase, T1 (caracterizacion): Flyway 10.20.1 admite PostgreSQL 17 sin avisar de version no
     * probada, y las ubicaciones db/migration + db/postgresql (con su LEEME.md) no dejan ningun aviso. Si una subida
     * de PostgreSQL o de Flyway lo cambia, este test lo dice.
     */
    @Test
    void migrarEnPostgres17NoDejaAvisosDeFlyway() {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
        Logger logFlyway = (Logger) LoggerFactory.getLogger("org.flywaydb");
        ListAppender<ILoggingEvent> eventos = new ListAppender<>();
        eventos.start();
        logFlyway.addAppender(eventos);
        try {
            // Flyway 10.20.1 solo registra en DEBUG una ubicacion inexistente: con failOnMissingLocations, una
            // carpeta db/postgresql que no llegue al classpath hace fallar el test.
            assertThat(PostgresEmbebido.flyway(PostgresEmbebido.dataSource(baseDeDatos)).failOnMissingLocations(true)
                    .load().migrate().migrationsExecuted).isEqualTo(23);
        } finally {
            logFlyway.detachAppender(eventos);
            eventos.stop();
        }

        // Comprobacion de que de verdad se ha capturado el log de Flyway; si no, la siguiente no probaria nada.
        assertThat(eventos.list).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(mensaje -> mensaje.contains("Successfully applied 23 migrations"));
        assertThat(eventos.list)
                .filteredOn(evento -> evento.getLevel().isGreaterOrEqual(Level.WARN))
                .extracting(ILoggingEvent::getFormattedMessage)
                .isEmpty();
    }

    /**
     * Migracion a Supabase, T2 (menor 1 de la revision de T1): el Flyway programatico de los tests de PostgreSQL usa
     * las mismas ubicaciones que los application.yml con vendor = postgresql.
     */
    @Test
    void elFlywayDeLosTestsUsaLasUbicacionesDeLosApplicationYml() throws Exception {
        for (String fichero : List.of("src/main/resources/application.yml", "src/test/resources/application.yml")) {
            List<String> delYaml = ConfiguracionFlywayTest.ubicaciones(fichero).stream()
                    .map(u -> u.replace("{vendor}", "postgresql")).toList();
            assertThat(Arrays.stream(PostgresEmbebido.flyway(null).getLocations()).map(Object::toString).toList())
                    .as(fichero).isEqualTo(delYaml);
        }
    }

    private static Flyway flyway(DataSource dataSource) {
        return PostgresEmbebido.flyway(dataSource).load();
    }
}
