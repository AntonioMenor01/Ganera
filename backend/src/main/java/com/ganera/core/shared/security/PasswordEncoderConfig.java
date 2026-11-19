package com.ganera.core.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * El {@link PasswordEncoder} NO es infraestructura web: lo usan servicios de negocio (AuthService,
 * RegistroGestoriaService, OnboardingController, DemoSemillaService), no la cadena de filtros de seguridad. Por eso
 * vive aqui y NO en {@link SecurityConfig} (que si es web y se excluye del WORKER de OVZ con
 * {@code @ConditionalOnWebApplication}). Asi el worker, que carga el mismo grafo de servicios que el backend pero sin
 * servidor web, sigue teniendo este bean disponible. En el backend el comportamiento es identico a antes: el mismo
 * {@code BCryptPasswordEncoder}, solo cambia la clase @Configuration que lo define. Ver PerfilCleverWorkerArranqueTest.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
