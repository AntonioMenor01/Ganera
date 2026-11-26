package com.ganera.core.auth;

/**
 * Datos del usuario autenticado (GET /auth/me y respuesta de POST /auth/cambiar-password).
 * {@code debeCambiarPassword} es true mientras la contrasena sea la temporal del alta automatica: el
 * frontend lo usa para obligar a cambiarla antes de dejar usar el resto de la app.
 */
public record UsuarioActualResponse(Long id, String email, String nombre, Long gestoriaId, boolean activo,
                                    boolean debeCambiarPassword) {
}
