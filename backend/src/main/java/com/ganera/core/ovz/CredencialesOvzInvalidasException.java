package com.ganera.core.ovz;

/** Fallo del navegador OVZ (T4). El mensaje es el motivo corto para el gestor: nunca lleva datos del trámite. */
public class CredencialesOvzInvalidasException extends RuntimeException {

    public CredencialesOvzInvalidasException() {
        super("Las credenciales de OVZ del ganadero no son válidas.");
    }

    public CredencialesOvzInvalidasException(String motivo) {
        super(motivo);
    }
}
