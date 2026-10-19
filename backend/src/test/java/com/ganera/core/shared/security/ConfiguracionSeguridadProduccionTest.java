package com.ganera.core.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Despliegue en Clever, T2 y T3: fija la configuracion REAL (src/main/resources) de HSTS y CORS. Se leen los
 * ficheros del disco a proposito: en el classpath de los tests, application.yml es el de src/test/resources y tapa
 * el de verdad (mismo patron que ConfiguracionRegistroPorDefectoTest).
 */
class ConfiguracionSeguridadProduccionTest {

    private static Object valor(String fichero, String propiedad) throws IOException {
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader()
                .load(fichero, new FileSystemResource("src/main/resources/" + fichero));
        assertThat(fuentes).isNotEmpty();
        return fuentes.get(0).getProperty(propiedad);
    }

    @Test
    void fueraDeProduccionNiHstsSiempreNiHttpsObligatorio() throws IOException {
        assertThat(String.valueOf(valor("application.yml", "ganera.seguridad.hsts-siempre"))).isEqualTo("false");
        Object exigirHttps = valor("application.yml", "ganera.frontend.exigir-https");
        assertThat(exigirHttps == null || "false".equals(String.valueOf(exigirHttps))).isTrue();
    }

    @Test
    void elPerfilCleverMandaHstsYExigeUnOrigenHttpsSinValorPorDefecto() throws IOException {
        assertThat(String.valueOf(valor("application-clever.yml", "ganera.seguridad.hsts-siempre"))).isEqualTo("true");
        assertThat(String.valueOf(valor("application-clever.yml", "ganera.frontend.exigir-https"))).isEqualTo("true");
        // Sin ":valor por defecto": sin FRONTEND_ORIGEN la app no arranca.
        assertThat(String.valueOf(valor("application-clever.yml", "ganera.frontend.origen")))
                .isEqualTo("${FRONTEND_ORIGEN}");
    }
}
