package com.ganera.core.tramite;

/** Crotal de un Tramite en GET /tramites y GET /tramites/{id}. enInventario = resolucion EN_INVENTARIO. */
public record TramiteCrotalResponse(
        String crotalIndicado,
        String crotal,
        Long animalId,
        boolean enInventario,
        String resolucion) {

    public static TramiteCrotalResponse from(TramiteCrotal fila) {
        return new TramiteCrotalResponse(
                fila.getCrotalIndicado(),
                fila.getCrotal(),
                fila.getAnimal() != null ? fila.getAnimal().getId() : null,
                fila.getResolucion() == ResolucionCrotal.EN_INVENTARIO,
                fila.getResolucion().name());
    }
}
