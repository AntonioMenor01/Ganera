package com.ganera.core.registro;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra {@link RegistroCerradoFilter} solo para /gestorias/registro y con la maxima prioridad (salvo
 * ContextoPeticionFilter, que pone X-Request-Id y va antes),
 * por delante de la cadena de Spring Security (orden -100): asi el 404 del registro cerrado es el
 * mismo con o sin JWT, y con un JWT invalido.
 *
 * <p>{@code ganera.registro.abierto} es fail-closed (D4): {@code false} en application.yml y
 * {@code false} tambien aqui si la propiedad no existe. Solo la abren los tests que la necesitan.
 */
// Solo en apps web (servlet): registra un filtro de servlet. El backend siempre es web (identico a hoy); el WORKER de
// OVZ (web-application-type: none) no tiene cadena de filtros. Ver PerfilCleverWorkerArranqueTest.
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
class RegistroConfig {

    static final String RUTA_REGISTRO = "/gestorias/registro";

    @Bean
    FilterRegistrationBean<RegistroCerradoFilter> registroCerradoFilter(
            @Value("${ganera.registro.abierto:false}") boolean registroAbierto) {
        FilterRegistrationBean<RegistroCerradoFilter> registro =
                new FilterRegistrationBean<>(new RegistroCerradoFilter(registroAbierto));
        registro.addUrlPatterns(RUTA_REGISTRO);
        // + 1: justo detras de ContextoPeticionFilter (auditoria), que va el primero para poner X-Request-Id.
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registro;
    }
}
