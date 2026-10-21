package com.ganera.core.migracion;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migracion a Supabase, T1: las ubicaciones de Flyway de los DOS application.yml (el de src/main/resources y el de
 * src/test/resources, que lo tapa en el classpath de los tests) incluyen db/{vendor}, donde van las migraciones y
 * callbacks solo-Postgres. Se leen del disco a proposito (mismo patron que ConfiguracionSeguridadProduccionTest).
 */
public class ConfiguracionFlywayTest {

    @ParameterizedTest
    @ValueSource(strings = {"src/main/resources/application.yml", "src/test/resources/application.yml"})
    void lasUbicacionesDeFlywaySonLasComunesYLasDelMotor(String fichero) throws IOException {
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader()
                .load(fichero, new FileSystemResource(fichero));
        assertThat(fuentes).isNotEmpty();

        assertThat(ubicaciones(fuentes.get(0)))
                .containsExactly("classpath:db/migration", "classpath:db/{vendor}");
    }

    /**
     * Migracion a Supabase, T2: sin fail-on-missing-locations, si db/postgresql no llegara al jar, Flyway 10 solo lo
     * diria en DEBUG y el callback de seguridad (RLS y permisos) dejaria de ejecutarse en silencio (hallazgo de T1).
     */
    @ParameterizedTest
    @ValueSource(strings = {"src/main/resources/application.yml", "src/test/resources/application.yml"})
    void flywayFallaSiFaltaUnaUbicacion(String fichero) throws IOException {
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader()
                .load(fichero, new FileSystemResource(fichero));
        assertThat(fuentes).isNotEmpty();

        assertThat(fuentes.get(0).getProperty("spring.flyway.fail-on-missing-locations")).isEqualTo(true);
    }

    /** Lee spring.flyway.locations de un application.yml (tambien lo usa el test del embebido). */
    public static List<String> ubicaciones(String fichero) throws IOException {
        return ubicaciones(new YamlPropertySourceLoader().load(fichero, new FileSystemResource(fichero)).get(0));
    }

    /** Admite tanto una cadena separada por comas como una lista YAML. */
    private static List<String> ubicaciones(PropertySource<?> fuente) {
        List<String> resultado = new ArrayList<>();
        Object unaCadena = fuente.getProperty("spring.flyway.locations");
        if (unaCadena != null) {
            Arrays.stream(String.valueOf(unaCadena).split(",")).map(String::trim).forEach(resultado::add);
        }
        for (int i = 0; fuente.getProperty("spring.flyway.locations[" + i + "]") != null; i++) {
            resultado.add(String.valueOf(fuente.getProperty("spring.flyway.locations[" + i + "]")).trim());
        }
        return resultado;
    }
}
