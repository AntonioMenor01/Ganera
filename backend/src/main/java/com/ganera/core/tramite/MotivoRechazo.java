package com.ganera.core.tramite;

/**
 * Codigo del motivo de un rechazo (plan 2026-10-12, D4/A1). Se guarda el CODIGO en
 * {@code tramite.motivo_rechazo} (nunca texto: el texto lo pone el frontend); el CHECK de V23 solo admite
 * los valores de aqui y solo con estado RECHAZADO. Un rechazo normal no tiene motivo (null).
 */
public enum MotivoRechazo {
    /** Descartado por el gestor como mensaje sin tramite (saludo, agradecimiento, pregunta...). */
    SIN_TRAMITE
}
