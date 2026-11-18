package com.ganera.core.tramite;

/** El trámite no tiene lo necesario para presentarlo en OVZ (T4). El mensaje es el motivo para el gestor, sin datos. */
public class DatosPresentacionInvalidosException extends RuntimeException {

    public DatosPresentacionInvalidosException(String motivo) {
        super(motivo);
    }
}
