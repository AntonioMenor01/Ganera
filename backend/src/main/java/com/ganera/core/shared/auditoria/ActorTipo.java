package com.ganera.core.shared.auditoria;

/** Quien hace la accion auditada (columna {@code auditoria.actor_tipo}, cerrada por un CHECK de V25). */
public enum ActorTipo {
    USUARIO,
    SISTEMA,
    WEBHOOK_TWILIO,
    WEBHOOK_STRIPE,
    PLANIFICADOR
}
