package com.ganera.core.tramite;

/**
 * Reglas puras del descarte de mensajes sin tramite (plan 2026-10-12, D3/D7), en un unico sitio que usan
 * TramiteRevisionService y las dos respuestas. Sin Spring ni BD.
 */
public final class DescarteTramite {

    private DescarteTramite() {
    }

    /**
     * D3: descartable si esta en PENDIENTE_REVISION, la IA dijo SIN_TRAMITE y nadie lo ha completado (sin
     * tipo y sin crotales). {@code numeroCrotales} son las filas de tramite_crotal que ya se cargan.
     */
    public static boolean esDescartable(Tramite tramite, int numeroCrotales) {
        return tramite.getEstado() == EstadoTramite.PENDIENTE_REVISION
                && tramite.getEstadoExtraccion() == EstadoExtraccion.SIN_TRAMITE
                && tramite.getTipoTramite() == null
                && numeroCrotales == 0;
    }

    /** D7/A1: un descarte es un RECHAZADO con motivo SIN_TRAMITE; solo eso se puede deshacer. */
    public static boolean esDescarte(Tramite tramite) {
        return tramite.getEstado() == EstadoTramite.RECHAZADO
                && tramite.getMotivoRechazo() == MotivoRechazo.SIN_TRAMITE;
    }
}
