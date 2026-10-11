package com.ganera.core.whatsapp;

import java.time.Instant;

/**
 * Una respuesta enviada por WhatsApp tal como la ve la gestoria (B2 reducido, D14). {@code estado} es el
 * efectivo (ENVIANDO de hace mas de 2 minutos sale como SIN_CONFIRMAR); {@code motivoError} es el texto de
 * Ganera (nunca el de Twilio); {@code enviadoPorNombre} es Usuario.nombre (nunca email ni id);
 * {@code enviadoEn} se serializa ISO-8601 UTC con Z. Sin telefono ni SID.
 */
public record RespuestaWhatsappResponse(
        Long id,
        String texto,
        String estado,
        String enviadoPorNombre,
        Instant enviadoEn,
        Integer codigoError,
        String motivoError) {

    static RespuestaWhatsappResponse from(RespuestaWhatsapp respuesta, Instant ahora) {
        EstadoRespuestaWhatsapp efectivo = ReglasRespuestaWhatsapp.estadoEfectivo(
                respuesta.getEstado(), respuesta.getCreatedAt(), ahora);
        return new RespuestaWhatsappResponse(
                respuesta.getId(),
                respuesta.getCuerpo(),
                efectivo.name(),
                respuesta.getEnviadoPor().getNombre(),
                respuesta.getCreatedAt(),
                respuesta.getCodigoErrorTwilio(),
                ReglasRespuestaWhatsapp.motivoError(efectivo, respuesta.getCodigoErrorTwilio()));
    }
}
