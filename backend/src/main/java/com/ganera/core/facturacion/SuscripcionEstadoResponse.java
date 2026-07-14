package com.ganera.core.facturacion;

/** Respuesta de GET /facturacion/suscripcion. */
public record SuscripcionEstadoResponse(
        EstadoSuscripcion estado,
        boolean puedeAprobarTramites,
        Integer explotacionesContratadas) {
}
