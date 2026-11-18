package com.ganera.core.ovz;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Validacion pura del contenido antes de guardar un justificante (T3): PDF, no vacio, <= 10 MB. */
class ValidadorPdfTest {

    @Test
    void unPdfDeVerdadEsPdf() {
        assertThat(ValidadorPdf.esPdf(PdfDePrueba.conLineas("Baja Animal"))).isTrue();
    }

    @Test
    void unosBytesQueNoEmpiezanPorLaCabeceraNoSonPdf() {
        assertThat(ValidadorPdf.esPdf("no soy un pdf".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(ValidadorPdf.esPdf(new byte[]{0x25, 0x50})).isFalse(); // "%P", mas corto que "%PDF"
        assertThat(ValidadorPdf.esPdf(null)).isFalse();
        assertThat(ValidadorPdf.esPdf(new byte[0])).isFalse();
    }

    @Test
    void exigirAceptaUnPdfValido() {
        assertThat(catchThrowable(() -> ValidadorPdf.exigirPdfValido(PdfDePrueba.conLineas("ok")))).isNull();
    }

    @Test
    void exigirRechazaUnNoPdf() {
        assertThatThrownBy(() -> ValidadorPdf.exigirPdfValido("texto plano".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DocumentoOvzInvalidoException.class)
                .hasMessageContaining("no es un PDF");
    }

    @Test
    void exigirRechazaVacio() {
        assertThatThrownBy(() -> ValidadorPdf.exigirPdfValido(new byte[0]))
                .isInstanceOf(DocumentoOvzInvalidoException.class);
        assertThatThrownBy(() -> ValidadorPdf.exigirPdfValido(null))
                .isInstanceOf(DocumentoOvzInvalidoException.class);
    }

    @Test
    void exigirRechazaUnPdfMayorDe10Mb() {
        byte[] enorme = new byte[ValidadorPdf.MAX_BYTES + 1];
        enorme[0] = 0x25;
        enorme[1] = 0x50;
        enorme[2] = 0x44;
        enorme[3] = 0x46; // cabecera %PDF valida, pero demasiado grande
        assertThatThrownBy(() -> ValidadorPdf.exigirPdfValido(enorme))
                .isInstanceOf(DocumentoOvzInvalidoException.class)
                .hasMessageContaining("10 MB");
    }
}
