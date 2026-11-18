package com.ganera.core.ovz;

import java.time.Instant;

/** Fila de la tabla de Documentos de OVZ (tipo «Baja Animal»): {@code referencia} es lo que el navegador usa para descargar. */
public record DocumentoCandidato(String referencia, Instant fechaCreacion) {
}
