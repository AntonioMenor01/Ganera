package com.ganera.core.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Arranca la semilla demo solo si {@code ganera.demo.sembrar=true}. En producción/Clever esa
 * propiedad no existe (default false): no se siembra nada.
 */
@Component
public class DemoSemillaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSemillaRunner.class);

    private final DemoSemillaProperties properties;
    private final DemoSemillaService semillaService;

    public DemoSemillaRunner(DemoSemillaProperties properties, DemoSemillaService semillaService) {
        this.properties = properties;
        this.semillaService = semillaService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSembrar()) {
            return;
        }
        DemoSemillaService.Resultado resultado = semillaService.sembrarSiHaceFalta();
        if (resultado.creada()) {
            log.warn("Semilla demo creada: email={} gestoriaId={} (solo para demos locales)",
                    DemoSemillaService.EMAIL_DEMO, resultado.gestoriaId());
        } else {
            log.info("Semilla demo ya existía (email={}): no se ha vuelto a sembrar",
                    DemoSemillaService.EMAIL_DEMO);
        }
    }
}
