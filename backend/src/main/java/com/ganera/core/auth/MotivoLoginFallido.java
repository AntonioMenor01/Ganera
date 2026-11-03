package com.ganera.core.auth;

/**
 * Motivo de un login fallido con email existente (clave {@code MOTIVO} de la fila {@code LOGIN_FALLIDO},
 * plan 2026-10-21, A2). Es un CODIGO que solo va a la auditoria: el {@code 401} que ve el cliente sigue
 * siendo identico byte a byte en los tres casos (contrasena mala, usuario inactivo, email inexistente), asi
 * que nunca revela el motivo. Un email inexistente no deja fila (no tiene gestoria ni usuario).
 */
public enum MotivoLoginFallido {
    CONTRASENA_INCORRECTA,
    USUARIO_INACTIVO
}
