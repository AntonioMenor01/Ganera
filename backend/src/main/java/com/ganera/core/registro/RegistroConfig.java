package com.ganera.core.registro;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra {@link RegistroCerradoFilter} solo para /gestorias/registro y con la maxima prioridad,
 * por delante de la cadena de Spring Security (orden -100): asi el 404 del registro cerrado es el
 * mismo con o sin JWT, y con un JWT invalido.
 *
 * <p>{@code ganera.registro.abierto} es fail-closed (D4): {@code false} en application.yml y
 * {@code false} tambien aqui si la propiedad no existe. Solo la abren los tests que la necesitan.
 */
@Configuration
class RegistroConfig {

    static final String RUTA_REGISTRO = "/gestorias/registro";

    @Bean
    FilterRegistrationBean<RegistroCerradoFilter> registroCerradoFilter(
            @Value("${ganera.registro.abierto:false}") boolean registroAbierto) {
        FilterRegistrationBean<RegistroCerradoFilter> registro =
                new FilterRegistrationBean<>(new RegistroCerradoFilter(registroAbierto));
        registro.addUrlPatterns(RUTA_REGISTRO);
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }
}
