package com.ganera.core.shared.basedatos;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migracion a Supabase, T3: las propiedades de conexion se resuelven en modo estricto antes de crear ningun bean. Sin
 * Spring Boot ni base de datos: un {@link StandardEnvironment} con un {@link MapPropertySource}.
 */
class ConexionBaseDeDatosEstrictaTest {

    private static final String VALOR_SECRETO = "valor-secreto-que-no-debe-salir";

    private static StandardEnvironment entorno(Map<String, Object> propiedades) {
        StandardEnvironment entorno = new StandardEnvironment();
        // Sin las variables de entorno ni las propiedades del sistema reales: solo lo que pone el test.
        entorno.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        entorno.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        entorno.getPropertySources().addFirst(new MapPropertySource("prueba", propiedades));
        return entorno;
    }

    private static void comprobar(Map<String, Object> propiedades) {
        new ConexionBaseDeDatosEstricta().postProcessEnvironment(entorno(propiedades), null);
    }

    @Test
    void unaVariableQueFaltaNoArranca() {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put("spring.datasource.password", "${SUPABASE_DB_PASSWORD}");

        assertThatThrownBy(() -> comprobar(propiedades))
                .hasStackTraceContaining("Could not resolve placeholder 'SUPABASE_DB_PASSWORD'")
                .hasStackTraceContaining("spring.datasource.password");
    }

    @Test
    void unaVariableQueFaltaEnLaUrlNoArrancaYElMensajeNoLlevaElValorDeLasDemas() {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put("spring.datasource.url", "jdbc:postgresql://${HOST}:${PORT}/${NAME}?sslmode=verify-full");
        propiedades.put("HOST", VALOR_SECRETO);
        propiedades.put("PORT", "5432");

        assertThatThrownBy(() -> comprobar(propiedades))
                .hasStackTraceContaining("Could not resolve placeholder 'NAME'")
                .satisfies(e -> assertThat(trazaCompleta(e)).doesNotContain(VALOR_SECRETO));
    }

    @Test
    void unaVariableDefinidaPeroVaciaNoArrancaYElMensajeNombraLaPropiedadYLaVariable() {
        for (String propiedad : ConexionBaseDeDatosEstricta.PROPIEDADES) {
            Map<String, Object> propiedades = new HashMap<>();
            propiedades.put(propiedad, "${VARIABLE_VACIA}");
            propiedades.put("VARIABLE_VACIA", "  ");
            propiedades.put("OTRA", VALOR_SECRETO);

            assertThatThrownBy(() -> comprobar(propiedades)).as(propiedad)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(propiedad)
                    .hasMessageContaining("VARIABLE_VACIA")
                    .satisfies(e -> assertThat(trazaCompleta(e)).doesNotContain(VALOR_SECRETO));
        }
    }

    /**
     * Revision I1: la comprobacion es por VARIABLE, no por propiedad. Una URL compone varias variables y nunca queda en
     * blanco, pero con el host vacio pgjdbc conectaria a localhost, y con la base vacia, a la del nombre del usuario.
     */
    @Test
    void unaVariableVaciaDentroDeLaUrlNoArrancaYElMensajeNombraLaVariableYLaPropiedad() {
        for (String vacia : new String[] {"HOST", "NAME"}) {
            Map<String, Object> propiedades = new HashMap<>();
            propiedades.put("spring.datasource.url", "jdbc:postgresql://${HOST}:${PORT}/${NAME}?sslmode=verify-full");
            propiedades.put("HOST", VALOR_SECRETO);
            propiedades.put("PORT", "5432");
            propiedades.put("NAME", VALOR_SECRETO);
            propiedades.put(vacia, "");

            assertThatThrownBy(() -> comprobar(propiedades)).as(vacia)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("spring.datasource.url")
                    .hasMessageContaining(vacia)
                    .satisfies(e -> assertThat(trazaCompleta(e)).doesNotContain(VALOR_SECRETO));
        }
    }

    /** Definida y vacia es lo mismo que si faltara, aunque el placeholder tenga valor por defecto. */
    @Test
    void unaVariableDefinidaPeroVaciaConValorPorDefectoNoArranca() {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put("spring.datasource.password", "${DATABASE_PASSWORD:ganera}");
        propiedades.put("DATABASE_PASSWORD", "");

        assertThatThrownBy(() -> comprobar(propiedades))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATABASE_PASSWORD")
                .hasMessageContaining("spring.datasource.password");
    }

