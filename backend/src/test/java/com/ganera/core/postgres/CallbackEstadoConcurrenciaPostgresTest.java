package com.ganera.core.postgres;

import com.ganera.core.whatsapp.EstadoRespuestaWhatsappService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Callback de estado (B2 reducido, T4) sobre PostgreSQL 17 real: el UPDATE condicional por SID no retrocede
 * aunque dos callbacks lleguen a la vez. Determinista: una transaccion JDBC deja la fila en LEIDO SIN confirmar
 * (con el bloqueo de fila); el {@code delivered} de la aplicacion se queda esperando ese bloqueo y, al confirmarse
 * la primera, PostgreSQL (READ COMMITTED) vuelve a evaluar el WHERE sobre la fila nueva: 0 filas, sigue LEIDO.
 */
class CallbackEstadoConcurrenciaPostgresTest {

    private static final String SID = "SMconcurrencia000000000000000000001";

    @Test
    void unDeliveredQueEsperaAUnReadSinConfirmarNoLoPisa() throws Exception {
        String baseDeDatos = PostgresEmbebido.nuevaBaseDeDatos();
        try (ConfigurableApplicationContext contexto = PostgresEmbebido.arrancarAplicacion(baseDeDatos, "validate")) {
            JdbcTemplate jdbc = contexto.getBean(JdbcTemplate.class);
            sembrar(jdbc);
            EstadoRespuestaWhatsappService servicio = contexto.getBean(EstadoRespuestaWhatsappService.class);
            DataSource dataSource = PostgresEmbebido.dataSource(baseDeDatos);
            ExecutorService hilo = Executors.newSingleThreadExecutor();
            try (Connection primera = dataSource.getConnection()) {
                primera.setAutoCommit(false);
                try (Statement st = primera.createStatement()) {
                    assertThat(st.executeUpdate("UPDATE respuesta_whatsapp SET estado = 'LEIDO' "
                            + "WHERE twilio_message_sid = '" + SID + "' AND estado IN ('ENVIADO', 'ENTREGADO')"))
                            .isEqualTo(1);
                }

                Future<Boolean> entregado = hilo.submit(() -> servicio.aplicar(SID, "delivered", null));
                // Esta bloqueado por la fila de la primera transaccion.
                assertThatThrownBy(() -> entregado.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);

                primera.commit();
                assertThat(entregado.get(30, TimeUnit.SECONDS)).isFalse();
            } finally {
                hilo.shutdownNow();
            }
            assertThat(jdbc.queryForObject("SELECT estado FROM respuesta_whatsapp WHERE twilio_message_sid = ?",
                    String.class, SID)).isEqualTo("LEIDO");
        }
    }

    private static void sembrar(JdbcTemplate jdbc) {
        jdbc.execute("SET TIME ZONE 'UTC'");
        jdbc.update("INSERT INTO gestoria (id, nombre, created_at) VALUES (1, 'Gestoría Uno', TIMESTAMP '2031-03-10 09:00:00')");
        jdbc.update("INSERT INTO usuario (id, gestoria_id, email, password_hash, nombre, created_at) "
                + "VALUES (1, 1, 'uno@test.com', 'x', 'Usuario Uno', TIMESTAMP '2031-03-10 09:00:00')");
        jdbc.update("INSERT INTO contacto (id, gestoria_id, telefono, nombre, created_at) "
                + "VALUES (1, 1, '+34600000001', 'Contacto Uno', TIMESTAMP '2031-03-10 09:00:00')");
        jdbc.update("INSERT INTO tramite (id, gestoria_id, contacto_id, estado, created_at) "
                + "VALUES (1, 1, 1, 'PENDIENTE_REVISION', TIMESTAMP '2031-03-10 09:00:00')");
        jdbc.update("INSERT INTO respuesta_whatsapp (gestoria_id, tramite_id, contacto_id, telefono_destino, cuerpo, "
                + "enviado_por, creado_en, actualizado_en, estado, twilio_message_sid, clave_idempotencia) VALUES "
                + "(1, 1, 1, '+34600000001', 'Hola', 1, TIMESTAMP '2031-03-10 09:00:00', "
                + "TIMESTAMP '2031-03-10 09:00:00', 'ENVIADO', '" + SID + "', "
                + "'11111111-1111-1111-1111-111111111111')");
    }
}
