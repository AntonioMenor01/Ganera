package com.ganera.core.shared.basedatos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fail-closed de las propiedades de conexion a la base de datos (migracion a Supabase, T3): si una variable de la
 * que dependen falta, o esta definida pero vacia, la app no arranca, y lo hace ANTES de crear ningun bean (sin
 * intentar conectar). La comprobacion de blanco es por cada variable referenciada (revision I1 de T3).
 *
 * <p>Por que hace falta: al enlazar {@code spring.datasource.*} y {@code spring.flyway.*}, el {@code Binder} de Boot
 * resuelve los placeholders en modo permisivo y una variable que falta deja el texto literal
 * {@code ${SUPABASE_DB_PASSWORD}} como valor. Con el host o el usuario la conexion falla por casualidad, pero con una
 * contrasena un servidor que no la pida (autenticacion {@code trust}) la aceptaria y la app arrancaria. El fail-closed
 * no puede depender de como autentique el servidor.
 *
 * <p>Corre despues de cargar los YAML (orden mas bajo) y no depende del perfil: con los valores por defecto de
 * application.yml ({@code ${DATABASE_URL:...}}) o con literales (el {@code password:} vacio de H2 en los tests) no
 * cambia nada. Los mensajes solo nombran propiedades y variables, nunca valores.
 */
public class ConexionBaseDeDatosEstricta implements EnvironmentPostProcessor, Ordered {

    static final List<String> PROPIEDADES = List.of("spring.datasource.url", "spring.datasource.username",
            "spring.datasource.password", "spring.flyway.url", "spring.flyway.user", "spring.flyway.password");

    /** El nombre de la variable de un placeholder, sin su valor por defecto. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        for (String propiedad : PROPIEDADES) {
            if (!environment.containsProperty(propiedad)) {
                continue;
            }
            try {
                // Modo estricto: el Environment lanza "Could not resolve placeholder 'X' in value ..." si falta X.
                environment.getProperty(propiedad);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("No se puede resolver la propiedad " + propiedad
                        + ": falta una variable de la que depende. La app no arranca.", e);
            }
            // Por VARIABLE, no por propiedad: la URL compone varias y nunca queda en blanco, pero con el host vacio
            // pgjdbc conectaria a localhost. Una variable sin definir con valor por defecto (${X:}) no cuenta.
            for (String variable : variables(valorEnBruto(environment, propiedad))) {
                if (environment.containsProperty(variable) && enBlanco(environment.getProperty(variable))) {
                    throw new IllegalStateException("La variable " + variable + " (de la propiedad " + propiedad
                            + ") esta definida pero en blanco. La app no arranca.");
                }
            }
        }
    }

    private static boolean enBlanco(String valor) {
        return valor == null || valor.isBlank();
    }

    /** El valor sin resolver de la primera fuente que define la propiedad (la que gana). */
    private static String valorEnBruto(ConfigurableEnvironment environment, String propiedad) {
        for (PropertySource<?> fuente : environment.getPropertySources()) {
            if (fuente.containsProperty(propiedad)) {
                Object valor = fuente.getProperty(propiedad);
                return valor == null ? null : String.valueOf(valor);
            }
        }
        return null;
    }

    private static List<String> variables(String enBruto) {
        List<String> variables = new ArrayList<>();
        if (enBruto != null) {
            Matcher m = PLACEHOLDER.matcher(enBruto);
            while (m.find()) {
                variables.add(m.group(1));
            }
        }
        return variables;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