    /** {@code ${X:}} con X sin definir es un vacio buscado a proposito (por defecto vacio): no falla. */
    @Test
    void unValorPorDefectoVacioConLaVariableSinDefinirArranca() {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put("spring.datasource.password", "${X:}");

        assertThatCode(() -> comprobar(propiedades)).doesNotThrowAnyException();
    }

    @Test
    void conValorPorDefectoOValoresNormalesArranca() {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put("spring.datasource.url", "${DATABASE_URL:jdbc:postgresql://localhost:5432/ganera}");
        propiedades.put("spring.datasource.username", "${DATABASE_USERNAME:ganera}");
        propiedades.put("spring.datasource.password", "${SUPABASE_DB_PASSWORD}");
        propiedades.put("SUPABASE_DB_PASSWORD", VALOR_SECRETO);
        propiedades.put("spring.flyway.user", "propietario");

        assertThatCode(() -> comprobar(propiedades)).doesNotThrowAnyException();
    }

    /** El {@code password:} vacio de H2 en el application.yml de test: un literal vacio sin placeholder vale. */
    @Test
    void unLiteralVacioSinPlaceholderArranca() {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put("spring.datasource.password", "");
        propiedades.put("spring.flyway.password", "");

        assertThatCode(() -> comprobar(propiedades)).doesNotThrowAnyException();
    }

    @Test
    void sinNingunaPropiedadDeConexionArranca() {
        assertThatCode(() -> comprobar(Map.of("otra.cosa", "${NO_EXISTE}"))).doesNotThrowAnyException();
    }

    /**
     * Perfil worker (T6): con {@code spring.flyway.enabled=false} la BD la migra solo el backend, asi que el worker
     * NO necesita las variables de Flyway (SUPABASE_FLYWAY_USER/_PASSWORD). No se exigen cuando Flyway esta apagado,
     * aunque sus placeholders se queden sin resolver.
     */
    @Test
    void conFlywayApagadoNoSeExigenLasVariablesDeFlyway() {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put("spring.flyway.enabled", "false");
        propiedades.put("spring.datasource.url", "${DATABASE_URL:jdbc:postgresql://localhost:5432/ganera}");
        propiedades.put("spring.datasource.username", "${SUPABASE_DB_USER}");
        propiedades.put("spring.datasource.password", "${SUPABASE_DB_PASSWORD}");
        propiedades.put("SUPABASE_DB_USER", "app");
        propiedades.put("SUPABASE_DB_PASSWORD", VALOR_SECRETO);
        // Las de Flyway sin resolver: con Flyway apagado no deben hacer fallar el arranque.
        propiedades.put("spring.flyway.user", "${SUPABASE_FLYWAY_USER}");
        propiedades.put("spring.flyway.password", "${SUPABASE_FLYWAY_PASSWORD}");

        assertThatCode(() -> comprobar(propiedades)).doesNotThrowAnyException();
    }

    /** Pero las de la aplicacion se siguen exigiendo aunque Flyway este apagado. */
    @Test
    void conFlywayApagadoLasVariablesDeLaAppSeSiguenExigiendo() {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put("spring.flyway.enabled", "false");
        propiedades.put("spring.datasource.password", "${SUPABASE_DB_PASSWORD}");

        assertThatThrownBy(() -> comprobar(propiedades))
                .hasStackTraceContaining("Could not resolve placeholder 'SUPABASE_DB_PASSWORD'")
                .hasStackTraceContaining("spring.datasource.password");
    }

    /** Con Flyway encendido (por defecto, el backend) las variables de Flyway se exigen como siempre. */
    @Test
    void conFlywayEncendidoSeExigenLasVariablesDeFlyway() {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put("spring.flyway.user", "${SUPABASE_FLYWAY_USER}");

        assertThatThrownBy(() -> comprobar(propiedades))
                .hasStackTraceContaining("Could not resolve placeholder 'SUPABASE_FLYWAY_USER'")
                .hasStackTraceContaining("spring.flyway.user");
    }

    private static String trazaCompleta(Throwable e) {
        StringBuilder traza = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            traza.append(t).append('\n');
        }
        return traza.toString();
    }
}
