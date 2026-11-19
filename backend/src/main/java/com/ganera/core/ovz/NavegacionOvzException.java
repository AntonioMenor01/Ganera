package com.ganera.core.ovz;

/** Fallo de navegación en OVZ (T4). El mensaje es el motivo corto para el gestor: nunca lleva datos del trámite. */
public class NavegacionOvzException extends RuntimeException {

    public NavegacionOvzException(String motivo) {
        super(motivo);
    }
}
