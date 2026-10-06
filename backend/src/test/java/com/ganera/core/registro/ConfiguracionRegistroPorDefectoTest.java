package com.ganera.core.registro;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt C, T2 (D4): fija que la configuracion REAL de la aplicacion (src/main/resources, no la de
 * test, que la tapa en el classpath de los tests) deja el registro publico cerrado, y que el
 * perfil clever no lo abre. Se leen los ficheros del disco a proposito: en el classpath de test
 * application.yml es el de src/test/resources.
 */
class ConfiguracionRegistroPorDefectoTest {

    private static Object valor(String fichero, String propiedad) throws IOException {
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader()
                .load(fichero, new FileSystemResource("src/main/resources/" + fichero));
        assertThat(fuentes).isNotEmpty();
        return fuentes.get(0).getProperty(propiedad);
    }

    @Test
    void elApplicationYmlRealDejaElRegistroCerrado() throws IOException {
        assertThat(String.valueOf(valor("application.yml", "ganera.registro.abierto"))).isEqualTo("false");
    }

    @Test
    void elPerfilCleverNoAbreElRegistro() throws IOException {
        Object enClever = valor("application-clever.yml", "ganera.registro.abierto");
        assertThat(enClever == null || "false".equals(String.valueOf(enClever))).isTrue();
    }
}
