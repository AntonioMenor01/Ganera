package com.ganera.core.tramite;

/**
 * Cuerpo de POST /tramites/{id}/rechazar (mini-prompt tras A2, punto 4): la version del Tramite
 * que mostraba la pantalla, igual que en PATCH y aprobar (decision 27). Obligatoria (ausente, o
 * sin cuerpo -> 400 antes de buscar el Tramite); distinta de la actual -> 409 sin cambios.
 * Record propio y no TramiteAprobarRequest: cada endpoint documenta su contrato, y un rechazo
 * puede ganar campos que aprobar no tiene (p. ej. un motivo de rechazo) sin tocar el otro.
 */
public record TramiteRechazarRequest(Long version) {
}
