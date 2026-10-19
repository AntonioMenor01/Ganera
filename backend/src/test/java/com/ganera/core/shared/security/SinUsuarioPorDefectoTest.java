package com.ganera.core.shared.security;

import com.ganera.core.GaneraApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Despliegue en Clever, T1: Spring Boot no crea su usuario por defecto. Sin ningun bean de
 * autenticacion propio ({@code UserDetailsService}, {@code AuthenticationProvider}, {@code AuthenticationManager}...),
 * su {@code UserDetailsServiceAutoConfiguration} creaba un {@code InMemoryUserDetailsManager} con un usuario {@code user} y una contrasena aleatoria que
 * imprimia en el log de arranque («Using generated security password: …»). La app no lo usa (JWT, sin
 * {@code httpBasic} ni {@code formLogin}), pero dejaba una contrasena en los logs y un usuario que no deberia
 * existir.
 *
 * <p>El contexto es propio de este test (SpringApplicationBuilder) y no el que cachea {@code @SpringBootTest}:
 * con un contexto reutilizado de otra clase, el log de arranque ya habria salido antes de capturarlo y el test
 * pasaria aunque el usuario existiera.
 */
@ExtendWith(OutputCaptureExtension.class)
class SinUsuarioPorDefectoTest {

    @Test
    void elArranqueNoCreaUsuarioPorDefectoNiImprimeSuContrasena(CapturedOutput salida) {
        try (ConfigurableApplicationContext contexto = new SpringApplicationBuilder(GaneraApplication.class).run(
                "--spring.datasource.url=jdbc:h2:mem:sin_usuario_por_defecto;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                "--spring.datasource.hikari.pool-name=sin-usuario-por-defecto",
                "--server.port=0",
                "--spring.jmx.enabled=false")) {

            assertThat(contexto.getBeanNamesForType(UserDetailsService.class)).isEmpty();
            // Comprobacion de que de verdad se ha capturado el arranque completo (Tomcat arranca al final del refresh,
            // despues de crear los beans); si no, la siguiente no probaria nada.
            assertThat(salida.getOut()).contains("Tomcat started on port");
            assertThat(salida.getAll()).doesNotContain("Using generated security password");
        }
    }
}
