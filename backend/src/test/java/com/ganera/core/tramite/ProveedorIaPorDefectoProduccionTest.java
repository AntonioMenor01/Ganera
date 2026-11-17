package com.ganera.core.tramite;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sin {@code AI_PROVIDER} definida, el proveedor por defecto es Anthropic (el piloto corre con Haiku). Se lee el
 * application.yml REAL del disco a proposito: en el classpath de los tests, application.yml es el de
 * src/test/resources y tapa el de verdad (mismo patron que {@code ConfiguracionSeguridadProduccionTest}).
 */
class ProveedorIaPorDefectoProduccionTest {

    private static Object valor(String fichero, String propiedad) throws IOException {
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader()
                .load(fichero, new FileSystemResource("src/main/resources/" + fichero));
        assertThat(fuentes).isNotEmpty();
        return fuentes.get(0).getProperty(propiedad);
    }

    /** El application.yml de verdad: sin la variable se usa anthropic (no mistral). */
    @Test
    void elApplicationYmlUsaAnthropicSiNoHayAiProvider() throws IOException {
        assertThat(String.valueOf(valor("application.yml", "ganera.ai.provider")))
                .isEqualTo("${AI_PROVIDER:anthropic}");
    }

    /** Y ese valor por defecto, interpretado, selecciona Anthropic (y es un valor reconocido, sin aviso). */
    @Test
    void elValorPorDefectoSeleccionaAnthropic() throws IOException {
        String propiedad = String.valueOf(valor("application.yml", "ganera.ai.provider"));
        // Lo que resuelve Spring cuando AI_PROVIDER no esta definida: el texto tras los dos puntos.
        String porDefecto = propiedad.substring(propiedad.indexOf(':') + 1, propiedad.length() - 1);

        ProveedorIa.Seleccion seleccion = ProveedorIa.resolver(porDefecto);
        assertThat(seleccion.proveedor()).isEqualTo(ProveedorIa.ANTHROPIC);
        assertThat(seleccion.reconocido()).isTrue();
    }
}
