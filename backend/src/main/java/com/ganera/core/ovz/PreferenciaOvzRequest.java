package com.ganera.core.ovz;

/**
 * Cuerpo de PUT /configuracion/preferencias-ovz. Cada campo null o en blanco se guarda como null
 * (se limpia). No es un PATCH: el cliente manda el estado completo que ve en pantalla.
 */
public record PreferenciaOvzRequest(
        String oficinaDi,
        String oficinaMer,
        String emisionDi,
        String tipoIdentificador,
        String telefonoMovimiento,
        String medioTransporte,
        String matricula,
        String transportistaSirentra,
        String tipoResponsable) {
}
