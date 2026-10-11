package com.ganera.core.whatsapp;

import com.twilio.exception.ApiException;

import java.util.Locale;

/**
 * Logica pura del envio por Twilio (skill test-sin-mocks-externos): clasifica el resultado y normaliza
 * remitente y destino. TwilioEnvioWhatsapp solo la llama; aqui no hay red ni cliente.
 */
final class ClasificadorEnvioWhatsapp {

    private static final String PREFIJO = "whatsapp:";

    private ClasificadorEnvioWhatsapp() {
    }

    /** Twilio devolvio un mensaje: con SID es Aceptado; sin SID no sabemos que paso. */
    static ResultadoEnvioWhatsapp aceptado(String messageSid) {
        if (messageSid == null || messageSid.isBlank()) {
            return new ResultadoEnvioWhatsapp.SinConfirmar();
        }
        return new ResultadoEnvioWhatsapp.Aceptado(messageSid);
    }

    /**
     * Una excepcion al enviar: {@link ApiException} con status 4xx (Twilio contesto y rechazo la
     * peticion) es Rechazado con su codigo; una ApiException 5xx o sin status, y cualquier otra
     * (ApiConnectionException, timeout, E/S, otra RuntimeException), es SinConfirmar, porque no se sabe si el mensaje salio.
     */
    static ResultadoEnvioWhatsapp clasificar(RuntimeException error) {
        // Revision T1, I1: solo un 4xx es un rechazo seguro. Un 5xx (502/504 de pasarela incluidos) puede
        // llegar con el mensaje ya creado, y sin status no sabemos si Twilio llego a contestar.
        if (error instanceof ApiException api && api.getStatusCode() != null && api.getStatusCode() < 500) {
            return new ResultadoEnvioWhatsapp.Rechazado(api.getCode());
        }
        return new ResultadoEnvioWhatsapp.SinConfirmar();
    }

    /** Codigo de Twilio para los logs (solo si es ApiException), nunca su mensaje. */
    static Integer codigo(RuntimeException error) {
        return error instanceof ApiException api ? api.getCode() : null;
    }

    /**
     * Remitente configurado ({@code whatsapp:+…} o {@code +…}) como {@code whatsapp:+…}; null si esta
     * vacio o no empieza por + seguido de digitos.
     */
    static String normalizarRemitente(String remitente) {
        if (remitente == null) {
            return null;
        }
        String valor = remitente.strip();
        if (valor.toLowerCase(Locale.ROOT).startsWith(PREFIJO)) {
            valor = valor.substring(PREFIJO.length()).strip();
        }
        if (!valor.matches("[+][0-9]{6,15}")) {
            return null;
        }
        return PREFIJO + valor;
    }

    /** Destino de Twilio para un telefono E.164 ya normalizado. */
    static String destino(String telefonoE164) {
        return PREFIJO + telefonoE164;
    }
}
