package com.ganera.core.tramite;

import java.util.List;

/**
 * Respuesta de GET /resumen: los números del dashboard, agregados por gestoría en el mínimo de consultas y sin
 * traer filas para los contadores. tramitesPendientes cuenta solo PENDIENTE_REVISION (la cola que el gestor
 * revisa). ganaderos es el nº de ganaderos de la gestoría. alertasActivas reutiliza las alertas derivadas de
 * {@link AlertaService}. cola es la previa (primeras 5) de la cola de revisión con el nombre del ganadero.
 */
public record ResumenResponse(
        long tramitesPendientes,
        long ganaderos,
        int alertasActivas,
        List<ColaResumenItem> cola) {
}
