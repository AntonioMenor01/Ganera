package com.ganera.core.tramite;

/**
 * Cuerpo de POST /tramites/{id}/marcar-hecho-ovz y /desmarcar-hecho-ovz («hecho en OVZ», 2026-10-07):
 * solo la version del Tramite que mostraba la pantalla (decision 27). Obligatoria (ausente, o sin
 * cuerpo -> 400 antes de buscar el Tramite); distinta de la actual -> 409 sin cambios.
 * A proposito NO lleva el usuario: quien marca sale siempre del JWT (GaneraUserPrincipal). Un campo
 * de mas en el JSON (p. ej. "hechoEnOvzPor") se ignora y no cambia nada.
 */
public record TramiteHechoEnOvzRequest(Long version) {
}
