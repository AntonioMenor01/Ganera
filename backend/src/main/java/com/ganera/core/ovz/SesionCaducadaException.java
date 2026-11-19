package com.ganera.core.ovz;

/** Fallo del navegador OVZ (T4). El mensaje es el motivo corto para el gestor: nunca lleva datos del trámite. */
public class SesionCaducadaException extends RuntimeException {

    public SesionCaducadaException() {
        super("La sesión de OVZ se perdió o caducó.");
    }

    public SesionCaducadaException(String motivo) {
        super(motivo);
    }
}
