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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V20 (tipos de tramite de OVZ, ficha OVZ T1) sobre una H2 en memoria PROPIA (MODE=PostgreSQL),
 * mismo patron que V18BusquedaNormalizadaMigracionTest: migrar hasta la 19, insertar tramites con
 * los tipos viejos, migrar al final y comprobar cada valor. Todo MOVIMIENTO pasa a
 * SOLICITUD_MOVIMIENTO; los null siguen null; la columna admite ya CONFIRMACION_MOVIMIENTO (23).
 */
class V20TiposTramiteOvzMigracionTest {

    @Test
    void convierteCadaTipoViejoAlDeOvzYDejaLosNullComoEstan() throws SQLException {
        DataSource dataSource = nuevaBaseDeDatos();
        flyway(dataSource).target("19").load().migrate();
        insertarTramitesConTiposViejos(dataSource);

        flyway(dataSource).load().migrate();

        Map<Long, String> tipos = columna(dataSource, "SELECT id, tipo_tramite FROM tramite ORDER BY id");
        Map<Long, String> esperados = new LinkedHashMap<>();
        esperados.put(1L, "ALTA_NACIMIENTO");
        esperados.put(2L, "BAJA_MUERTE");
        esperados.put(3L, "SOLICITUD_MOVIMIENTO");
        esperados.put(4L, "DECLARACION_CENSO");
        esperados.put(5L, "DEMORA_CROTALIZACION");
        esperados.put(6L, null);
        esperados.put(7L, "SOLICITUD_MOVIMIENTO");
        assertThat(tipos).containsExactlyEntriesOf(esperados);
    }

    @Test
    void antesDeLaV20NoCabeConfirmacionMovimientoYDespuesSi() throws SQLException {
        DataSource dataSource = nuevaBaseDeDatos();
        flyway(dataSource).target("19").load().migrate();
        insertarTramitesConTiposViejos(dataSource);

        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            assertThatThrownBy(() -> st.executeUpdate("INSERT INTO tramite (id, gestoria_id, contacto_id, "
                    + "tipo_tramite, estado) VALUES (20, 1, 1, 'CONFIRMACION_MOVIMIENTO', 'PENDIENTE_REVISION')"))
                    // 22001 = valor demasiado largo para la columna (revision T1, M1): no vale otro fallo.
                    .isInstanceOfSatisfying(SQLException.class,
                            e -> assertThat(e.getSQLState()).isEqualTo("22001"));
        }

        flyway(dataSource).load().migrate();

        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            st.executeUpdate("INSERT INTO tramite (id, gestoria_id, contacto_id, tipo_tramite, estado) "
                    + "VALUES (20, 1, 1, 'CONFIRMACION_MOVIMIENTO', 'PENDIENTE_REVISION')");
        }
        assertThat(columna(dataSource, "SELECT id, tipo_tramite FROM tramite WHERE id = 20"))
                .containsExactly(Map.entry(20L, "CONFIRMACION_MOVIMIENTO"));
        assertThat(longitud(dataSource, "TRAMITE", "TIPO_TRAMITE")).isEqualTo(30);
        // Un arranque posterior: validate no falla y migrate no vuelve a ejecutar la V20.
        flyway(dataSource).load().validate();
        assertThat(flyway(dataSource).load().migrate().migrationsExecuted).isZero();
    }

    private static void insertarTramitesConTiposViejos(DataSource dataSource) throws SQLException {
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
    }

    private static DataSource nuevaBaseDeDatos() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:v20_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
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

    private static int longitud(DataSource dataSource, String tabla, String columna) throws SQLException {
        try (Connection conexion = dataSource.getConnection();
             ResultSet rs = conexion.getMetaData().getColumns(null, null, tabla, columna)) {
            assertThat(rs.next()).as("existe la columna %s.%s", tabla, columna).isTrue();
            return rs.getInt("COLUMN_SIZE");
        }
    }
}
