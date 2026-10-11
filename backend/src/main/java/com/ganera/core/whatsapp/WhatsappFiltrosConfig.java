package com.ganera.core.whatsapp;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra el tope de tamano del callback de estado (B2 reducido, T4) SOLO en su ruta exacta y con el orden mas
 * alto: corre antes de Spring Security (orden -100) y de cualquier filtro que pudiera leer el cuerpo.
 */
@Configuration
public class WhatsappFiltrosConfig {

    @Bean
    public FilterRegistrationBean<LimiteCuerpoCallbackEstadoFilter> limiteCuerpoCallbackEstado() {
        FilterRegistrationBean<LimiteCuerpoCallbackEstadoFilter> registro =
                new FilterRegistrationBean<>(new LimiteCuerpoCallbackEstadoFilter());
        registro.addUrlPatterns(LimiteCuerpoCallbackEstadoFilter.RUTA);
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registro.setName("limiteCuerpoCallbackEstado");
        return registro;
    }
}
