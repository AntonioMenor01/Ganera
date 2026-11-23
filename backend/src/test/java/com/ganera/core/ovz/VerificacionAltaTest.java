package com.ganera.core.ovz;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verificación pura de la pantalla 2 del alta (T4): el crotal seleccionado que muestra OVZ debe ser el del ternero. */
class VerificacionAltaTest {

    @Test
    void coincideCuandoElCrotalSeleccionadoEsElDelTernero() {
        ResultadoVerificacion r = VerificacionAlta.verificar("ES120000009999", "ES120000009999");
        assertThat(r.ok()).isTrue();
        assertThat(r.motivo()).isNull();
    }

    @Test
    void crotalDistintoNoEsOk() {
        ResultadoVerificacion r = VerificacionAlta.verificar("ES120000000001", "ES120000009999");
        assertThat(r.ok()).isFalse();
        assertThat(r.motivo()).isEqualTo("El crotal seleccionado en OVZ no coincide con el del ternero del trámite.");
    }

    @Test
    void igualaConEspaciosYMinusculas() {
        assertThat(VerificacionAlta.verificar(" es 1200 0000 9999 ", "ES120000009999").ok()).isTrue();
    }

    @Test
    void lecturasEnBlancoONulasNoSonOk() {
        assertThat(VerificacionAlta.verificar(null, "ES1").ok()).isFalse();
        assertThat(VerificacionAlta.verificar("ES1", null).ok()).isFalse();
        assertThat(VerificacionAlta.verificar("  ", "ES1").ok()).isFalse();
        assertThat(VerificacionAlta.verificar("ES1", "  ").ok()).isFalse();
    }

    @Test
    void elMotivoNuncaLlevaElCrotal() {
        ResultadoVerificacion r = VerificacionAlta.verificar("ES120000000001", "ES120000009999");
        assertThat(r.motivo()).doesNotContain("ES120000009999").doesNotContain("ES120000000001");
    }
}
