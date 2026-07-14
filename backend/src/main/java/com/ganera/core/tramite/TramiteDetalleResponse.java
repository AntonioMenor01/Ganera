package com.ganera.core.tramite;

/** Respuesta de GET /tramites/{id} -- el listado (TramiteResponse) se queda ligero a proposito. */
public record TramiteDetalleResponse(
        Long id,
        String tipoTramite,
        String estado,
        String motivoError,
        Long explotacionId,
        String explotacionCodigoRega,
        String explotacionNombre,
        String mensajeOriginal) {

    public static TramiteDetalleResponse from(Tramite tramite, String mensajeOriginal) {
        boolean tieneExplotacion = tramite.getExplotacion() != null;
        return new TramiteDetalleResponse(
                tramite.getId(),
                tramite.getTipoTramite() != null ? tramite.getTipoTramite().name() : null,
                tramite.getEstado().name(),
                tramite.getMotivoError(),
                tieneExplotacion ? tramite.getExplotacion().getId() : null,
                tieneExplotacion ? tramite.getExplotacion().getCodigoRega() : null,
                tieneExplotacion ? tramite.getExplotacion().getNombre() : null,
                mensajeOriginal);
    }
}
