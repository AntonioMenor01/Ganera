package com.ganera.core.whatsapp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnmascaradorTelefonoTest {

    @Test
    void dejaSoloElPrefijoYLosTresUltimosDigitos() {
        assertThat(EnmascaradorTelefono.enmascarar("+34600111222")).isEqualTo("+34******222");
    }

    @Test
    void unNumeroDeOtroPaisTambienSeEnmascara() {
        assertThat(EnmascaradorTelefono.enmascarar("+447700900123")).isEqualTo("+44*******123");
    }

    @Test
    void unValorSinNormalizarComoElFromCrudoNoDejaVerElNumero() {
        String enmascarado = EnmascaradorTelefono.enmascarar("whatsapp:+34600111222");

        assertThat(enmascarado).doesNotContain("600111").endsWith("222").hasSize("whatsapp:+34600111222".length());
    }

    @Test
    void unValorCortoSeEnmascaraEntero() {
        assertThat(EnmascaradorTelefono.enmascarar("123456")).isEqualTo("******");
        assertThat(EnmascaradorTelefono.enmascarar("12")).isEqualTo("**");
    }

    @Test
    void nuloOVacioDaCadenaVacia() {
        assertThat(EnmascaradorTelefono.enmascarar(null)).isEmpty();
        assertThat(EnmascaradorTelefono.enmascarar("")).isEmpty();
    }
}
