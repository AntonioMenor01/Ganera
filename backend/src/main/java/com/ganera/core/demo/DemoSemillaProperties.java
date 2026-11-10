package com.ganera.core.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Semilla de datos inventados para demos locales. Fail-closed: por defecto no siembra nada.
 * Solo {@code ganera.demo.sembrar=true} (perfil {@code demo}) la activa.
 */
@ConfigurationProperties(prefix = "ganera.demo")
public class DemoSemillaProperties {

    /** Si true, al arrancar se siembra la gestoría demo (idempotente). */
    private boolean sembrar = false;

    public boolean isSembrar() {
        return sembrar;
    }

    public void setSembrar(boolean sembrar) {
        this.sembrar = sembrar;
    }
}
