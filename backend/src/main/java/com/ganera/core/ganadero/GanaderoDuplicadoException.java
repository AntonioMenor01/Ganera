package com.ganera.core.ganadero;

/** El NIF del alta manual ya está en uso. El controlador la traduce a 409 con su motivo. */
public class GanaderoDuplicadoException extends RuntimeException {
    public GanaderoDuplicadoException(String motivo) {
        super(motivo);
    }
}
