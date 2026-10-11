package com.ganera.core.whatsapp;

/** 409 de la respuesta por WhatsApp (B2 reducido): su mensaje es el {@code motivo} que ve la gestoria. */
public class RespuestaWhatsappConflictoException extends RuntimeException {

    public RespuestaWhatsappConflictoException(String motivo) {
        super(motivo);
    }
}
