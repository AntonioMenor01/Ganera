package com.ganera.core.ovz;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ¿El PDF contiene el crotal? (escalon 2, §9/D5) — logica pura sobre PDFs inventados en el propio test. Es el metodo
 * con el que el worker (T4) confirmará que el justificante descargado es el de LA baja que presentó.
 */
class BuscadorCrotalEnPdfTest {

    @Test
    void encuentraElCrotalCuandoEstaEnElTexto() {
        byte[] pdf = PdfDePrueba.conLineas("Justificante de baja", "Crotal: ES120000009999", "Fecha: 18/11/2026");
        assertThat(BuscadorCrotalEnPdf.contiene(pdf, "ES120000009999")).isTrue();
    }

    @Test
    void noLoEncuentraCuandoNoEsta() {
        byte[] pdf = PdfDePrueba.conLineas("Justificante de baja", "Crotal: ES120000000001");
        assertThat(BuscadorCrotalEnPdf.contiene(pdf, "ES120000009999")).isFalse();
    }

    @Test
    void casaAunqueElCrotalVengaConEspaciosEnElPdf() {
        // En el maquetado del PDF el crotal puede salir partido por espacios: la normalizacion (sin espacios) lo une.
        byte[] pdf = PdfDePrueba.conLineas("Crotal ES12 0000 009999 bovino");
        assertThat(BuscadorCrotalEnPdf.contiene(pdf, "ES120000009999")).isTrue();
    }

    @Test
    void esIndiferenteAMayusculas() {
        byte[] pdf = PdfDePrueba.conLineas("crotal es120000009999");
        assertThat(BuscadorCrotalEnPdf.contiene(pdf, "ES120000009999")).isTrue();
    }

    @Test
    void unContenidoQueNoEsPdfDaFalseSinExcepcion() {
        assertThat(BuscadorCrotalEnPdf.contiene("no soy un pdf".getBytes(StandardCharsets.UTF_8), "ES120000009999"))
                .isFalse();
    }

    @Test
    void entradasNulasOVaciasDanFalse() {
        byte[] pdf = PdfDePrueba.conLineas("ES120000009999");
        assertThat(BuscadorCrotalEnPdf.contiene(null, "ES120000009999")).isFalse();
        assertThat(BuscadorCrotalEnPdf.contiene(pdf, null)).isFalse();
        assertThat(BuscadorCrotalEnPdf.contiene(pdf, "   ")).isFalse();
    }
}
