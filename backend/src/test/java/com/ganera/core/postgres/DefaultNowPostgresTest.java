package com.ganera.core.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de caracterizacion (Prompt C, T1, decision D8): las columnas created_at son TIMESTAMP (sin
 * zona) con DEFAULT now(). La aplicacion nunca usa ese DEFAULT (las entidades ponen Instant.now() y
 * la columna queda con la hora de pared UTC, ver InstantTimestampPostgresTest), pero una fila
 * insertada A MANO por SQL sin created_at recibe now() convertido a la ZONA DE LA SESION:
 * <ul>
 *   <li>con la sesion en Europe/Madrid queda con la hora local de Madrid, y la aplicacion, que lee
 *       la columna como UTC, la ve desplazada el desfase de Madrid en ese momento (+2 h en verano,
 *       +1 h en invierno; observado el 2026-10-06: PT2H);</li>
 *   <li>con {@code SET TIME ZONE 'UTC'} al principio queda bien.</li>
 * </ul>
 * Por eso (D8, sin V20 ni codigo nuevo) el SQL de siembra de T4 empieza con
 * {@code SET TIME ZONE 'UTC'} y pone created_at explicito, y Clever corre con TZ=UTC.
 */
class DefaultNowPostgresTest {

    @Test
    void conLaSesionEnUtcElDefaultNowGuardaLaHoraDeParedUtcQueLeeLaAplicacion() throws SQLException {
        Duration desfase = desfaseDeUnaFilaPorSql("UTC");

        assertThat(desfase.abs()).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void conLaSesionEnMadridElDefaultNowGuardaLaHoraLocalYLaAplicacionLaVeDesplazada() throws SQLException {
        ZoneOffset desfaseMadrid = ZoneId.of("Europe/Madrid").getRules().getOffset(Instant.now());

        Duration desfase = desfaseDeUnaFilaPorSql("Europe/Madrid");

        // El desfase es exactamente el de Madrid en ese instante (1 h o 2 h), con margen de ejecucion.
        assertThat(desfase.minusSeconds(desfaseMadrid.getTotalSeconds()).abs()).isLessThan(Duration.ofMinutes(1));
        assertThat(desfase).isBetween(Duration.ofMinutes(59), Duration.ofMinutes(121));
    }

    /**
     * Inserta una gestoria por SQL sin created_at con la sesion en {@code zonaSesion} y devuelve
     * cuanto se separa de "ahora" el created_at interpretado como lo interpreta la aplicacion
     * (hora de pared UTC).
     */
    private static Duration desfaseDeUnaFilaPorSql(String zonaSesion) throws SQLException {
        DataSource dataSource = PostgresEmbebido.dataSource(PostgresEmbebido.nuevaBaseDeDatos());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        try (Connection conexion = dataSource.getConnection(); Statement st = conexion.createStatement()) {
            st.execute("SET TIME ZONE '" + zonaSesion + "'");
            Instant antes = Instant.now();
            st.executeUpdate("INSERT INTO gestoria (nombre) VALUES ('Insertada por SQL')");
            try (ResultSet rs = st.executeQuery("SELECT created_at FROM gestoria")) {
                assertThat(rs.next()).isTrue();
                LocalDateTime pared = rs.getObject(1, LocalDateTime.class);
                Duration desfase = Duration.between(antes, pared.toInstant(ZoneOffset.UTC));
                System.out.printf("[T1 DEFAULT now()] sesion=%s ahora=%s columna=%s desfase=%s%n",
                        zonaSesion, antes, pared, desfase);
                return desfase;
            }
        }
    }
}
