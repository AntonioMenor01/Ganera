package com.ganera.core.ovz;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verificación pura de la pantalla 2 de la baja (T4): el Código RIIA y la explotación leídos deben cuadrar. */
class VerificacionBajaTest {

    @Test
    void coincideCuandoCrotalYExplotacionCuadran() {
        ResultadoVerificacion r = VerificacionBaja.verificar("ES120000009999", "ES120000009999",
                "ES010100000123 - Finca de prueba", "ES010100000123");
        assertThat(r.ok()).isTrue();
    }

    @Test
    void crotalDistintoNoEsOk() {
        ResultadoVerificacion r = VerificacionBaja.verificar("ES120000000001", "ES120000009999",
                "ES010100000123", "ES010100000123");
        assertThat(r.ok()).isFalse();
        assertThat(r.motivo()).isEqualTo("El Código RIIA leído en OVZ no coincide con el crotal del trámite.");
    }

    @Test
    void regaDistintoNoEsOk() {
        ResultadoVerificacion r = VerificacionBaja.verificar("ES120000009999", "ES120000009999",
                "ES019900000001 - Otra finca", "ES010100000123");
        assertThat(r.ok()).isFalse();
        assertThat(r.motivo()).isEqualTo("La explotación de ubicación en OVZ no coincide con el REGA del trámite.");
    }

    @Test
    void igualaConEspaciosYMinusculas() {
        ResultadoVerificacion r = VerificacionBaja.verificar(" es 1200 0000 9999 ", "ES120000009999",
                "es 0101 00000123 - finca", "ES01 0100 000123");
        assertThat(r.ok()).isTrue();
    }

    @Test
    void regaEnBlancoONuloNoEsOk() {
        assertThat(VerificacionBaja.verificar("ES1", "ES1", "ES0101", "  ").ok()).isFalse();
        assertThat(VerificacionBaja.verificar("ES1", "ES1", "ES0101", null).ok()).isFalse();
    }

    @Test
    void lecturasNulasNoSonOk() {
        assertThat(VerificacionBaja.verificar(null, "ES1", "ES0101", "ES0101").ok()).isFalse();
        assertThat(VerificacionBaja.verificar("ES1", "ES1", null, "ES0101").ok()).isFalse();
    }

    @Test
    void elMotivoNuncaLlevaElCrotalNiElRega() {
        ResultadoVerificacion a = VerificacionBaja.verificar("ES120000000001", "ES120000009999", "X", "ES010100000123");
        ResultadoVerificacion b = VerificacionBaja.verificar("ES120000009999", "ES120000009999", "X", "ES010100000123");
        assertThat(a.motivo()).doesNotContain("ES120000009999").doesNotContain("ES120000000001")
                .doesNotContain("ES010100000123");
        assertThat(b.motivo()).doesNotContain("ES120000009999").doesNotContain("ES010100000123");
    }
}
