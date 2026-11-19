package com.ganera.core.whatsapp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra el tope de tamano del callback de estado (B2 reducido, T4) SOLO en su ruta exacta y con el orden mas
 * alto: corre antes de Spring Security (orden -100) y de cualquier filtro que pudiera leer el cuerpo.
 */
// Solo en apps web (servlet): registra un filtro de servlet. El backend siempre es web (identico a hoy); el WORKER de
// OVZ (web-application-type: none) no tiene cadena de filtros. Ver PerfilCleverWorkerArranqueTest.
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class WhatsappFiltrosConfig {

    @Bean
    public FilterRegistrationBean<LimiteCuerpoCallbackEstadoFilter> limiteCuerpoCallbackEstado() {
        FilterRegistrationBean<LimiteCuerpoCallbackEstadoFilter> registro =
                new FilterRegistrationBean<>(new LimiteCuerpoCallbackEstadoFilter());
        registro.addUrlPatterns(LimiteCuerpoCallbackEstadoFilter.RUTA);
        // + 1: justo detras de ContextoPeticionFilter (auditoria), que va el primero para poner X-Request-Id.
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registro.setName("limiteCuerpoCallbackEstado");
        return registro;
    }
}
