package com.ganera.core.shared.auditoria;

import java.util.Locale;

/**
 * Vocabulario cerrado de claves de {@code antes} / {@code despues} (plan 2026-10-21, D8). En el JSON cada clave va
 * con su nombre en minusculas ({@code EXPLOTACION_ID} -> {@code "explotacion_id"}). T3-T5 anaden las que falten;
 * nunca una clave que lleve texto libre (DatosAuditoria ya no deja guardar un String).
 */
public enum Clave {
    ESTADO,
    VERSION,
    EXPLOTACION_ID,
    TIPO_TRAMITE,
    ANIMAL_IDS,
    NUM_CROTALES,
    ESTADO_EXTRACCION,
    MOTIVO,
    ROL,
    ACTIVO,
    CAMPOS_CAMBIADOS,
    ORIGEN,
    FILAS,
    LIMITE,
    RESULTADO,
    NUM_MEDIA,
    CODIGO_ERROR,
    EXPLOTACIONES_CONTRATADAS,
    OPERACION,
    CREADAS,
    ACTUALIZADAS,
    ERRORES,
    CROTALES_DESCARTADOS,
    INTENTOS,
    /** La pone DatosAuditoria sola cuando recorta una lista de ids (maximo 50 en total). */
    TRUNCADO;

    /** Nombre de la clave en el JSON. */
    String json() {
        return name().toLowerCase(Locale.ROOT);
    }
}
