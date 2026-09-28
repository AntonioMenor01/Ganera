package com.ganera.core.tramite;

import java.util.List;

/** Respuesta de GET /tramites/{id} -- el listado (TramiteResponse) se queda ligero a proposito.
 * version (decision 27) es la que el cliente debe devolver en PATCH y aprobar. */
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
        Long version) {

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
                tramite.getVersion());
    }
}
