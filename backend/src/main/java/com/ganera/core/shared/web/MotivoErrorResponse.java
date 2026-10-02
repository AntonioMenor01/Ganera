package com.ganera.core.shared.web;

/** Cuerpo de error comun para 400/409 (y el 403 de aprobar sin suscripcion): { "motivo": "..." }. Los 404 van sin cuerpo (no revelan existencia). */
public record MotivoErrorResponse(String motivo) {
}
