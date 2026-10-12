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
 * V23 (mensajes sin tramite, plan 2026-10-12, A1/A6) sobre una H2 en memoria PROPIA (MODE=PostgreSQL),
 * mismo patron que V21HechoEnOvzMigracionTest: migrar hasta la 22, insertar tramites en varios estados,
 * migrar al final y comprobar que las filas previas quedan con motivo_rechazo null, que el CHECK
 * ck_tramite_motivo_rechazo solo admite un codigo previsto en un RECHAZADO, y que tramite_evento solo
 * admite sus dos tipos y usuario/tramite/gestoria existentes. Las sentencias son las mismas que en
 * V23DescarteSinTramitePostgresTest ({@link V23SentenciasDePrueba}).
 */
class V23DescarteSinTramiteMigracionTest {

    /** SQLState de H2 para la violacion de un CHECK; en PostgreSQL es 23514. */
    private static final String CHECK_VIOLADO_H2 = "23513";
    /** SQLState de H2 para la violacion de una clave ajena; en PostgreSQL es 23503. */
    private static final String FK_VIOLADA_H2 = "23506";

    @Test
    void lasFilasPreviasQuedanSinMotivoYUnArranquePosteriorNoVuelveAMigrar() throws SQLException {
        DataSource dataSource = nuevaBaseDeDatos();
        flyway(dataSource).target("22").load().migrate();
        ejecutar(dataSource, V23SentenciasDePrueba.DATOS_PREVIOS);

        flyway(dataSource).load().migrate();

        List<String> filas = new ArrayList<>();
        try (Connection conexion = dataSource.getConnection();
             Statement st = conexion.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, estado, motivo_rechazo FROM tramite ORDER BY id")) {
            while (rs.next()) {
                filas.add(rs.getLong(1) + "|" + rs.getString(2) + "|" + rs.getObject(3));
            }
        }
        assertThat(filas).containsExactly(
                "1|PENDIENTE_EXTRACCION|null",
                "2|PENDIENTE_REVISION|null",
                "3|APROBADO|null",
                "4|RECHAZADO|null",
                "5|HECHO_EN_OVZ|null");
        try (Connection conexion = dataSource.getConnection();
             Statement st = conexion.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM tramite_evento")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
        flyway(dataSource).load().validate();
        assertThat(flyway(dataSource).load().migrate().migrationsExecuted).isZero();
    }

    /** A5: el CHECK no admite motivo_rechazo con un estado distinto de RECHAZADO. */
    @Test
    void elCheckSoloAdmiteElMotivoEnUnRechazado() throws SQLException {
        DataSource dataSource = migradaConDatos();

        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            for (String sql : V23SentenciasDePrueba.MOTIVO_CON_OTRO_ESTADO) {
                assertThatThrownBy(() -> st.executeUpdate(sql))
                        .as(sql)
                        .isInstanceOfSatisfying(SQLException.class, e -> {
                            assertThat(e.getSQLState()).isEqualTo(CHECK_VIOLADO_H2);
                            assertThat(e.getMessage()).containsIgnoringCase("ck_tramite_motivo_rechazo");
                        });
            }
            for (String sql : V23SentenciasDePrueba.COHERENTES) {
                assertThat(st.executeUpdate(sql)).as(sql).isEqualTo(1);
            }
        }
    }

    /** A1: motivo_rechazo guarda un codigo de una lista cerrada, nunca texto libre. */
    @Test
    void elCheckRechazaUnCodigoNoPrevistoAunqueSeaUnRechazado() throws SQLException {
        DataSource dataSource = migradaConDatos();

        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            for (String sql : V23SentenciasDePrueba.CODIGO_NO_PREVISTO) {
                assertThatThrownBy(() -> st.executeUpdate(sql))
                        .as(sql)
                        .isInstanceOfSatisfying(SQLException.class, e -> {
                            assertThat(e.getSQLState()).isEqualTo(CHECK_VIOLADO_H2);
                            assertThat(e.getMessage()).containsIgnoringCase("ck_tramite_motivo_rechazo");
                        });
            }
        }
    }

    @Test
    void tramiteEventoSoloAdmiteSusDosTiposYClavesAjenasExistentes() throws SQLException {
        DataSource dataSource = migradaConDatos();

        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            for (String sql : V23SentenciasDePrueba.EVENTOS_VALIDOS) {
                assertThat(st.executeUpdate(sql)).as(sql).isEqualTo(1);
            }
            assertThatThrownBy(() -> st.executeUpdate(V23SentenciasDePrueba.EVENTO_TIPO_NO_PREVISTO))
                    .isInstanceOfSatisfying(SQLException.class, e -> {
                        assertThat(e.getSQLState()).isEqualTo(CHECK_VIOLADO_H2);
                        assertThat(e.getMessage()).containsIgnoringCase("ck_tramite_evento_tipo");
                    });
            for (String sql : V23SentenciasDePrueba.EVENTOS_CON_FK_ROTA) {
                assertThatThrownBy(() -> st.executeUpdate(sql))
                        .as(sql)
                        .isInstanceOfSatisfying(SQLException.class,
                                e -> assertThat(e.getSQLState()).isEqualTo(FK_VIOLADA_H2));
            }
        }
    }

    private static DataSource migradaConDatos() throws SQLException {
        DataSource dataSource = nuevaBaseDeDatos();
        flyway(dataSource).target("22").load().migrate();
        ejecutar(dataSource, V23SentenciasDePrueba.DATOS_PREVIOS);
        flyway(dataSource).load().migrate();
        ejecutar(dataSource, V23SentenciasDePrueba.DATOS_TRAS_MIGRAR);
        return dataSource;
    }

    private static void ejecutar(DataSource dataSource, List<String> sentencias) throws SQLException {
        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            for (String sql : sentencias) {
                st.executeUpdate(sql);
            }
        }
    }

    private static DataSource nuevaBaseDeDatos() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:v23_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static FluentConfiguration flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
    }
}
