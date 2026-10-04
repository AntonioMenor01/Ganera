package com.ganera.core.migracion;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V18 (Java, solo JDBC) sobre una H2 en memoria PROPIA (nombre unico, MODE=PostgreSQL), no la
 * de la suite: se migra hasta la 17, se insertan filas por SQL como las que habria antes de la
 * V18, se migra al final y se comprueban los valores exactos y el NOT NULL.
 */
class V18BusquedaNormalizadaMigracionTest {

    @Test
    void rellenaLasColumnasDeLasFilasExistentesYLasDejaNotNull() throws SQLException {
        DataSource dataSource = nuevaBaseDeDatos();
        flyway(dataSource).target("17").load().migrate();

        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            st.executeUpdate("INSERT INTO gestoria (id, nombre) VALUES (1, 'Gestoría Uno')");
            st.executeUpdate("INSERT INTO ganadero (id, gestoria_id, nombre, nif) VALUES "
                    + "(1, 1, 'José Martínez', '1A'),"
                    + "(2, 1, 'Peña-García, S.L.', '2B'),"
                    + "(3, 1, 'Mart´in Ñúñez', '3C')");
            st.executeUpdate("INSERT INTO explotacion (id, gestoria_id, ganadero_id, codigo_rega, nombre) VALUES "
                    + "(1, 1, 1, 'ES-12 0000 0001', 'Finca La Peña'),"
                    + "(2, 1, 2, 'ES120000000002', 'Cortijo Mart´in-Pérez'),"
                    + "(3, 1, 3, 'ES120000000003', '---')");
        }

        flyway(dataSource).load().migrate();

        assertThat(columna(dataSource, "SELECT id, nombre_busqueda FROM ganadero ORDER BY id"))
                .containsExactly(
                        Map.entry(1L, "jose martinez"),
                        Map.entry(2L, "penagarcia sl"),
                        Map.entry(3L, "martin nunez"));
        assertThat(columna(dataSource, "SELECT id, busqueda FROM explotacion ORDER BY id"))
                .containsExactly(
                        Map.entry(1L, "es12 0000 0001 finca la pena"),
                        Map.entry(2L, "es120000000002 cortijo martinperez"),
                        Map.entry(3L, "es120000000003"));

        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO ganadero (id, gestoria_id, nombre) VALUES (10, 1, 'Sin columna')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO explotacion (id, gestoria_id, ganadero_id, codigo_rega, nombre) "
                            + "VALUES (10, 1, 1, 'ES129999999999', 'Sin columna')"))
                    .isInstanceOf(SQLException.class);
        }
        assertThat(esNullable(dataSource, "GANADERO", "NOMBRE_BUSQUEDA")).isFalse();
        assertThat(esNullable(dataSource, "EXPLOTACION", "BUSQUEDA")).isFalse();
    }

    @Test
    void conLasTablasVaciasTambienMigraYUnArranquePosteriorValida() throws SQLException {
        DataSource dataSource = nuevaBaseDeDatos();
        flyway(dataSource).target("17").load().migrate();

        flyway(dataSource).load().migrate();

        assertThat(esNullable(dataSource, "GANADERO", "NOMBRE_BUSQUEDA")).isFalse();
        assertThat(esNullable(dataSource, "EXPLOTACION", "BUSQUEDA")).isFalse();
        // Un arranque posterior: validate no falla y migrate no vuelve a ejecutar la V18.
        flyway(dataSource).load().validate();
        assertThat(flyway(dataSource).load().migrate().migrationsExecuted).isZero();
    }

    /**
     * 1203 filas en cada tabla: cruzan dos cortes de lote completos (500 y 1000, el executeBatch
     * dentro del bucle y el reinicio del contador) y un lote final parcial de 203 (el executeBatch
     * de despues del bucle). Si alguno faltara, quedarian filas a null y el SET NOT NULL fallaria.
     */
    @Test
    void conMasFilasQueUnLoteRellenaTodasCruzandoVariosCortes() throws SQLException {
        int filas = 1203;
        DataSource dataSource = nuevaBaseDeDatos();
        flyway(dataSource).target("17").load().migrate();

        try (Connection conexion = dataSource.getConnection()) {
            try (Statement st = conexion.createStatement()) {
                st.executeUpdate("INSERT INTO gestoria (id, nombre) VALUES (1, 'Gestoría Lotes')");
            }
            try (PreparedStatement ganadero = conexion.prepareStatement(
                    "INSERT INTO ganadero (id, gestoria_id, nombre, nif) VALUES (?, 1, ?, ?)");
                 PreparedStatement explotacion = conexion.prepareStatement(
                         "INSERT INTO explotacion (id, gestoria_id, ganadero_id, codigo_rega, nombre) "
                                 + "VALUES (?, 1, ?, ?, ?)")) {
                for (int i = 1; i <= filas; i++) {
                    ganadero.setLong(1, i);
                    ganadero.setString(2, "Ganadero Peña " + i);
                    ganadero.setString(3, "NIF" + i);
                    ganadero.addBatch();
                    explotacion.setLong(1, i);
                    explotacion.setLong(2, i);
                    explotacion.setString(3, String.format("ES-%012d", i));
                    explotacion.setString(4, "Finca Martínez " + i);
                    explotacion.addBatch();
                }
                ganadero.executeBatch();
                explotacion.executeBatch();
            }
        }

        flyway(dataSource).load().migrate();

        Map<Long, String> ganaderos = columna(dataSource, "SELECT id, nombre_busqueda FROM ganadero ORDER BY id");
        Map<Long, String> explotaciones = columna(dataSource, "SELECT id, busqueda FROM explotacion ORDER BY id");
        assertThat(ganaderos).hasSize(filas).doesNotContainValue(null);
        assertThat(explotaciones).hasSize(filas).doesNotContainValue(null);
        for (long id : new long[]{1, 499, 500, 501, 1000, 1001, 1202, 1203}) {
            assertThat(ganaderos.get(id)).isEqualTo("ganadero pena " + id);
            assertThat(explotaciones.get(id)).isEqualTo(String.format("es%012d finca martinez %d", id, id));
        }
    }

    private static DataSource nuevaBaseDeDatos() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:v18_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
    }

    private static Map<Long, String> columna(DataSource dataSource, String sql) throws SQLException {
        Map<Long, String> valores = new LinkedHashMap<>();
        try (Connection conexion = dataSource.getConnection();
             Statement st = conexion.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                valores.put(rs.getLong(1), rs.getString(2));
            }
        }
        return valores;
    }

    private static boolean esNullable(DataSource dataSource, String tabla, String columna) throws SQLException {
        try (Connection conexion = dataSource.getConnection();
             ResultSet rs = conexion.getMetaData().getColumns(null, null, tabla, columna)) {
            assertThat(rs.next()).as("existe la columna %s.%s", tabla, columna).isTrue();
            return "YES".equals(rs.getString("IS_NULLABLE"));
        }
    }
}
