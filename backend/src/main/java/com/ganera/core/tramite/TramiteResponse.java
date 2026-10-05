package com.ganera.core.tramite;

import java.util.List;

/**
 * Fila de GET /tramites y respuesta de aprobar/rechazar. explotacionCodigoRega/explotacionNombre
 * (mini-prompt tras A2, punto 5; mismos nombres que en TramiteDetalleResponse) son null si el
 * Tramite no tiene Explotacion. En el listado la Explotacion llega ya cargada en la consulta de la
 * pagina (EntityGraph en TramiteRepository), nunca una consulta por fila.
 *
 * <p>origen, estadoExtraccion y crotalesDescartados (B1, T3) son columnas del propio Tramite, sin
 * consultas extra: origen es "WHATSAPP" o null (Tramites anteriores a B1); estadoExtraccion es
 * "PENDIENTE", "COMPLETADA", "FALLIDA", "SIN_TEXTO" o null (anteriores a B1); crotalesDescartados
 * cuenta los identificadores que la IA devolvio y no parecian crotales (0 si no hubo). El motivo
 * tecnico de un fallo de la IA nunca se expone: solo va a los logs, sin datos del mensaje.
 */
public record TramiteResponse(
        Long id,
        Long explotacionId,
        String explotacionCodigoRega,
        String explotacionNombre,
        String tipoTramite,
        String estado,
        String motivoError,
        List<TramiteCrotalResponse> crotales,
        Long version,
        String origen,
        String estadoExtraccion,
        int crotalesDescartados) {

    public static TramiteResponse from(Tramite tramite, List<TramiteCrotalResponse> crotales) {
        boolean tieneExplotacion = tramite.getExplotacion() != null;
        return new TramiteResponse(
                tramite.getId(),
                tieneExplotacion ? tramite.getExplotacion().getId() : null,
                tieneExplotacion ? tramite.getExplotacion().getCodigoRega() : null,
                tieneExplotacion ? tramite.getExplotacion().getNombre() : null,
                tramite.getTipoTramite() != null ? tramite.getTipoTramite().name() : null,
                tramite.getEstado().name(),
                tramite.getMotivoError(),
                crotales != null ? crotales : List.of(),
                tramite.getVersion(),
                tramite.getOrigen() != null ? tramite.getOrigen().name() : null,
                tramite.getEstadoExtraccion() != null ? tramite.getEstadoExtraccion().name() : null,
                tramite.getCrotalesDescartados());
    }
}
