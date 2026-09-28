package com.ganera.core.contacto;

public record ContactoExplotacionResponse(Long explotacionId, String codigoRega, String nombre, RolContacto rol) {

    public static ContactoExplotacionResponse from(ContactoExplotacion enlace) {
        return new ContactoExplotacionResponse(
                enlace.getExplotacion().getId(),
                enlace.getExplotacion().getCodigoRega(),
                enlace.getExplotacion().getNombre(),
                enlace.getRol());
    }
}
