package com.ganera.core.tramite;

/**
 * Cuerpo de POST /tramites/{id}/aprobar (decision 27): la version del Tramite que mostraba la
 * pantalla. Obligatoria (ausente, o sin cuerpo -> 400); distinta de la actual -> 409 sin cambios.
 */
public record TramiteAprobarRequest(Long version) {
}
