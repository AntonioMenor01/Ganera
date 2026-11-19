package com.ganera.core.ovz;

import com.ganera.core.GaneraApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Perfil del WORKER de OVZ (escalon 2, presentacion automatica de la baja; plan 2026-11-17, ajuste de Antonio):
 * el worker corre con la MISMA base de codigo que el backend pero <b>sin servidor web</b> (no expone ningun
 * endpoint HTTP) y con el planificador del worker activado, mientras que en el backend ese planificador esta
 * DESACTIVADO.
 *
 * <p>Dos comprobaciones:
 * <ul>
 *   <li><b>Desde disco</b> (como {@code ProveedorIaPorDefectoProduccionTest}): el application.yml real deja el
 *       worker a false (apagado en el backend) y application-worker.yml pone {@code web-application-type: none}
 *       y el worker a true. Barato, sin contexto; mutar cualquiera de esos valores pone el test en rojo.</li>
 *   <li><b>Arrancando de verdad</b> en el perfil {@code worker} (como {@code SinUsuarioPorDefectoTest}, con su
 *       propio contexto para capturar el arranque completo): el contexto NO es web, no hay {@code DispatcherServlet}
 *       y no arranca Tomcat; el flag del worker resuelve a true.</li>
 * </ul>
 */
@ExtendWith(OutputCaptureExtension.class)
class PerfilWorkerTest {

    private static Object valor(String fichero, String propiedad) throws IOException {
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader()
                .load(fichero, new FileSystemResource("src/main/resources/" + fichero));
        assertThat(fuentes).as("el yaml %s no se pudo leer", fichero).isNotEmpty();
        return fuentes.get(0).getProperty(propiedad);
    }

    @Test
    void elBackendTieneElPlanificadorDelWorkerDesactivado() throws IOException {
        assertThat(String.valueOf(valor("application.yml", "ganera.ovz.worker.activo"))).isEqualTo("false");
    }

    @Test
    void elPerfilWorkerNoTieneServidorWebYActivaElWorker() throws IOException {
        assertThat(String.valueOf(valor("application-worker.yml", "spring.main.web-application-type")))
                .isEqualTo("none");
        assertThat(String.valueOf(valor("application-worker.yml", "ganera.ovz.worker.activo"))).isEqualTo("true");
        // «solo el planificador»: los planificadores propios del backend se apagan en el worker.
        assertThat(String.valueOf(valor("application-worker.yml", "ganera.extraccion.planificador.activo")))
                .isEqualTo("false");
        assertThat(String.valueOf(valor("application-worker.yml", "ganera.retencion.activo"))).isEqualTo("false");
        assertThat(String.valueOf(valor("application-worker.yml", "ganera.facturacion.sync.activo")))
                .isEqualTo("false");
        // El worker NO migra: Flyway apagado (la BD la migra solo el backend; el worker no necesita las
        // credenciales de Flyway, solo las de la aplicacion).
        assertThat(String.valueOf(valor("application-worker.yml", "spring.flyway.enabled"))).isEqualTo("false");
    }

    @Test
    void elArranqueEnPerfilWorkerNoLevantaServidorWeb(CapturedOutput salida) {
        // Contexto propio (no el cacheado por @SpringBootTest): asi se captura el arranque entero y se prueba el
        // tipo de aplicacion real, no solo la config. Hereda el application.yml de test (H2 + secretos) y le suma
        // application-worker.yml (web-application-type: none, worker a true).
        try (ConfigurableApplicationContext contexto = new SpringApplicationBuilder(GaneraApplication.class).run(
                "--spring.profiles.active=worker",
                "--spring.datasource.url=jdbc:h2:mem:perfil_worker;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                "--spring.datasource.hikari.pool-name=perfil-worker",
                "--spring.jmx.enabled=false")) {

            assertThat(contexto).isNotInstanceOf(WebApplicationContext.class);
            assertThat(contexto.getBeanNamesForType(DispatcherServlet.class)).isEmpty();
            assertThat(contexto.getEnvironment().getProperty("ganera.ovz.worker.activo")).isEqualTo("true");
            assertThat(salida.getAll()).doesNotContain("Tomcat started on port");
            // «solo el planificador»: en el worker no corre ninguno de los planificadores del backend. El de
            // Stripe se instanciaria por matchIfMissing=true si el perfil worker no lo apagara, asi que esta
            // asercion prueba de verdad su apagado (no solo que la propiedad existe).
            assertThat(contexto.containsBean("suscripcionSyncScheduler"))
                    .as("reconciliacion de Stripe apagada en el worker").isFalse();
            assertThat(contexto.containsBean("extraccionTramiteScheduler"))
                    .as("extraccion por IA apagada en el worker").isFalse();
            assertThat(contexto.containsBean("retencionMensajesScheduler"))
                    .as("retencion de WhatsApp apagada en el worker").isFalse();
            // El worker no migra: con spring.flyway.enabled=false Boot no crea el bean Flyway. Asi el worker no
            // necesita SUPABASE_FLYWAY_USER/_PASSWORD (ConexionBaseDeDatosEstricta tampoco las exige).
            assertThat(contexto.getBeanNamesForType(org.flywaydb.core.Flyway.class))
                    .as("Flyway apagado en el worker (migra solo el backend)").isEmpty();
        }
    }
}
