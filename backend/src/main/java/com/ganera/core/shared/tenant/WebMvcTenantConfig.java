package com.ganera.core.shared.tenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcTenantConfig implements WebMvcConfigurer {

    private final TenantFilterActivationInterceptor tenantFilterActivationInterceptor;

    public WebMvcTenantConfig(TenantFilterActivationInterceptor tenantFilterActivationInterceptor) {
        this.tenantFilterActivationInterceptor = tenantFilterActivationInterceptor;
    }

    /**
     * order(LOWEST_PRECEDENCE) es obligatorio: Spring Boot registra su propio
     * OpenEntityManagerInViewInterceptor (spring.jpa.open-in-view=true) como otro
     * HandlerInterceptor de MVC, y sin un orden explicito el orden relativo entre
     * dos WebMvcConfigurer distintos no esta garantizado. Si este interceptor corre
     * ANTES de que OSIV ate el EntityManager real de la request al hilo, el
     * EntityManager compartido inyectado aqui crea uno temporal y no transaccional
     * solo para esa llamada -- enableFilter() se aplica a una Session que se
     * descarta al momento, sin ningun efecto sobre las queries reales que ejecuta
     * el controller despues. Sintoma exacto que esto causaba: gestoriaFilter nunca
     * se aplicaba de verdad en real HTTP (aunque el test unitario del interceptor
     * pasaba, porque invoca preHandle directamente dentro de la misma transaccion
     * de test) -- GET /explotaciones y GET /tramites devolvian filas de CUALQUIER
     * Gestoria, no solo la autenticada. Encontrado sembrando dos Gestorias reales
     * con datos solapados y comparando via HTTP real -- ningun test previo (todos
     * unitarios, sin passar por el interceptor real) lo habia ejercitado.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantFilterActivationInterceptor).order(Ordered.LOWEST_PRECEDENCE);
    }
}
