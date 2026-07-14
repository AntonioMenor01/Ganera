package com.ganera.core.explotacion;

public record ExplotacionResponse(
        Long id,
        String codigoRega,
        String nombre,
        Long ganaderoId,
        String nombreGanadero) {

    public static ExplotacionResponse from(Explotacion explotacion) {
        return new ExplotacionResponse(
                explotacion.getId(),
                explotacion.getCodigoRega(),
                explotacion.getNombre(),
                explotacion.getGanadero().getId(),
                explotacion.getGanadero().getNombre());
    }
}
