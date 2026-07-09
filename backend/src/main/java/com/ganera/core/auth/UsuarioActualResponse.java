package com.ganera.core.auth;

public record UsuarioActualResponse(Long id, String email, String nombre, Long gestoriaId, boolean activo) {
}
