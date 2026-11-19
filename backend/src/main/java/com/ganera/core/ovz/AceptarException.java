package com.ganera.core.ovz;

/** Fallo del navegador OVZ (T4). El mensaje es el motivo corto para el gestor: nunca lleva datos del trámite. */
public class AceptarException extends RuntimeException {

    public AceptarException() {
        super("No se pudo comprobar el botón «Aceptar» de OVZ; no se pulsó.");
    }

    public AceptarException(String motivo) {
        super(motivo);
    }
}
