package com.ganera.core.ganadero;

/** Fila de GET /ganaderos. DTO explicito: nunca expone las credenciales OVZ del Ganadero. */
public record GanaderoResumenResponse(Long id, String nombre, String nif, long numeroExplotaciones) {
}
