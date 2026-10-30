package com.ganera.core.search;

import com.ganera.core.explotacion.AnimalResultado;
import com.ganera.core.explotacion.ExplotacionResultado;
import com.ganera.core.ganadero.GanaderoResultado;

import java.util.List;

/**
 * Respuesta del buscador global (GET /buscar): resultados agrupados por tipo, cada grupo limitado.
 * Todo DTO explícito y ya filtrado por el gestoriaId del JWT; nunca credenciales OVZ ni entidades.
 */
public record BusquedaResponse(
        List<GanaderoResultado> ganaderos,
        List<ExplotacionResultado> explotaciones,
        List<AnimalResultado> animales) {

    public static BusquedaResponse vacia() {
        return new BusquedaResponse(List.of(), List.of(), List.of());
    }
}
