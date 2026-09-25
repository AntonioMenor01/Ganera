package com.ganera.core.registro;

public record RegistroGestoriaRequest(
        String nombreGestoria,
        String nombreUsuario,
        String email,
        String password,
        RangoClientes rangoClientes) {
}
