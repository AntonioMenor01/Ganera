package com.ganera.core.tramite;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Revision m2 de B1 T2: los @Scheduled no comparten un solo hilo (el de Spring Boot por defecto), para
 * que una IA lenta no retrase SuscripcionSyncScheduler ni el job de retencion. Se lee el
 * application.yml de PRODUCCION del disco (en los tests, el de src/test/resources lo tapa en el
 * classpath) y se comprueba que Spring Boot crea el TaskScheduler con ese tamano.
 */
class PoolPlanificadorConfigTest {

    @Configuration
    @EnableScheduling
    static class ConPlanificacion {
    }

    @Test
    void elApplicationYmlDeProduccionDaTresHilosAlPlanificador() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application.yml"));
        Properties propiedades = yaml.getObject();
        String tamano = propiedades.getProperty("spring.task.scheduling.pool.size");
        assertThat(tamano).isEqualTo("3");

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(ConPlanificacion.class)
                .withPropertyValues("spring.task.scheduling.pool.size=" + tamano)
                .run(contexto -> assertThat(contexto.getBean(ThreadPoolTaskScheduler.class)
                        .getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(3));
    }
}
