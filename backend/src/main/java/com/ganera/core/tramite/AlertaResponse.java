package com.ganera.core.tramite;

import java.time.LocalDate;

/** Una alerta de plazo o cola para Inicio / Calendario (plan 2026-11-10). */
public record AlertaResponse(
        String id,
        String tipo,
        String titulo,
        String detalle,
        LocalDate venceEl,
        Long tramiteId,
        String severidad) {
}
