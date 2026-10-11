package com.ganera.core.tramite;

public enum EstadoTramite {
    PENDIENTE_EXTRACCION,
    PENDIENTE_REVISION,
    APROBADO,
    /**
     * Una persona de la gestoria ha indicado que ELLA ya metio este tramite (APROBADO) en OVZ; Ganera
     * guarda quien y cuando (Tramite.hechoEnOvzPor / hechoEnOvzEn, coherencia garantizada por el CHECK
     * de V21) y no envia nada a OVZ. Se puede deshacer (vuelve a APROBADO). Plan
     * docs/superpowers/plans/2026-10-07-hecho-en-ovz.md.
     */
    HECHO_EN_OVZ,
    // EN_PROCESO, EJECUTADO_OVZ y ERROR_OVZ: reservados para la automatizacion de OVZ (Prompt 3c);
    // hoy nada los asigna y el filtro de la cola no los ofrece.
    EN_PROCESO,
    EJECUTADO_OVZ,
    ERROR_OVZ,
    RECHAZADO
}
