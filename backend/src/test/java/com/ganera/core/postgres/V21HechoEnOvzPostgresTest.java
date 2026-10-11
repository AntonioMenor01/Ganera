package com.ganera.core.postgres;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V21 («hecho en OVZ») sobre PostgreSQL 16 real. Mismo patron que V21HechoEnOvzMigracionTest (H2):
 * migrar hasta la 20, insertar tramites en varios estados, migrar al final; las filas previas quedan
 * sin marca y el CHECK ck_tramite_hecho_en_ovz rechaza las combinaciones incoherentes con el SQLState
 * de violacion de CHECK de PostgreSQL (23514), no con cualquier fallo.
 */
class V21HechoEnOvzPostgresTest {

    private static final String CHECK_VIOLADO_POSTGRES = "23514";

    @Test
    void lasFilasPreviasQuedanSinMarcaYElCheckRechazaLoIncoherente() throws SQLException {
        DataSource dataSource = PostgresEmbebido.dataSource(PostgresEmbebido.nuevaBaseDeDatos());
        flyway(dataSource).target("20").load().migrate();
        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            st.executeUpdate("INSERT INTO gestoria (id, nombre) VALUES (1, 'Gestoría Uno')");
            st.executeUpdate("INSERT INTO usuario (id, gestoria_id, email, password_hash, nombre) "
                    + "VALUES (1, 1, 'uno@test.com', 'x', 'Usuario Uno')");
            st.executeUpdate("INSERT INTO contacto (id, gestoria_id, telefono, nombre) "
                    + "VALUES (1, 1, '+34600000001', 'Contacto Uno')");
            st.executeUpdate("INSERT INTO tramite (id, gestoria_id, contacto_id, estado) VALUES "
                    + "(1, 1, 1, 'PENDIENTE_EXTRACCION'),"
                    + "(2, 1, 1, 'PENDIENTE_REVISION'),"
                    + "(3, 1, 1, 'APROBADO'),"
                    + "(4, 1, 1, 'RECHAZADO'),"
                    + "(5, 1, 1, 'EN_PROCESO'),"
                    + "(6, 1, 1, 'EJECUTADO_OVZ'),"
                    + "(7, 1, 1, 'ERROR_OVZ')");
        }

        flyway(dataSource).load().migrate();

        List<String> filas = new ArrayList<>();
        try (Connection conexion = dataSource.getConnection();
             Statement st = conexion.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, estado, hecho_en_ovz_por, hecho_en_ovz_en FROM tramite ORDER BY id")) {
            while (rs.next()) {
                filas.add(rs.getLong(1) + "|" + rs.getString(2) + "|" + rs.getObject(3) + "|" + rs.getObject(4));
            }
        }
        assertThat(filas).containsExactly(
                "1|PENDIENTE_EXTRACCION|null|null",
                "2|PENDIENTE_REVISION|null|null",
                "3|APROBADO|null|null",
                "4|RECHAZADO|null|null",
                "5|EN_PROCESO|null|null",
                "6|EJECUTADO_OVZ|null|null",
                "7|ERROR_OVZ|null|null");

        // En PostgreSQL un error aborta la transaccion: autocommit (por defecto) aisla cada sentencia.
        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            for (String sql : sentenciasIncoherentes()) {
                assertThatThrownBy(() -> st.executeUpdate(sql))
                        .as(sql)
                        .isInstanceOfSatisfying(SQLException.class, e -> {
                            assertThat(e.getSQLState()).isEqualTo(CHECK_VIOLADO_POSTGRES);
                            assertThat(e.getMessage()).contains("ck_tramite_hecho_en_ovz");
                        });
            }
            assertThatThrownBy(() -> st.executeUpdate("UPDATE tramite SET estado = 'HECHO_EN_OVZ', "
                    + "hecho_en_ovz_por = 999, hecho_en_ovz_en = TIMESTAMP '2026-10-07 10:30:00' WHERE id = 3"))
                    // 23503 = violacion de clave ajena.
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23503"));

            assertThat(st.executeUpdate("UPDATE tramite SET estado = 'HECHO_EN_OVZ', hecho_en_ovz_por = 1, "
                    + "hecho_en_ovz_en = TIMESTAMP '2026-10-07 10:30:00' WHERE id = 3")).isEqualTo(1);
            assertThat(st.executeUpdate("UPDATE tramite SET estado = 'APROBADO', hecho_en_ovz_por = NULL, "
                    + "hecho_en_ovz_en = NULL WHERE id = 3")).isEqualTo(1);
        }
    }

    /** Mismas combinaciones incoherentes que el test de H2. */
    private static List<String> sentenciasIncoherentes() {
        return List.of(
                "UPDATE tramite SET estado = 'HECHO_EN_OVZ' WHERE id = 3",
                "UPDATE tramite SET estado = 'HECHO_EN_OVZ', hecho_en_ovz_en = TIMESTAMP '2026-10-07 10:30:00' "
                        + "WHERE id = 3",
                "UPDATE tramite SET estado = 'HECHO_EN_OVZ', hecho_en_ovz_por = 1 WHERE id = 3",
                "UPDATE tramite SET hecho_en_ovz_por = 1, hecho_en_ovz_en = TIMESTAMP '2026-10-07 10:30:00' "
                        + "WHERE id = 3",
                "UPDATE tramite SET hecho_en_ovz_por = 1 WHERE id = 2",
                "UPDATE tramite SET hecho_en_ovz_en = TIMESTAMP '2026-10-07 10:30:00' WHERE id = 4",
                "INSERT INTO tramite (id, gestoria_id, contacto_id, estado, hecho_en_ovz_por, hecho_en_ovz_en) "
                        + "VALUES (31, 1, 1, 'APROBADO', 1, TIMESTAMP '2026-10-07 10:30:00')");
    }

    private static FluentConfiguration flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
    }
}
