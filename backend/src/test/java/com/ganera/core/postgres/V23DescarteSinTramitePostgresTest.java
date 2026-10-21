package com.ganera.core.postgres;

import com.ganera.core.migracion.V23SentenciasDePrueba;
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
 * V23 (mensajes sin tramite) sobre PostgreSQL 17 real, con las MISMAS sentencias que
 * V23DescarteSinTramiteMigracionTest (H2): filas previas sin motivo; el CHECK ck_tramite_motivo_rechazo
 * rechaza el motivo con otro estado y un codigo no previsto con el SQLState de CHECK de PostgreSQL (23514);
 * tramite_evento con su CHECK de tipo y sus tres claves ajenas (23503).
 */
class V23DescarteSinTramitePostgresTest {

    private static final String CHECK_VIOLADO_POSTGRES = "23514";
    private static final String FK_VIOLADA_POSTGRES = "23503";

    @Test
    void lasFilasPreviasQuedanSinMotivoYLosChecksYClavesAjenasSeCumplen() throws SQLException {
        DataSource dataSource = PostgresEmbebido.dataSource(PostgresEmbebido.nuevaBaseDeDatos());
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
        ejecutar(dataSource, V23SentenciasDePrueba.DATOS_TRAS_MIGRAR);

        // En PostgreSQL un error aborta la transaccion: autocommit (por defecto) aisla cada sentencia.
        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            List<String> invalidas = new ArrayList<>(V23SentenciasDePrueba.MOTIVO_CON_OTRO_ESTADO);
            invalidas.addAll(V23SentenciasDePrueba.CODIGO_NO_PREVISTO);
            for (String sql : invalidas) {
                assertThatThrownBy(() -> st.executeUpdate(sql))
                        .as(sql)
                        .isInstanceOfSatisfying(SQLException.class, e -> {
                            assertThat(e.getSQLState()).isEqualTo(CHECK_VIOLADO_POSTGRES);
                            assertThat(e.getMessage()).contains("ck_tramite_motivo_rechazo");
                        });
            }
            for (String sql : V23SentenciasDePrueba.COHERENTES) {
                assertThat(st.executeUpdate(sql)).as(sql).isEqualTo(1);
            }
            for (String sql : V23SentenciasDePrueba.EVENTOS_VALIDOS) {
                assertThat(st.executeUpdate(sql)).as(sql).isEqualTo(1);
            }
            assertThatThrownBy(() -> st.executeUpdate(V23SentenciasDePrueba.EVENTO_TIPO_NO_PREVISTO))
                    .isInstanceOfSatisfying(SQLException.class, e -> {
                        assertThat(e.getSQLState()).isEqualTo(CHECK_VIOLADO_POSTGRES);
                        assertThat(e.getMessage()).contains("ck_tramite_evento_tipo");
                    });
            for (String sql : V23SentenciasDePrueba.EVENTOS_CON_FK_ROTA) {
                assertThatThrownBy(() -> st.executeUpdate(sql))
                        .as(sql)
                        .isInstanceOfSatisfying(SQLException.class,
                                e -> assertThat(e.getSQLState()).isEqualTo(FK_VIOLADA_POSTGRES));
            }
        }
    }

    private static void ejecutar(DataSource dataSource, List<String> sentencias) throws SQLException {
        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            for (String sql : sentencias) {
                st.executeUpdate(sql);
            }
        }
    }

    private static FluentConfiguration flyway(DataSource dataSource) {
        return PostgresEmbebido.flyway(dataSource);
    }
}
