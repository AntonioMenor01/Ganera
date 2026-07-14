package com.ganera.core.tramite;

public record TramiteResponse(
        Long id,
        Long explotacionId,
        String tipoTramite,
        String estado,
        String motivoError) {

    public static TramiteResponse from(Tramite tramite) {
        return new TramiteResponse(
                tramite.getId(),
                tramite.getExplotacion() != null ? tramite.getExplotacion().getId() : null,
                tramite.getTipoTramite() != null ? tramite.getTipoTramite().name() : null,
                tramite.getEstado().name(),
                tramite.getMotivoError());
    }
}
