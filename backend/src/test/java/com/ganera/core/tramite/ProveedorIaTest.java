package com.ganera.core.tramite;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Interpretacion del valor de AI_PROVIDER (seleccion de proveedor de IA). */
class ProveedorIaTest {

    @Test
    void anthropicEsElValorPorDefecto() {
        assertThat(ProveedorIa.POR_DEFECTO).isEqualTo(ProveedorIa.ANTHROPIC);
    }

    @Test
    void reconoceAnthropicYMistral() {
        assertThat(ProveedorIa.resolver("anthropic").proveedor()).isEqualTo(ProveedorIa.ANTHROPIC);
        assertThat(ProveedorIa.resolver("mistral").proveedor()).isEqualTo(ProveedorIa.MISTRAL);
        assertThat(ProveedorIa.resolver("anthropic").reconocido()).isTrue();
        assertThat(ProveedorIa.resolver("mistral").reconocido()).isTrue();
    }

    @Test
    void noDistingueMayusculasNiEspacios() {
        assertThat(ProveedorIa.resolver("MISTRAL").proveedor()).isEqualTo(ProveedorIa.MISTRAL);
        assertThat(ProveedorIa.resolver("  Mistral  ").proveedor()).isEqualTo(ProveedorIa.MISTRAL);
        assertThat(ProveedorIa.resolver("Anthropic").proveedor()).isEqualTo(ProveedorIa.ANTHROPIC);
        assertThat(ProveedorIa.resolver("  Mistral  ").valorOriginal()).isEqualTo("Mistral");
    }

    @Test
    void ausenteOEnBlancoUsaAnthropicSinMarcarloComoNoReconocido() {
        for (String valor : new String[] {null, "", "   "}) {
            ProveedorIa.Seleccion seleccion = ProveedorIa.resolver(valor);
            assertThat(seleccion.proveedor()).isEqualTo(ProveedorIa.ANTHROPIC);
            assertThat(seleccion.reconocido()).isTrue();
        }
    }

    @Test
    void unValorDesconocidoUsaAnthropicYSeMarcaComoNoReconocido() {
        ProveedorIa.Seleccion seleccion = ProveedorIa.resolver("openai");
        assertThat(seleccion.proveedor()).isEqualTo(ProveedorIa.ANTHROPIC);
        assertThat(seleccion.reconocido()).isFalse();
        assertThat(seleccion.valorOriginal()).isEqualTo("openai");
    }
}
