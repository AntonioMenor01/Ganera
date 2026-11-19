package com.ganera.core.ovz;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Identificación del justificante / anti-duplicado (T4): PDFs inventados + descargador falso. */
class IdentificadorJustificanteTest {

    private static final String CROTAL = "ES120000009999";
    private static final Instant CLIC = Instant.parse("2026-11-18T10:00:00Z");

    private static DocumentoCandidato doc(String ref, String fecha) {
        return new DocumentoCandidato(ref, Instant.parse(fecha));
    }

    @Test
    void unoConElCrotalYFechaPosteriorEsEncontrado() {
        var bueno = doc("a", "2026-11-18T10:00:05Z");
        var otro = doc("b", "2026-11-18T10:00:06Z");
        Map<String, byte[]> pdfs = Map.of(
                "a", PdfDePrueba.conLineas("Baja", "Crotal " + CROTAL),
                "b", PdfDePrueba.conLineas("Baja", "Crotal ES120000000001"));

        var r = IdentificadorJustificante.identificar(List.of(bueno, otro), CLIC, CROTAL, c -> pdfs.get(c.referencia()));

        assertThat(r.resultado()).isEqualTo(ResultadoBusquedaJustificante.ENCONTRADO);
        assertThat(r.justificante()).isPresent();
        assertThat(r.justificante().get().candidato()).isEqualTo(bueno);
        assertThat(r.justificante().get().contenido()).isEqualTo(pdfs.get("a"));
    }

    @Test
    void ningunoConElCrotalEsNoEncontrado() {
        var d = doc("a", "2026-11-18T10:00:05Z");
        var r = IdentificadorJustificante.identificar(List.of(d), CLIC, CROTAL,
                c -> PdfDePrueba.conLineas("Crotal ES120000000001"));
        assertThat(r.resultado()).isEqualTo(ResultadoBusquedaJustificante.NO_ENCONTRADO);
        assertThat(r.justificante()).isEmpty();
    }

    @Test
    void sinCandidatosEsNoEncontrado() {
        var r = IdentificadorJustificante.identificar(List.of(), CLIC, CROTAL, c -> new byte[0]);
        assertThat(r.resultado()).isEqualTo(ResultadoBusquedaJustificante.NO_ENCONTRADO);
    }

    @Test
    void dosConElCrotalSonAmbiguos() {
        var a = doc("a", "2026-11-18T10:00:05Z");
        var b = doc("b", "2026-11-18T10:00:09Z");
        var r = IdentificadorJustificante.identificar(List.of(a, b), CLIC, CROTAL,
                c -> PdfDePrueba.conLineas("Crotal " + CROTAL));
        assertThat(r.resultado()).isEqualTo(ResultadoBusquedaJustificante.AMBIGUO);
        assertThat(r.justificante()).isEmpty();
    }

    @Test
    void unCandidatoAnteriorAlClicSeIgnoraYNoSeDescarga() {
        var viejo = doc("viejo", "2026-11-18T09:59:59Z");
        var nuevo = doc("nuevo", "2026-11-18T10:00:05Z");
        List<String> descargados = new ArrayList<>();

        var r = IdentificadorJustificante.identificar(List.of(viejo, nuevo), CLIC, CROTAL, c -> {
            descargados.add(c.referencia());
            return PdfDePrueba.conLineas("Crotal " + CROTAL);
        });

        assertThat(r.resultado()).isEqualTo(ResultadoBusquedaJustificante.ENCONTRADO);
        assertThat(r.justificante().get().candidato()).isEqualTo(nuevo);
        assertThat(descargados).containsExactly("nuevo");
    }

    @Test
    void siSoloHayAnterioresAlClicNoEncontrado() {
        var viejo = doc("viejo", "2026-11-17T09:00:00Z");
        var r = IdentificadorJustificante.identificar(List.of(viejo), CLIC, CROTAL,
                c -> PdfDePrueba.conLineas("Crotal " + CROTAL));
        assertThat(r.resultado()).isEqualTo(ResultadoBusquedaJustificante.NO_ENCONTRADO);
    }
}
