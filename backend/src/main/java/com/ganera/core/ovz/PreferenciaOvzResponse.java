package com.ganera.core.ovz;

/** Preferencias OVZ de la gestoría autenticada. Todos los campos pueden ser null. */
public record PreferenciaOvzResponse(
        String oficinaDi,
        String oficinaMer,
        String emisionDi,
        String tipoIdentificador,
        String telefonoMovimiento,
        String medioTransporte,
        String matricula,
        String transportistaSirentra,
        String tipoResponsable) {

    static PreferenciaOvzResponse vacia() {
        return new PreferenciaOvzResponse(null, null, null, null, null, null, null, null, null);
    }

    static PreferenciaOvzResponse from(PreferenciaOvz p) {
        return new PreferenciaOvzResponse(
                p.getOficinaDi(),
                p.getOficinaMer(),
                p.getEmisionDi(),
                p.getTipoIdentificador(),
                p.getTelefonoMovimiento(),
                p.getMedioTransporte(),
                p.getMatricula(),
                p.getTransportistaSirentra(),
                p.getTipoResponsable());
    }
}
