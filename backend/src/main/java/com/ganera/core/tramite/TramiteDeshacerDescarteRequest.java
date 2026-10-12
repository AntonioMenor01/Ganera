package com.ganera.core.tramite;

/**
 * Cuerpo de POST /tramites/{id}/deshacer-descarte (plan 2026-10-12, D7): solo la version que mostraba la
 * pantalla. Obligatoria (ausente o sin cuerpo -> 400 antes de buscar el Tramite). Quien deshace sale
 * siempre del JWT.
 */
public record TramiteDeshacerDescarteRequest(Long version) {
}
