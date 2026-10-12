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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V22 (respuesta_whatsapp, B2 reducido T1) sobre una H2 en memoria PROPIA (MODE=PostgreSQL), mismo
 * patron que V21HechoEnOvzMigracionTest: migrar hasta la 21, sembrar, migrar al final y comprobar
 * columnas, nulos, valores por defecto, claves ajenas y los dos UNIQUE con su SQLState concreto.
 */
class V22RespuestaWhatsappMigracionTest {

    /** SQLState de violacion de unicidad (el mismo en H2 y en PostgreSQL). */
    private static final String UNICO_VIOLADO = "23505";
    /** SQLState de violacion de clave ajena en H2 (en PostgreSQL, 23503). */
    private static final String FK_VIOLADA_H2 = "23506";
    /** SQLState de NOT NULL violado en H2 (en PostgreSQL, 23502). */
    private static final String NULO_VIOLADO_H2 = "23502";

    @Test
    void creaLaTablaConSusColumnasYUnaFilaValidaSeInserta() throws SQLException {
        DataSource dataSource = migrada();

        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            assertThat(st.executeUpdate(insertar(1, "SM1", "11111111-1111-1111-1111-111111111111"))).isEqualTo(1);
            // telefono_destino, twilio_message_sid y codigo_error_twilio admiten null; texto_vaciado por defecto false.
            assertThat(st.executeUpdate("INSERT INTO respuesta_whatsapp (gestoria_id, tramite_id, contacto_id, cuerpo, "
                    + "enviado_por, creado_en, actualizado_en, estado, clave_idempotencia) VALUES "
                    + "(1, 1, 1, 'Hola', 1, TIMESTAMP '2031-03-10 09:00:00', TIMESTAMP '2031-03-10 09:00:00', "
                    + "'ENVIANDO', '22222222-2222-2222-2222-222222222222')")).isEqualTo(1);
            Map<String, Object> fila = new HashMap<>();
            try (ResultSet rs = st.executeQuery("SELECT * FROM respuesta_whatsapp "
                    + "WHERE clave_idempotencia = '22222222-2222-2222-2222-222222222222'")) {
                assertThat(rs.next()).isTrue();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    fila.put(rs.getMetaData().getColumnName(i).toLowerCase(Locale.ROOT), rs.getObject(i));
                }
            }
            assertThat(fila.keySet()).containsExactlyInAnyOrder("id", "gestoria_id", "tramite_id", "contacto_id",
                    "telefono_destino", "cuerpo", "enviado_por", "creado_en", "actualizado_en", "estado",
                    "twilio_message_sid", "codigo_error_twilio", "clave_idempotencia", "texto_vaciado");
            assertThat(fila.get("telefono_destino")).isNull();
            assertThat(fila.get("twilio_message_sid")).isNull();
            assertThat(fila.get("codigo_error_twilio")).isNull();
            assertThat(fila.get("texto_vaciado")).isEqualTo(false);
        }
        flyway(dataSource).load().validate();
        assertThat(flyway(dataSource).load().migrate().migrationsExecuted).isZero();
    }

    @Test
    void laClaveEsUnicaPorGestoriaYElSidEsUnicoGlobal() throws SQLException {
        DataSource dataSource = migrada();
        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            st.executeUpdate(insertar(1, "SM1", "11111111-1111-1111-1111-111111111111"));
            // Misma clave en otra gestoria: permitido.
            assertThat(st.executeUpdate(insertar(2, "SM2", "11111111-1111-1111-1111-111111111111"))).isEqualTo(1);
            // Dos filas sin SID: permitido (UNIQUE admite varios null).
            assertThat(st.executeUpdate(insertar(1, null, "33333333-3333-3333-3333-333333333333"))).isEqualTo(1);
            assertThat(st.executeUpdate(insertar(1, null, "44444444-4444-4444-4444-444444444444"))).isEqualTo(1);

            assertThatThrownBy(() -> st.executeUpdate(insertar(1, "SM3", "11111111-1111-1111-1111-111111111111")))
                    .isInstanceOfSatisfying(SQLException.class, e -> {
                        assertThat(e.getSQLState()).isEqualTo(UNICO_VIOLADO);
                        assertThat(e.getMessage()).containsIgnoringCase("uk_respuesta_whatsapp_clave");
                    });
            assertThatThrownBy(() -> st.executeUpdate(insertar(2, "SM1", "55555555-5555-5555-5555-555555555555")))
                    .isInstanceOfSatisfying(SQLException.class, e -> {
                        assertThat(e.getSQLState()).isEqualTo(UNICO_VIOLADO);
                        assertThat(e.getMessage()).containsIgnoringCase("uk_respuesta_whatsapp_sid");
                    });
        }
    }

    @Test
    void lasClavesAjenasYLosNotNullSeRespetan() throws SQLException {
        DataSource dataSource = migrada();
        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            for (String sql : new String[]{
                    insertar(1, "SMx", "66666666-6666-6666-6666-666666666666").replace("(1, 1, 1, '+34", "(1, 999, 1, '+34"),
                    insertar(1, "SMy", "77777777-7777-7777-7777-777777777777").replace("(1, 1, 1, '+34", "(1, 1, 999, '+34"),
                    insertar(9, "SMz", "88888888-8888-8888-8888-888888888888"),
                    insertar(1, "SMw", "99999999-9999-9999-9999-999999999999").replace("'Hola', 1,", "'Hola', 999,")}) {
                assertThatThrownBy(() -> st.executeUpdate(sql)).as(sql)
                        .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(FK_VIOLADA_H2));
            }
            assertThatThrownBy(() -> st.executeUpdate(insertar(1, "SMv", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                    .replace("'Hola'", "NULL")))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(NULO_VIOLADO_H2));
            assertThatThrownBy(() -> st.executeUpdate(insertar(1, "SMu", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
                    .replace("'ENVIADO'", "NULL")))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(NULO_VIOLADO_H2));
        }
    }

    /** Fila valida de la gestoria {@code gestoria} (1 o 2) sobre el tramite/contacto/usuario 1. */
    static String insertar(int gestoria, String sid, String clave) {
        return "INSERT INTO respuesta_whatsapp (gestoria_id, tramite_id, contacto_id, telefono_destino, cuerpo, "
                + "enviado_por, creado_en, actualizado_en, estado, twilio_message_sid, codigo_error_twilio, "
                + "clave_idempotencia) VALUES (" + gestoria + ", 1, 1, '+34600000001', 'Hola', 1, "
                + "TIMESTAMP '2031-03-10 09:00:00', TIMESTAMP '2031-03-10 09:00:01', 'ENVIADO', "
                + (sid == null ? "NULL" : "'" + sid + "'") + ", NULL, '" + clave + "')";
    }

    static void sembrar(Statement st) throws SQLException {
        st.executeUpdate("INSERT INTO gestoria (id, nombre) VALUES (1, 'Gestoría Uno'), (2, 'Gestoría Dos')");
        st.executeUpdate("INSERT INTO usuario (id, gestoria_id, email, password_hash, nombre) "
                + "VALUES (1, 1, 'uno@test.com', 'x', 'Usuario Uno')");
        st.executeUpdate("INSERT INTO contacto (id, gestoria_id, telefono, nombre) "
                + "VALUES (1, 1, '+34600000001', 'Contacto Uno')");
        st.executeUpdate("INSERT INTO tramite (id, gestoria_id, contacto_id, estado) VALUES (1, 1, 1, 'APROBADO')");
    }

    private static DataSource migrada() throws SQLException {
        DataSource dataSource = nuevaBaseDeDatos();
        flyway(dataSource).target("21").load().migrate();
        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            sembrar(st);
        }
        assertThat(flyway(dataSource).load().migrate().migrationsExecuted).isEqualTo(1);
        return dataSource;
    }

    private static DataSource nuevaBaseDeDatos() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:v22_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    /** Hasta la V22 como mucho (la que prueba este test): asi «una sola migracion» y «nada pendiente» siguen
     * siendo ciertos aunque se anadan migraciones posteriores (V23). */
    private static FluentConfiguration flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("22");
    }
}
