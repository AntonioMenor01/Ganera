package com.ganera.core.tramite;

import java.time.Instant;

/**
 * Una linea de {@code historialDescarte} en el detalle (A6): tipo ("DESCARTADO" o "DESCARTE_DESHECHO"),
 * el nombre del usuario (nunca su email ni su id) y cuando (ISO-8601 UTC con «Z»).
 */
public record EventoDescarteResponse(String tipo, String usuarioNombre, Instant en) {

    public static EventoDescarteResponse from(TramiteEvento evento) {
        return new EventoDescarteResponse(evento.getTipo().name(), evento.getUsuario().getNombre(),
                evento.getCreatedAt());
    }
}
