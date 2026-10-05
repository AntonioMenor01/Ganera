package com.ganera.core.whatsapp;

import com.ganera.core.contacto.TelefonoNormalizador;

/**
 * Lo que se usa de un POST de Twilio ya validado: el resto del formulario (ProfileName, WaId,
 * AccountSid, URLs de adjuntos...) no se guarda (R4 del Prompt B1).
 *
 * @param from     el From tal como llega (p. ej. {@code whatsapp:+34600111222}), sin normalizar
 * @param cuerpo   el Body; cadena vacia si no venia (un mensaje solo con foto o audio)
 * @param numMedia NumMedia; 0 si falta o no es un numero valido
 */
public record MensajeEntrante(String messageSid, String from, String cuerpo, int numMedia) {

    public MensajeEntrante {
        cuerpo = cuerpo == null ? "" : cuerpo;
        numMedia = Math.max(numMedia, 0);
    }

    /** Longitud de mensaje_campo.telefono_origen (V9). */
    static final int LONGITUD_MAXIMA_TELEFONO = 30;

    /**
     * Lo que se guarda en telefono_origen: el From normalizado (E.164) o, si no se puede normalizar,
     * tal cual pero truncado a 30 caracteres. Un numero no normalizable nunca resuelve a un Contacto,
     * asi que truncarlo no cambia nada y evita que la columna reviente (y se pierda el mensaje).
     */
    public String telefonoParaGuardar() {
        return TelefonoNormalizador.normalizar(from).orElseGet(() ->
                from == null ? "" : from.substring(0, Math.min(from.length(), LONGITUD_MAXIMA_TELEFONO)));
    }

    /** Sin cuerpo ni From: un log de este record nunca debe llevar el texto ni el telefono (R4). */
    @Override
    public String toString() {
        return "MensajeEntrante[messageSid=" + messageSid + ", numMedia=" + numMedia + "]";
    }

    public static MensajeEntrante desdeFormulario(String messageSid, String from, String body, String numMedia) {
        return new MensajeEntrante(messageSid, from, body, leerNumMedia(numMedia));
    }

    private static int leerNumMedia(String valor) {
        if (valor == null || valor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(valor.trim()), 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
