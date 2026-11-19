package com.ganera.core.shared.auditoria;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
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
// Solo en apps web (servlet): registra un filtro de servlet. El backend siempre es web (identico a hoy); el WORKER de
// OVZ (web-application-type: none) no tiene cadena de filtros. Ver PerfilCleverWorkerArranqueTest.
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
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
