package com.ganera.core.tramite;

import java.util.List;

/** Respuesta de GET /tramites/{id} y de PATCH /tramites/{id} -- el listado (TramiteResponse) se
 * queda ligero a proposito. version (decision 27) es la que el cliente debe devolver en PATCH,
 * aprobar y rechazar. mensajeOriginal es el texto de WhatsApp (null sin mensaje); pasado el plazo de
 * retencion (D6) es "[texto eliminado por antiguedad]" (RetencionMensajesService.TEXTO_ELIMINADO).
 * origen, estadoExtraccion y crotalesDescartados (B1, T3): mismos valores que en TramiteResponse;
 * el motivo tecnico de un fallo de la IA nunca se expone. */
public record TramiteDetalleResponse(
        Long id,
        String tipoTramite,
        String estado,
        String motivoError,
        Long explotacionId,
        String explotacionCodigoRega,
        String explotacionNombre,
        String mensajeOriginal,
        List<TramiteCrotalResponse> crotales,
        Long version,
        String origen,
        String estadoExtraccion,
        int crotalesDescartados) {

    public static TramiteDetalleResponse from(
            Tramite tramite, String mensajeOriginal, List<TramiteCrotalResponse> crotales) {
        boolean tieneExplotacion = tramite.getExplotacion() != null;
        return new TramiteDetalleResponse(
                tramite.getId(),
                tramite.getTipoTramite() != null ? tramite.getTipoTramite().name() : null,
                tramite.getEstado().name(),
                tramite.getMotivoError(),
                tieneExplotacion ? tramite.getExplotacion().getId() : null,
                tieneExplotacion ? tramite.getExplotacion().getCodigoRega() : null,
                tieneExplotacion ? tramite.getExplotacion().getNombre() : null,
                mensajeOriginal,
                crotales != null ? crotales : List.of(),
                tramite.getVersion(),
                tramite.getOrigen() != null ? tramite.getOrigen().name() : null,
                tramite.getEstadoExtraccion() != null ? tramite.getEstadoExtraccion().name() : null,
                tramite.getCrotalesDescartados());
    }
}
