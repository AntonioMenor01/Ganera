package com.ganera.core.tramite;

import java.time.Instant;

/**
 * Estado de la presentación automática de la baja en OVZ de un trámite, para el detalle (escalón 2, T5). Es el reflejo
 * del {@link TrabajoOvz} en {@code GET /tramites/{id}} (y en el PATCH/aprobar cuando lo haya); {@code null} cuando el
 * trámite no tiene trabajo de presentación (vía manual o cualquier otro tipo).
 *
 * <ul>
 *   <li>{@code estadoTrabajo} — {@link EstadoTrabajoOvz} ({@code PENDIENTE}/{@code EN_PROCESO}/{@code EJECUTADO}/
 *       {@code ERROR}/{@code ENSAYO_OK}); el frontend lo usa para el ensayo y para el estado en curso.</li>
 *   <li>{@code claseError} — {@link TipoErrorOvz} ({@code SEGURO}/{@code INCIERTO}), solo cuando el trabajo está en
 *       {@code ERROR}; gobierna el reencolado (D11): el frontend exige la casilla de confirmación si es INCIERTO.</li>
 *   <li>{@code actualizadoEn} — el instante del último cambio del trabajo (p. ej. cuándo se presentó); ISO-8601 UTC.</li>
 * </ul>
 *
 * <p><b>Nunca</b> lleva el motivo de error (texto para el gestor): ese va en {@code tramite.motivoError}
 * ({@code TramiteDetalleResponse.motivoError}), ni la contraseña ni dato personal alguno.
 */
public record PresentacionOvzResponse(
        String estadoTrabajo,
        String claseError,
        Instant actualizadoEn) {

    public static PresentacionOvzResponse from(TrabajoOvz trabajo) {
        return new PresentacionOvzResponse(
                trabajo.getEstado() != null ? trabajo.getEstado().name() : null,
                trabajo.getClaseError() != null ? trabajo.getClaseError().name() : null,
                trabajo.getActualizadoEn());
    }
}
