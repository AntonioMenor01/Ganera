package com.ganera.core.whatsapp;

/**
 * Cuerpo de POST /tramites/{id}/respuestas-whatsapp (B2 reducido). No hay campo de telefono, contacto ni
 * gestoria: el destinatario sale siempre del Tramite y cualquier campo de mas se ignora (D6).
 */
public record RespuestaWhatsappRequest(String texto, String claveIdempotencia) {
}
