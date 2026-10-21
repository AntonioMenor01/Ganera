package com.ganera.core.migracion;

import com.ganera.core.GaneraApplication;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migracion a Supabase, T1: con {@code classpath:db/{vendor}} en las ubicaciones, Boot la resuelve a
 * {@code classpath:db/h2} en H2 y Flyway aplica V1-V24 sin avisar de una ubicacion que no existe (la carpeta
 * db/h2 solo tiene un LEEME.md, que Maven copia y Flyway ignora). Flyway 10.20.1 no avisa en WARN de una
 * ubicacion inexistente (lo deja en DEBUG), asi que el test arranca con fail-on-missing-locations y el escaner de
 * Flyway en DEBUG.
 *
 * <p>El contexto es propio (SpringApplicationBuilder), no el cacheado de {@code @SpringBootTest}: con uno
 * reutilizado, el log de Flyway ya habria salido antes de capturarlo y el test pasaria sin probar nada. El aviso
 * conocido de que H2 2.3.232 es mas nuevo que lo probado por Flyway no cuenta: solo se buscan avisos de ubicacion.
 */
@ExtendWith(OutputCaptureExtension.class)
class FlywayH2VendorTest {

    @Test
    void enH2SeResuelveDbH2YSeAplicanLasVeinticuatroSinAvisoDeUbicacion(CapturedOutput salida) {
        try (ConfigurableApplicationContext contexto = new SpringApplicationBuilder(GaneraApplication.class).run(
                "--spring.datasource.url=jdbc:h2:mem:flyway_h2_vendor;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                "--spring.datasource.hikari.pool-name=flyway-h2-vendor",
                "--spring.main.web-application-type=none",
                "--spring.jmx.enabled=false",
                // Flyway 10.20.1 solo registra en DEBUG «Unable to resolve location …» para una ubicacion que no
                // existe; con fail-on-missing-locations el arranque falla, y con el escaner en DEBUG se ve en el log.
                "--spring.flyway.fail-on-missing-locations=true",
                "--logging.level.org.flywaydb.core.internal.scanner=DEBUG")) {

            Flyway flyway = contexto.getBean(Flyway.class);
            assertThat(Arrays.stream(flyway.getConfiguration().getLocations()).map(Location::getDescriptor))
                    .containsExactlyInAnyOrder("classpath:db/migration", "classpath:db/h2");

            MigrationInfo[] aplicadas = flyway.info().applied();
            assertThat(Arrays.stream(aplicadas).map(m -> m.getVersion().getVersion()).toList())
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, 24).mapToObj(String::valueOf).toList());
            assertThat(aplicadas).allSatisfy(m -> assertThat(m.getState()).isEqualTo(MigrationState.SUCCESS));

            // Comprobacion de que de verdad se ha capturado el log de Flyway; si no, la siguiente no probaria nada.
            assertThat(salida.getAll()).contains("Successfully applied 24 migrations");
            assertThat(salida.getAll()).contains("classpath:db/h2");
            // («Unable to resolve location classpath:db/callback.» sale siempre en DEBUG: es la ubicacion de callbacks
            // por defecto de Flyway y no la usamos.)
            assertThat(salida.getAll())
                    .doesNotContain("Unable to resolve location classpath:db/h2")
                    .doesNotContain("Unable to resolve location classpath:db/migration");
            assertThat(salida.getAll().lines()
                    .filter(linea -> linea.contains("WARN") && linea.toLowerCase().contains("location")))
                    .isEmpty();
        }
    }
}
