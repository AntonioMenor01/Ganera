package com.ganera.core.ganadero;

/** Resultado de ganadero en el buscador global (GET /buscar). DTO explícito: nunca credenciales OVZ. */
public record GanaderoResultado(Long id, String nombre, String nif) {
}
