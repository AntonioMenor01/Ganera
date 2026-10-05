package com.ganera.core.whatsapp;

/**
 * Resultado de registrar un mensaje entrante. {@code duplicado}: el MessageSid ya estaba guardado y
 * no se ha hecho nada (sin resultado ni ids). Solo se acusa recibo cuando se ha creado un Tramite.
 */
public record RecepcionMensaje(boolean duplicado, ResultadoMensaje resultado, Long mensajeId, Long tramiteId) {

    public static RecepcionMensaje deDuplicado() {
        return new RecepcionMensaje(true, null, null, null);
    }

    public boolean acusar() {
        return !duplicado && resultado == ResultadoMensaje.TRAMITE_CREADO;
    }
}
