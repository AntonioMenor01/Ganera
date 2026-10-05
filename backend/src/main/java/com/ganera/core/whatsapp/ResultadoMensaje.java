package com.ganera.core.whatsapp;

/** Que paso con un mensaje entrante (columna mensaje_campo.resultado VARCHAR(30), V19). */
public enum ResultadoMensaje {
    TRAMITE_CREADO,
    NUMERO_DESCONOCIDO,
    CONTACTO_INACTIVO,
    /** Fallo la creacion del Tramite: el mensaje se guardo aparte para no perderlo (sin gestoria ni
     * Tramite). Hay que revisarlo a mano. */
    ERROR_RECEPCION
}
