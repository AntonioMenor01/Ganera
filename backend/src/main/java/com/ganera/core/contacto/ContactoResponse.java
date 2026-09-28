package com.ganera.core.contacto;

import java.util.List;

public record ContactoResponse(
        Long id,
        String telefono,
        String nombre,
        boolean activo,
        List<ContactoExplotacionResponse> explotaciones) {

    public static ContactoResponse from(Contacto contacto, List<ContactoExplotacionResponse> explotaciones) {
        return new ContactoResponse(
                contacto.getId(),
                contacto.getTelefono(),
                contacto.getNombre(),
                contacto.isActivo(),
                explotaciones);
    }
}
