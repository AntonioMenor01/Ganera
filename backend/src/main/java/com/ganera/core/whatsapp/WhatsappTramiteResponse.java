package com.ganera.core.whatsapp;

import java.time.Instant;
import java.util.List;

/**
 * Bloque «whatsapp» del detalle de un Tramite (B2 reducido, D14); null si el Tramite no llego por
 * WhatsApp. {@code telefonoEnmascarado} con EnmascaradorTelefono (nunca el telefono entero);
 * {@code ventanaAbiertaHasta} = ultimo mensaje del contacto en la gestoria + 24 h (null sin mensajes);
 * {@code firmaGestoria} = Gestoria.nombre; {@code motivoNoDisponible} null si se puede responder;
 * {@code respuestas} en orden cronologico.
 */
public record WhatsappTramiteResponse(
        String destinatarioNombre,
        String telefonoEnmascarado,
        boolean contactoActivo,
        Instant ventanaAbiertaHasta,
        String firmaGestoria,
        String motivoNoDisponible,
        List<RespuestaWhatsappResponse> respuestas) {
}
