package com.ganera.core.ganadero;

import com.ganera.core.contacto.RolContacto;

import java.util.List;

/**
 * GET /ganaderos/{id}. DTO explicito: nunca expone las credenciales OVZ del Ganadero. El {@code telefono} de la
 * ficha (nullable) sale SOLO aqui, en el detalle: nunca en el listado ni en el buscador (D9).
 */
public record GanaderoDetalleResponse(Long id, String nombre, String nif, String telefono,
                                      List<ExplotacionDeGanadero> explotaciones) {

    public record ExplotacionDeGanadero(Long id, String codigoRega, String nombre, List<ContactoDeExplotacion> contactos) {
    }

    public record ContactoDeExplotacion(Long contactoId, String nombre, String telefono, RolContacto rol) {
    }
}
