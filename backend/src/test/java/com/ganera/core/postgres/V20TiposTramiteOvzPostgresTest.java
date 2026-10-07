package com.ganera.core.postgres;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V20 (tipos de tramite de OVZ, ficha OVZ T1) sobre PostgreSQL 16 real. Mismo patron que
 * V20TiposTramiteOvzMigracionTest (H2): migrar hasta la 19, insertar tramites con los tipos viejos,
 * migrar al final y comprobar cada valor, los null y que la columna admite 23 caracteres.
 */
class V20TiposTramiteOvzPostgresTest {

    @Test
    void convierteCadaTipoViejoAlDeOvzYEnsanchaLaColumna() throws SQLException {
        DataSource dataSource = PostgresEmbebido.dataSource(PostgresEmbebido.nuevaBaseDeDatos());
        flyway(dataSource).target("19").load().migrate();

        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            st.executeUpdate("INSERT INTO gestoria (id, nombre) VALUES (1, 'Gestoría Uno')");
            st.executeUpdate("INSERT INTO contacto (id, gestoria_id, telefono, nombre) "
                    + "VALUES (1, 1, '+34600000001', 'Contacto Uno')");
            st.executeUpdate("INSERT INTO tramite (id, gestoria_id, contacto_id, tipo_tramite, estado) VALUES "
                    + "(1, 1, 1, 'ALTA', 'PENDIENTE_REVISION'),"
                    + "(2, 1, 1, 'BAJA', 'APROBADO'),"
                    + "(3, 1, 1, 'MOVIMIENTO', 'PENDIENTE_REVISION'),"
                    + "(4, 1, 1, 'CENSO', 'RECHAZADO'),"
                    + "(5, 1, 1, 'DEMORA', 'PENDIENTE_REVISION'),"
                    + "(6, 1, 1, NULL, 'PENDIENTE_EXTRACCION'),"
                    + "(7, 1, 1, 'MOVIMIENTO', 'APROBADO')");
        }
        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            assertThatThrownBy(() -> st.executeUpdate("INSERT INTO tramite (id, gestoria_id, contacto_id, "
                    + "tipo_tramite, estado) VALUES (20, 1, 1, 'CONFIRMACION_MOVIMIENTO', 'PENDIENTE_REVISION')"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("character varying(20)");
        }

        flyway(dataSource).load().migrate();

        Map<Long, String> esperados = new LinkedHashMap<>();
        esperados.put(1L, "ALTA_NACIMIENTO");
        esperados.put(2L, "BAJA_MUERTE");
        esperados.put(3L, "SOLICITUD_MOVIMIENTO");
        esperados.put(4L, "DECLARACION_CENSO");
        esperados.put(5L, "DEMORA_CROTALIZACION");
        esperados.put(6L, null);
        esperados.put(7L, "SOLICITUD_MOVIMIENTO");
        assertThat(columna(dataSource, "SELECT id, tipo_tramite FROM tramite ORDER BY id"))
                .containsExactlyEntriesOf(esperados);

        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            st.executeUpdate("INSERT INTO tramite (id, gestoria_id, contacto_id, tipo_tramite, estado) "
                    + "VALUES (20, 1, 1, 'CONFIRMACION_MOVIMIENTO', 'PENDIENTE_REVISION')");
        }
        assertThat(columna(dataSource, "SELECT id, tipo_tramite FROM tramite WHERE id = 20"))
                .containsExactly(Map.entry(20L, "CONFIRMACION_MOVIMIENTO"));
        assertThat(longitud(dataSource, "tramite", "tipo_tramite")).isEqualTo(30);
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
    private static int longitud(DataSource dataSource, String tabla, String columna) throws SQLException {
        try (Connection conexion = dataSource.getConnection();
             ResultSet rs = conexion.getMetaData().getColumns(null, "public", tabla, columna)) {
            assertThat(rs.next()).as("existe la columna %s.%s", tabla, columna).isTrue();
            return rs.getInt("COLUMN_SIZE");
        }
    }
}
