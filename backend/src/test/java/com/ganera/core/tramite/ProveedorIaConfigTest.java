package com.ganera.core.tramite;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El bean que elige el proveedor devuelve la implementacion correcta segun AI_PROVIDER, sin arrancar
 * un contexto de Spring (es solo la fabrica). Las claves van vacias: ninguna implementacion llama a su
 * API al construirse (el cliente es perezoso).
 */
class ProveedorIaConfigTest {

    private final ProveedorIaConfig config = new ProveedorIaConfig();

    private TramiteExtractionService crear(String proveedor) {
        return config.tramiteExtractionService(proveedor, "", "", 2, "", "", "modelo-x", 2);
    }

    @Test
    void anthropicPorDefectoCuandoFaltaElValor() {
        assertThat(crear(null)).isInstanceOf(AnthropicTramiteExtractionService.class);
        assertThat(crear("")).isInstanceOf(AnthropicTramiteExtractionService.class);
    }

    @Test
    void anthropicCuandoSePideAnthropic() {
        assertThat(crear("anthropic")).isInstanceOf(AnthropicTramiteExtractionService.class);
        assertThat(crear("ANTHROPIC")).isInstanceOf(AnthropicTramiteExtractionService.class);
    }

    @Test
    void mistralCuandoSePideMistral() {
        assertThat(crear("mistral")).isInstanceOf(MistralTramiteExtractionService.class);
        assertThat(crear("  Mistral  ")).isInstanceOf(MistralTramiteExtractionService.class);
    }

    @Test
    void unValorDesconocidoCaeEnAnthropic() {
        assertThat(crear("openai")).isInstanceOf(AnthropicTramiteExtractionService.class);
        assertThat(crear("gpt")).isInstanceOf(AnthropicTramiteExtractionService.class);
    }
}
