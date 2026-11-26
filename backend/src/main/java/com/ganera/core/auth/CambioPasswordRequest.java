package com.ganera.core.auth;

/** Cuerpo de POST /auth/cambiar-password. Campos JSON extra se ignoran. */
public record CambioPasswordRequest(String passwordActual, String passwordNueva) {
}
