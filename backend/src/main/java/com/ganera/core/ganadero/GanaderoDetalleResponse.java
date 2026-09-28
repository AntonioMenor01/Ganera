package com.ganera.core.ganadero;

import com.ganera.core.contacto.RolContacto;

import java.util.List;

/** GET /ganaderos/{id}. DTO explicito: nunca expone las credenciales OVZ del Ganadero. */
public record GanaderoDetalleResponse(Long id, String nombre, String nif, List<ExplotacionDeGanadero> explotaciones) {

    public record ExplotacionDeGanadero(Long id, String codigoRega, String nombre, List<ContactoDeExplotacion> contactos) {
    }

    public record ContactoDeExplotacion(Long contactoId, String nombre, String telefono, RolContacto rol) {
    }
}
