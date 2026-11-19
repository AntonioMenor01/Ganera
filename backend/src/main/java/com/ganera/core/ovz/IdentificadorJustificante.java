package com.ganera.core.ovz;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Identifica el justificante de LA baja recién presentada entre los documentos «Baja Animal» de OVZ (T4, anti-duplicado):
 * solo se consideran los creados DESPUÉS del clic en «Aceptar» y se descarga cada PDF para buscar el crotal dentro
 * ({@link BuscadorCrotalEnPdf}). Ninguno -> NO_ENCONTRADO, uno -> ENCONTRADO, varios -> AMBIGUO. Lógica pura: nunca
 * imprime ni registra nada.
 */
public final class IdentificadorJustificante {

    public record Resultado(ResultadoBusquedaJustificante resultado, Optional<Justificante> justificante) {
    }

    private IdentificadorJustificante() {
    }

    public static Resultado identificar(List<DocumentoCandidato> candidatos, Instant instanteClic, String crotal,
                                        Function<DocumentoCandidato, byte[]> descargador) {
        List<DocumentoCandidato> ventana = candidatos == null ? List.of() : candidatos.stream()
                .filter(c -> c.fechaCreacion() != null && c.fechaCreacion().isAfter(instanteClic))
                .sorted(Comparator.comparing(DocumentoCandidato::fechaCreacion).reversed())
                .toList();
        List<Justificante> coincidencias = new java.util.ArrayList<>();
        for (DocumentoCandidato candidato : ventana) {
            byte[] pdf = descargador.apply(candidato);
            if (BuscadorCrotalEnPdf.contiene(pdf, crotal)) {
                coincidencias.add(new Justificante(candidato, pdf));
            }
        }
        if (coincidencias.isEmpty()) {
            return new Resultado(ResultadoBusquedaJustificante.NO_ENCONTRADO, Optional.empty());
        }
        if (coincidencias.size() > 1) {
            return new Resultado(ResultadoBusquedaJustificante.AMBIGUO, Optional.empty());
        }
        return new Resultado(ResultadoBusquedaJustificante.ENCONTRADO, Optional.of(coincidencias.get(0)));
    }
}
