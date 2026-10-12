package com.ganera.core.tramite;

/**
 * Cuerpo de POST /tramites/{id}/descartar (plan 2026-10-12): solo la version que mostraba la pantalla
 * (decision 27). Obligatoria (ausente o sin cuerpo -> 400 antes de buscar el Tramite). Quien descarta sale
 * siempre del JWT; cualquier otro campo del JSON se ignora.
 */
public record TramiteDescartarRequest(Long version) {
}
