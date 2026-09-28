package com.ganera.core.shared.web;

/** Cuerpo de error comun para 400/409: { "motivo": "..." }. Los 404 van sin cuerpo (no revelan existencia). */
public record MotivoErrorResponse(String motivo) {
}
