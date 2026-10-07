package com.ganera.core.postgres;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Primera vez de Flyway contra PostgreSQL real (Prompt C, T1): V1-V20 (V18 es Java) en una base de
 * datos limpia de PostgreSQL 16, y despues Hibernate arrancado encima con ddl-auto=validate.
 */
class FlywayPostgresTest {

    @Test
    void aplicaLasVeinteMigracionesEnUnPostgresLimpio() {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
        DataSource dataSource = PostgresEmbebido.dataSource(baseDeDatos);

        MigrateResult resultado = flyway(dataSource).migrate();

        assertThat(resultado.success).isTrue();
        assertThat(resultado.migrationsExecuted).isEqualTo(20);
        MigrationInfo[] aplicadas = flyway(dataSource).info().applied();
        assertThat(Arrays.stream(aplicadas).map(m -> m.getVersion().getVersion()).toList())
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 20).mapToObj(String::valueOf).toList());
        assertThat(aplicadas).allSatisfy(m -> assertThat(m.getState()).isEqualTo(MigrationState.SUCCESS));
        assertThat(aplicadas[17].getType().name()).isEqualTo("JDBC"); // V18, la migracion Java
        assertThat(new JdbcTemplate(dataSource).queryForObject("select version()", String.class))
                .startsWith("PostgreSQL 16.");
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
            assertThat(versiones).hasSize(20).last().isEqualTo("20");
        }
    }

    private static Flyway flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
    }
}
