package com.ganera.core.ovz;

import com.ganera.core.GaneraApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduce el fallo de arranque del WORKER en Clever (perfiles {@code clever,worker}) y lo fija como regresion.
 *
 * <p><b>Causa (diagnostico):</b> el worker corre con {@code web-application-type: none} (no sirve HTTP de la
 * aplicacion), pero {@link com.ganera.core.shared.security.SecurityConfig} lleva {@code @EnableWebSecurity} y se
 * cargaba igual, construyendo el {@code securityFilterChain} y, con el, el bean {@code corsConfigurationSource}.
 * Ese bean resuelve {@code @Value("${ganera.frontend.origen}")}, que en el perfil {@code clever} vale
 * {@code ${FRONTEND_ORIGEN}} SIN valor por defecto. El worker no define {@code FRONTEND_ORIGEN} (ni debe), asi que
 * el placeholder no resuelve y el contexto falla con {@code UnsatisfiedDependencyException} al crear
 * {@code corsConfigurationSource} (justo lo que muestra el log de Clever), y Clever reporta «not listening on 8080»
 * porque el contexto nunca termina de arrancar.
 *
 * <p>Este test arranca el contexto con <b>exactamente</b> el conjunto de variables del worker en Clever (secretos
 * de prueba del application.yml de test, base de datos H2 de test) y <b>sin</b> {@code FRONTEND_ORIGEN},
 * {@code TWILIO_*}, {@code ANTHROPIC_API_KEY}, {@code AI_PROVIDER}, {@code ONBOARDING_SECRET} ni
 * {@code SUPABASE_FLYWAY_*}. Reproduce el perfil de produccion del worker (los YAML reales de {@code clever} y
 * {@code worker} se cargan; solo se sustituye la base de datos por H2 de test). El worker no debe levantar ninguna
 * superficie web: ni Tomcat, ni {@code DispatcherServlet}, ni {@code SecurityFilterChain}.
 */
class PerfilCleverWorkerArranqueTest {

    private static SpringApplicationBuilder worker() {
        return new SpringApplicationBuilder(GaneraApplication.class)
                // El perfil real del worker en Clever. `clever` aporta Supabase/SSL/CORS/HSTS; `worker` quita el web.
                .profiles("clever", "worker");
    }

    private static String[] argumentos(String nombreBd) {
        return new String[] {
                // Base de datos de test (H2): sustituye a Supabase. Al venir por command-line, gana sobre el YAML de
                // `clever` (jdbc:postgresql://${SUPABASE_*}) y ConexionBaseDeDatosEstricta ve un literal sin
                // placeholders, asi que no exige las variables SUPABASE_*. Flyway va apagado en `worker`.
                "--spring.datasource.url=jdbc:h2:mem:" + nombreBd + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.datasource.hikari.pool-name=" + nombreBd,
                "--spring.jmx.enabled=false",
                // El servidor de salud del worker enlaza un puerto; con PORT=0 usa uno efimero y no choca con el 8080.
                "--PORT=0"
        };
    }

    @Test
    void elWorkerArrancaEnPerfilCleverWorkerSinLasVariablesDelBackendWeb() {
        try (ConfigurableApplicationContext contexto =
                     worker().run(argumentos("clever_worker_arranque"))) {

            // El contexto arranca (antes del arreglo, fallaba al crear corsConfigurationSource por FRONTEND_ORIGEN).
            assertThat(contexto.isActive()).isTrue();

            // El dialecto Postgres EXPLICITO del perfil worker esta aplicado (evita que Hibernate abra una conexion
            // al arrancar para autodetectarlo).
            assertThat(contexto.getEnvironment().getProperty("spring.jpa.database-platform"))
                    .isEqualTo("org.hibernate.dialect.PostgreSQLDialect");

            // Sigue siendo un worker sin web: ni contexto web, ni DispatcherServlet, ni Tomcat.
            assertThat(contexto).isNotInstanceOf(WebApplicationContext.class);
            assertThat(contexto.getBeanNamesForType(DispatcherServlet.class)).isEmpty();

            // La cadena de seguridad web y el CORS NO se cargan en el worker (son del backend web).
            assertThat(contexto.getBeanNamesForType(SecurityFilterChain.class))
                    .as("SecurityFilterChain no se carga en el worker").isEmpty();
            assertThat(contexto.containsBean("corsConfigurationSource"))
                    .as("corsConfigurationSource no se carga en el worker").isFalse();

            // El worker sigue haciendo «lo suyo»: su planificador y servicio estan presentes.
            assertThat(contexto.containsBean("workerOvzScheduler"))
                    .as("el planificador del worker si se carga").isTrue();
            assertThat(contexto.getBean(ServidorSaludWorker.class).puerto()).isGreaterThan(0);
        }
    }

    /**
     * El worker arranca aunque la base de datos NO responda en el arranque (Supabase Free pausada/en frio): con el
     * dialecto Postgres EXPLICITO del perfil worker, Hibernate construye el {@code EntityManagerFactory} SIN abrir
     * conexion para autodetectar el dialecto por los metadatos JDBC. Aqui el datasource apunta a un Postgres
     * INALCANZABLE (puerto 1, conexion rechazada al instante); si el arranque abriera una conexion, el contexto no
     * levantaria. La primera conexion la haria perezosamente el planificador en su primera consulta (que aqui no
     * corre). Antes del arreglo esto fallaba de forma intermitente con «Unable to determine Dialect without JDBC
     * metadata».
     */
    @Test
    void elWorkerArrancaConDialectoExplicitoAunqueLaBaseDeDatosNoResponda() {
        try (ConfigurableApplicationContext contexto =
                     worker().run(argumentosPostgresInalcanzable())) {

            assertThat(contexto.isActive())
                    .as("el contexto del worker arranca sin conexion viva a la base de datos").isTrue();
            assertThat(contexto.getEnvironment().getProperty("spring.jpa.database-platform"))
                    .isEqualTo("org.hibernate.dialect.PostgreSQLDialect");
            // Se cargo el grafo del worker aunque la BD no responde: la conexion es perezosa.
            assertThat(contexto.containsBean("workerOvzScheduler")).isTrue();
        }
    }

    private static String[] argumentosPostgresInalcanzable() {
        return new String[] {
                // Postgres INALCANZABLE a proposito (puerto 1 -> conexion rechazada al instante). Literal sin
                // placeholders, asi que ConexionBaseDeDatosEstricta no exige SUPABASE_*. Si el arranque intentara
                // conectar, fallaria aqui; que el contexto quede activo prueba que NO hay conexion viva al arrancar.
                "--spring.datasource.url=jdbc:postgresql://localhost:1/ganera_worker_frio",
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.datasource.username=ganera_app",
                "--spring.datasource.password=irrelevante",
                "--spring.datasource.hikari.pool-name=worker_frio",
                "--spring.jmx.enabled=false",
                "--PORT=0"
        };
    }
}
