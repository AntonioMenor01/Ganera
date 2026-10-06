package com.ganera.core.postgres;

import com.ganera.core.shared.texto.NormalizadorBusqueda;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V18 (Java, solo JDBC) sobre datos existentes en PostgreSQL 16 real (Prompt C, T1). Mismo patron
 * que V18BusquedaNormalizadaMigracionTest (H2): migrar hasta la 17, insertar filas como las de
 * antes de la V18 (con tildes, enes, signos y mas de un lote), migrar al final y comprobar los
 * valores, que coinciden con NormalizadorBusqueda, y el NOT NULL.
 */
class V18BusquedaNormalizadaPostgresTest {

    @Test
    void rellenaLasFilasExistentesConTildesYEnesYLasDejaNotNull() throws SQLException {
        DataSource dataSource = PostgresEmbebido.dataSource(PostgresEmbebido.nuevaBaseDeDatos());
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

        Map<Long, String> ganaderos = columna(dataSource, "SELECT id, nombre_busqueda FROM ganadero ORDER BY id");
        assertThat(ganaderos).containsExactly(
                Map.entry(1L, "jose martinez"),
                Map.entry(2L, "penagarcia sl"),
                Map.entry(3L, "martin nunez"));
        assertThat(columna(dataSource, "SELECT id, busqueda FROM explotacion ORDER BY id"))
                .containsExactly(
                        Map.entry(1L, "es12 0000 0001 finca la pena"),
                        Map.entry(2L, "es120000000002 cortijo martinperez"),
                        Map.entry(3L, "es120000000003"));
        // Los textos han ido y vuelto por PostgreSQL sin estropearse: lo mismo que calcula Java.
        assertThat(ganaderos.get(3L)).isEqualTo(NormalizadorBusqueda.normalizar("Mart´in Ñúñez"));
        assertThat(columna(dataSource, "SELECT id, nombre FROM ganadero ORDER BY id").get(3L))
                .isEqualTo("Mart´in Ñúñez");

        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO ganadero (id, gestoria_id, nombre, nif) VALUES (10, 1, 'Sin columna', '10X')"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("nombre_busqueda");
        }
        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO explotacion (id, gestoria_id, ganadero_id, codigo_rega, nombre) "
                            + "VALUES (10, 1, 1, 'ES129999999999', 'Sin columna')"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("busqueda");
        }
        assertThat(esNullable(dataSource, "ganadero", "nombre_busqueda")).isFalse();
        assertThat(esNullable(dataSource, "explotacion", "busqueda")).isFalse();
    }

    /** 1203 filas: dos cortes de lote completos (500 y 1000) y un lote final parcial de 203. */
    @Test
    void conMasFilasQueUnLoteRellenaTodasCruzandoVariosCortes() throws SQLException {
        int filas = 1203;
        DataSource dataSource = PostgresEmbebido.dataSource(PostgresEmbebido.nuevaBaseDeDatos());
        flyway(dataSource).target("17").load().migrate();

        try (Connection conexion = dataSource.getConnection()) {
            try (Statement st = conexion.createStatement()) {
                st.executeUpdate("INSERT INTO gestoria (id, nombre) VALUES (1, 'Gestoría Lotes')");
            }
            try (PreparedStatement ganadero = conexion.prepareStatement(
                    "INSERT INTO ganadero (id, gestoria_id, nombre, nif) VALUES (?, 1, ?, ?)")) {
                for (int i = 1; i <= filas; i++) {
                    ganadero.setLong(1, i);
                    ganadero.setString(2, "Ganadero Peña " + i);
                    ganadero.setString(3, "NIF" + i);
                    ganadero.addBatch();
                }
                ganadero.executeBatch();
            }
            // En PostgreSQL la clave ajena se comprueba fila a fila: los ganaderos van antes.
            try (PreparedStatement explotacion = conexion.prepareStatement(
                    "INSERT INTO explotacion (id, gestoria_id, ganadero_id, codigo_rega, nombre) "
                            + "VALUES (?, 1, ?, ?, ?)")) {
                for (int i = 1; i <= filas; i++) {
                    explotacion.setLong(1, i);
                    explotacion.setLong(2, i);
                    explotacion.setString(3, String.format("ES-%012d", i));
                    explotacion.setString(4, "Finca Martínez " + i);
                    explotacion.addBatch();
                }
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
        assertThat(esNullable(dataSource, "ganadero", "nombre_busqueda")).isFalse();
        assertThat(esNullable(dataSource, "explotacion", "busqueda")).isFalse();
    }

    private static FluentConfiguration flyway(DataSource dataSource) {
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

    /** En PostgreSQL los nombres sin comillas se guardan en minusculas (en H2, en mayusculas). */
    private static boolean esNullable(DataSource dataSource, String tabla, String columna) throws SQLException {
        try (Connection conexion = dataSource.getConnection();
             ResultSet rs = conexion.getMetaData().getColumns(null, "public", tabla, columna)) {
            assertThat(rs.next()).as("existe la columna %s.%s", tabla, columna).isTrue();
            return "YES".equals(rs.getString("IS_NULLABLE"));
        }
    }
}
