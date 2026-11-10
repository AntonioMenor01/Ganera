package com.ganera.core.explotacion;

import com.ganera.core.contacto.RolContacto;

import java.util.List;

/**
 * GET /explotaciones/{id}: lo del listado más datos útiles para la gestoría (NIF del ganadero,
 * inventario, contactos activos y trámites pendientes de revisión). Sin credenciales OVZ.
 */
public record ExplotacionDetalleResponse(
        Long id,
        String codigoRega,
        String nombre,
        Long ganaderoId,
        String nombreGanadero,
        String ganaderoNif,
        long numeroAnimales,
        long tramitesPendientesRevision,
        List<ContactoDeExplotacion> contactos) {

    public record ContactoDeExplotacion(Long contactoId, String nombre, String telefono, RolContacto rol) {
    }
}
