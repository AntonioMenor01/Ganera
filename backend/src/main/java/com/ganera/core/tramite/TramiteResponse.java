package com.ganera.core.tramite;

import java.util.List;

public record TramiteResponse(
        Long id,
        Long explotacionId,
        String tipoTramite,
        String estado,
        String motivoError,
        List<TramiteCrotalResponse> crotales,
        Long version) {

    public static TramiteResponse from(Tramite tramite, List<TramiteCrotalResponse> crotales) {
        return new TramiteResponse(
                tramite.getId(),
                tramite.getExplotacion() != null ? tramite.getExplotacion().getId() : null,
                tramite.getTipoTramite() != null ? tramite.getTipoTramite().name() : null,
                tramite.getEstado().name(),
                tramite.getMotivoError(),
                crotales != null ? crotales : List.of(),
                tramite.getVersion());
    }
}
