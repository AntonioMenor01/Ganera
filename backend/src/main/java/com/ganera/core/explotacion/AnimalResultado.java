package com.ganera.core.explotacion;

/**
 * Resultado de crotal (animal) en el buscador global (GET /buscar). Lleva la explotación y el
 * ganadero para poder situarlo y navegar a la ficha del ganadero.
 */
public record AnimalResultado(
        String crotal, String explotacionCodigoRega, Long ganaderoId, String ganaderoNombre) {
}
