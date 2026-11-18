package com.ganera.core.ovz;

/** El justificante identificado: el candidato elegido y su PDF. No imprime su contenido (toString redactado). */
public record Justificante(DocumentoCandidato candidato, byte[] contenido) {

    @Override
    public String toString() {
        return "Justificante[REDACTADO]";
    }
}
