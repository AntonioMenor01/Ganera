package com.ganera.core.tramite;

/** Crotal con formato no valido; el mensaje es el motivo para el usuario (400 {motivo} en Task 6). */
public class CrotalInvalidoException extends RuntimeException {

    public CrotalInvalidoException(String motivo) {
        super(motivo);
    }
}
