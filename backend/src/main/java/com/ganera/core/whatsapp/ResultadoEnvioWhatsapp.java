package com.ganera.core.whatsapp;

/**
 * Lo que devuelve un envio por WhatsApp (B2 reducido, D9), en tipos propios: nada del SDK de Twilio sale
 * del adaptador.
 */
public sealed interface ResultadoEnvioWhatsapp {

    /** Twilio acepto el mensaje y le dio este SID. */
    record Aceptado(String messageSid) implements ResultadoEnvioWhatsapp {
    }

    /** Twilio rechazo la peticion; {@code codigoError} es su codigo (puede ser null). */
    record Rechazado(Integer codigoError) implements ResultadoEnvioWhatsapp {
    }

    /** Timeout, error de red u otro fallo: no se sabe si el mensaje salio. Nunca se reintenta. */
    record SinConfirmar() implements ResultadoEnvioWhatsapp {
    }
}
