package com.ganera.core.whatsapp;

/**
 * Estado de una respuesta enviada por WhatsApp (B2 reducido, D8/D9). ENVIANDO: fila creada, Twilio aun no
 * ha contestado (pasados 2 minutos se expone como SIN_CONFIRMAR, ver ReglasRespuestaWhatsapp). ENVIADO:
 * Twilio la acepto. ENTREGADO y LEIDO: los fijara el callback de estado (T4). FALLIDO: Twilio la rechazo
 * (con su codigo). SIN_CONFIRMAR: timeout o error de red; no se sabe si salio y nunca se reintenta.
 */
public enum EstadoRespuestaWhatsapp {
    ENVIANDO,
    ENVIADO,
    ENTREGADO,
    LEIDO,
    FALLIDO,
    SIN_CONFIRMAR
}
