package com.ganera.core.migracion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migracion a Supabase, T3: fija la configuracion REAL de la base de datos del perfil clever
 * (src/main/resources/application-clever.yml, leido del disco como en ConfiguracionSeguridadProduccionTest). SSL con
 * verify-full y la CA del classpath, el esquema ganera, nada de detalle del servidor en los errores, Hikari a 5, Flyway
 * con su propio usuario sobre un esquema que no crea, y todas las variables obligatorias (sin valor por defecto).
 */
class ConfiguracionBaseDeDatosProduccionTest {

    private static final Path FICHERO = Path.of("src/main/resources/application-clever.yml");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]*)}");

    private static EnumerablePropertySource<?> perfilClever() throws IOException {
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader()
                .load("application-clever.yml", new FileSystemResource(FICHERO));
        assertThat(fuentes).hasSize(1);
        return (EnumerablePropertySource<?>) fuentes.get(0);
    }

    private static String valor(String propiedad) throws IOException {
        Object valor = perfilClever().getProperty(propiedad);
        assertThat(valor).as(propiedad).isNotNull();
        return String.valueOf(valor);
    }

    @Test
    void laUrlExigeSslVerifyFullConLaCaDelClasspathElEsquemaGaneraYSinDetalleDelServidor() throws IOException {
        String url = valor("spring.datasource.url");

        assertThat(url).startsWith(
                "jdbc:postgresql://${SUPABASE_DB_HOST}:${SUPABASE_DB_PORT}/${SUPABASE_DB_NAME}?");
        Map<String, String> parametros = Arrays.stream(url.substring(url.indexOf('?') + 1).split("&"))
                .map(p -> p.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> p.length > 1 ? p[1] : ""));
        assertThat(parametros).containsExactlyInAnyOrderEntriesOf(Map.of(
                "sslmode", "verify-full",
                "sslfactory", "org.postgresql.ssl.SingleCertValidatingFactory",
                "sslfactoryarg", "classpath:supabase/ca-raiz.crt",
                "currentSchema", "ganera",
                "logServerErrorDetail", "false"));
    }

    @Test
    void laAppConectaConSusVariablesYUnPoolDeCinco() throws IOException {
        assertThat(valor("spring.datasource.username")).isEqualTo("${SUPABASE_DB_USER}");
        assertThat(valor("spring.datasource.password")).isEqualTo("${SUPABASE_DB_PASSWORD}");
        assertThat(valor("spring.datasource.driver-class-name")).isEqualTo("org.postgresql.Driver");
        assertThat(valor("spring.datasource.hikari.maximum-pool-size")).isEqualTo("5");
    }

    @Test
    void flywayUsaSuPropioUsuarioYUnEsquemaQueNoCrea() throws IOException {
        assertThat(valor("spring.flyway.user")).isEqualTo("${SUPABASE_FLYWAY_USER}");
        assertThat(valor("spring.flyway.password")).isEqualTo("${SUPABASE_FLYWAY_PASSWORD}");
        assertThat(valor("spring.flyway.default-schema")).isEqualTo("ganera");
        assertThat(valor("spring.flyway.schemas")).isEqualTo("ganera");
        assertThat(valor("spring.flyway.create-schemas")).isEqualTo("false");
        // Flyway sin URL propia: Boot deriva su conexion (sin pool) de la del datasource, con SSL incluido.
        assertThat(perfilClever().getProperty("spring.flyway.url")).isNull();
    }

    @Test
    void ningunaVariableDeBaseDeDatosTieneValorPorDefectoNiQuedaNadaDelAddOn() throws IOException {
        EnumerablePropertySource<?> perfil = perfilClever();
        List<String> deBaseDeDatos = Arrays.stream(perfil.getPropertyNames())
                .filter(n -> n.startsWith("spring.datasource.") || n.startsWith("spring.flyway."))
                .toList();
        assertThat(deBaseDeDatos).isNotEmpty();
        for (String nombre : deBaseDeDatos) {
            Matcher m = PLACEHOLDER.matcher(String.valueOf(perfil.getProperty(nombre)));
            while (m.find()) {
                assertThat(m.group(1)).as(nombre + " sin valor por defecto").doesNotContain(":");
            }
        }
        assertThat(Files.readString(FICHERO, StandardCharsets.UTF_8)).doesNotContain("POSTGRESQL_ADDON_");
    }
}
