package com.ganera.core.migracion;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V21 («hecho en OVZ») sobre una H2 en memoria PROPIA (MODE=PostgreSQL), mismo patron que
 * V20TiposTramiteOvzMigracionTest: migrar hasta la 20, insertar tramites en varios estados, migrar al
 * final y comprobar que las filas previas quedan con las dos columnas a null y que el CHECK
 * ck_tramite_hecho_en_ovz rechaza las combinaciones incoherentes.
 */
class V21HechoEnOvzMigracionTest {

    /** SQLState de H2 para la violacion de un CHECK (ErrorCode.CHECK_CONSTRAINT_VIOLATED_1). En
     * PostgreSQL es 23514 (ver V21HechoEnOvzPostgresTest). */
    private static final String CHECK_VIOLADO_H2 = "23513";

    @Test
    void lasFilasPreviasQuedanSinMarcaEnTodosLosEstados() throws SQLException {
        DataSource dataSource = nuevaBaseDeDatos();
        flyway(dataSource).target("20").load().migrate();
        insertarDatosPrevios(dataSource);

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
        // Un arranque posterior: validate no falla y migrate no vuelve a ejecutar la V21.
        flyway(dataSource).load().validate();
        assertThat(flyway(dataSource).load().migrate().migrationsExecuted).isZero();
    }

    @Test
    void elCheckSoloAceptaHechoEnOvzConUsuarioYFechaYNingunOtroEstadoConEllos() throws SQLException {
        DataSource dataSource = nuevaBaseDeDatos();
        flyway(dataSource).target("20").load().migrate();
        insertarDatosPrevios(dataSource);
        flyway(dataSource).load().migrate();

        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            for (String sql : sentenciasIncoherentes()) {
                assertThatThrownBy(() -> st.executeUpdate(sql))
                        .as(sql)
                        .isInstanceOfSatisfying(SQLException.class, e -> {
                            assertThat(e.getSQLState()).isEqualTo(CHECK_VIOLADO_H2);
                            assertThat(e.getMessage()).containsIgnoringCase("ck_tramite_hecho_en_ovz");
                        });
            }
            // Coherentes: marcar con los dos y desmarcar vaciando los dos.
            assertThat(st.executeUpdate("UPDATE tramite SET estado = 'HECHO_EN_OVZ', hecho_en_ovz_por = 1, "
                    + "hecho_en_ovz_en = TIMESTAMP '2026-10-07 10:30:00' WHERE id = 3")).isEqualTo(1);
            assertThat(st.executeUpdate("INSERT INTO tramite (id, gestoria_id, contacto_id, estado, hecho_en_ovz_por, "
                    + "hecho_en_ovz_en) VALUES (30, 1, 1, 'HECHO_EN_OVZ', 1, TIMESTAMP '2026-10-07 10:31:00')"))
                    .isEqualTo(1);
            assertThat(st.executeUpdate("UPDATE tramite SET estado = 'APROBADO', hecho_en_ovz_por = NULL, "
                    + "hecho_en_ovz_en = NULL WHERE id = 3")).isEqualTo(1);
        }
    }

    @Test
    void hechoEnOvzPorDebeSerUnUsuarioExistente() throws SQLException {
        DataSource dataSource = nuevaBaseDeDatos();
        flyway(dataSource).target("20").load().migrate();
        insertarDatosPrevios(dataSource);
        flyway(dataSource).load().migrate();

        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            assertThatThrownBy(() -> st.executeUpdate("UPDATE tramite SET estado = 'HECHO_EN_OVZ', "
                    + "hecho_en_ovz_por = 999, hecho_en_ovz_en = TIMESTAMP '2026-10-07 10:30:00' WHERE id = 3"))
                    // 23506 = violacion de clave ajena en H2 (en PostgreSQL, 23503).
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23506"));
        }
    }

    /** Las cuatro combinaciones que el CHECK debe rechazar (las mismas que en el test de PostgreSQL). */
    private static List<String> sentenciasIncoherentes() {
        return List.of(
                // HECHO_EN_OVZ sin usuario ni fecha, sin usuario, sin fecha.
                "UPDATE tramite SET estado = 'HECHO_EN_OVZ' WHERE id = 3",
                "UPDATE tramite SET estado = 'HECHO_EN_OVZ', hecho_en_ovz_en = TIMESTAMP '2026-10-07 10:30:00' "
                        + "WHERE id = 3",
                "UPDATE tramite SET estado = 'HECHO_EN_OVZ', hecho_en_ovz_por = 1 WHERE id = 3",
                // Otro estado con usuario y fecha, o con uno de los dos.
                "UPDATE tramite SET hecho_en_ovz_por = 1, hecho_en_ovz_en = TIMESTAMP '2026-10-07 10:30:00' "
                        + "WHERE id = 3",
                "UPDATE tramite SET hecho_en_ovz_por = 1 WHERE id = 2",
                "UPDATE tramite SET hecho_en_ovz_en = TIMESTAMP '2026-10-07 10:30:00' WHERE id = 4",
                "INSERT INTO tramite (id, gestoria_id, contacto_id, estado, hecho_en_ovz_por, hecho_en_ovz_en) "
                        + "VALUES (31, 1, 1, 'APROBADO', 1, TIMESTAMP '2026-10-07 10:30:00')");
    }

    private static void insertarDatosPrevios(DataSource dataSource) throws SQLException {
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
    }

    private static DataSource nuevaBaseDeDatos() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:v21_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static FluentConfiguration flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
    }
}
