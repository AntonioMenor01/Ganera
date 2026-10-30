package com.ganera.core.shared.auditoria;

import jakarta.servlet.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Solo tests: un filtro DETRAS de ContextoPeticionFilter que, durante la peticion y en el hilo de Tomcat, anota lo que
 * hay en ContextoPeticion y emite una linea de log (para ver el requestId del MDC en la salida). Se importa con
 * {@code @Import(VigiaPeticion.class)}.
 */
@Slf4j
@TestConfiguration
class VigiaPeticion {

    static final String MENSAJE_LOG = "vigia-peticion";

    /** ContextoPeticion visto durante cada peticion (vacio si no habia). */
    static final Queue<ContextoPeticion.Datos> VISTOS = new ConcurrentLinkedQueue<>();

    @Bean
    FilterRegistrationBean<Filter> vigiaPeticion() {
        FilterRegistrationBean<Filter> registro = new FilterRegistrationBean<>((peticion, respuesta, cadena) -> {
            ContextoPeticion.actual().ifPresent(VISTOS::add);
            log.info(MENSAJE_LOG);
            cadena.doFilter(peticion, respuesta);
        });
        registro.addUrlPatterns("/*");
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        registro.setName("vigiaPeticion");
        return registro;
    }
}
