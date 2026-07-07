package com.ganera.core.shared.tenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcTenantConfig implements WebMvcConfigurer {

    private final TenantFilterActivationInterceptor tenantFilterActivationInterceptor;

    public WebMvcTenantConfig(TenantFilterActivationInterceptor tenantFilterActivationInterceptor) {
        this.tenantFilterActivationInterceptor = tenantFilterActivationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantFilterActivationInterceptor);
    }
}
