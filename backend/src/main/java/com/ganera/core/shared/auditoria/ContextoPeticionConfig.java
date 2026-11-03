package com.ganera.core.shared.auditoria;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra {@link ContextoPeticionFilter} en todas las rutas y con la MAXIMA prioridad: corre antes que
 * RegistroCerradoFilter y LimiteCuerpoCallbackEstadoFilter (que van en {@code HIGHEST_PRECEDENCE + 1}) y que la
 * cadena de Spring Security (-100), asi que X-Request-Id sale tambien en las respuestas que ellos cortan.
 *
 * <p>{@code ganera.auditoria.ip-desde-x-forwarded-for} es fail-closed: {@code false} si la propiedad no existe; solo
 * {@code application-clever.yml} la pone a {@code true} (D5).
 */
@Configuration
class ContextoPeticionConfig {

    @Bean
    FilterRegistrationBean<ContextoPeticionFilter> contextoPeticionFilter(
            @Value("${ganera.auditoria.ip-desde-x-forwarded-for:false}") boolean ipDesdeXForwardedFor) {
        FilterRegistrationBean<ContextoPeticionFilter> registro =
                new FilterRegistrationBean<>(new ContextoPeticionFilter(ipDesdeXForwardedFor));
        registro.addUrlPatterns("/*");
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registro.setName("contextoPeticion");
        return registro;
    }
}
