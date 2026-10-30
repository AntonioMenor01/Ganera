package com.ganera.core.explotacion;

/**
 * Resultado de explotación en el buscador global (GET /buscar). Lleva el ganadero para poder navegar
 * a su ficha (la explotación vive dentro de la ficha del ganadero).
 */
public record ExplotacionResultado(
        Long id, String codigoRega, String nombre, Long ganaderoId, String ganaderoNombre) {
}
