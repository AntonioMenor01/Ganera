package com.ganera.core.postgres;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V22 (respuesta_whatsapp) sobre PostgreSQL 17 real. Mismo patron que V22RespuestaWhatsappMigracionTest
 * (H2): migrar hasta la 21, sembrar, migrar al final; los UNIQUE con nombre fallan con el SQLState de
 * unicidad (23505), las claves ajenas con 23503 y los NOT NULL con 23502.
 */
class V22RespuestaWhatsappPostgresTest {

    @Test
    void laTablaAplicaSusRestriccionesEnPostgres() throws SQLException {
        DataSource dataSource = PostgresEmbebido.dataSource(PostgresEmbebido.nuevaBaseDeDatos());
        flyway(dataSource).target("21").load().migrate();
        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            st.executeUpdate("INSERT INTO gestoria (id, nombre) VALUES (1, 'Gestoría Uno'), (2, 'Gestoría Dos')");
            st.executeUpdate("INSERT INTO usuario (id, gestoria_id, email, password_hash, nombre) "
                    + "VALUES (1, 1, 'uno@test.com', 'x', 'Usuario Uno')");
            st.executeUpdate("INSERT INTO contacto (id, gestoria_id, telefono, nombre) "
                    + "VALUES (1, 1, '+34600000001', 'Contacto Uno')");
            st.executeUpdate("INSERT INTO tramite (id, gestoria_id, contacto_id, estado) VALUES (1, 1, 1, 'APROBADO')");
        }

        assertThat(flyway(dataSource).load().migrate().migrationsExecuted).isEqualTo(1);

        // Autocommit (por defecto): en PostgreSQL un error aborta la transaccion, asi cada sentencia va aislada.
        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            assertThat(st.executeUpdate(insertar(1, "SM1", "11111111-1111-1111-1111-111111111111"))).isEqualTo(1);
            assertThat(st.executeUpdate(insertar(2, "SM2", "11111111-1111-1111-1111-111111111111"))).isEqualTo(1);
            assertThat(st.executeUpdate(insertar(1, null, "33333333-3333-3333-3333-333333333333"))).isEqualTo(1);
            assertThat(st.executeUpdate(insertar(1, null, "44444444-4444-4444-4444-444444444444"))).isEqualTo(1);

            assertThatThrownBy(() -> st.executeUpdate(insertar(1, "SM3", "11111111-1111-1111-1111-111111111111")))
                    .isInstanceOfSatisfying(SQLException.class, e -> {
                        assertThat(e.getSQLState()).isEqualTo("23505");
                        assertThat(e.getMessage()).contains("uk_respuesta_whatsapp_clave");
                    });
            assertThatThrownBy(() -> st.executeUpdate(insertar(2, "SM1", "55555555-5555-5555-5555-555555555555")))
                    .isInstanceOfSatisfying(SQLException.class, e -> {
                        assertThat(e.getSQLState()).isEqualTo("23505");
                        assertThat(e.getMessage()).contains("uk_respuesta_whatsapp_sid");
                    });
            assertThatThrownBy(() -> st.executeUpdate(insertar(9, "SM9", "66666666-6666-6666-6666-666666666666")))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23503"));
            assertThatThrownBy(() -> st.executeUpdate(insertar(1, "SM8", "77777777-7777-7777-7777-777777777777")
                    .replace("'Hola'", "NULL")))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23502"));

            try (ResultSet rs = st.executeQuery("SELECT texto_vaciado, telefono_destino FROM respuesta_whatsapp "
                    + "WHERE twilio_message_sid = 'SM1'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean(1)).isFalse();
                assertThat(rs.getString(2)).isEqualTo("+34600000001");
            }
            try (ResultSet rs = st.executeQuery("SELECT data_type FROM information_schema.columns "
                    + "WHERE table_name = 'respuesta_whatsapp' AND column_name = 'cuerpo'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("text");
            }
        }
    }

    private static String insertar(int gestoria, String sid, String clave) {
        return "INSERT INTO respuesta_whatsapp (gestoria_id, tramite_id, contacto_id, telefono_destino, cuerpo, "
                + "enviado_por, creado_en, actualizado_en, estado, twilio_message_sid, codigo_error_twilio, "
                + "clave_idempotencia) VALUES (" + gestoria + ", 1, 1, '+34600000001', 'Hola', 1, "
                + "TIMESTAMP '2031-03-10 09:00:00', TIMESTAMP '2031-03-10 09:00:01', 'ENVIADO', "
                + (sid == null ? "NULL" : "'" + sid + "'") + ", NULL, '" + clave + "')";
    }

    /** Hasta la V22 como mucho (la que prueba este test): asi «una sola migracion» y «nada pendiente» siguen
     * siendo ciertos aunque se anadan migraciones posteriores (V23). */
    private static FluentConfiguration flyway(DataSource dataSource) {
        return PostgresEmbebido.flyway(dataSource).target("22");
    }
}
