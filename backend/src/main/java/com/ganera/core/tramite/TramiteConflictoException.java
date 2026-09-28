package com.ganera.core.tramite;

/**
 * Conflicto con el estado del Tramite (decision 8): el controlador la traduce a 409 { "motivo" }.
 * El mensaje es el motivo para el usuario. Se lanza desde TramiteRevisionService y SALE de su
 * @Transactional (rollback completo); nunca se captura dentro de la transaccion (decision 26).
 */
public class TramiteConflictoException extends RuntimeException {

    public TramiteConflictoException(String motivo) {
        super(motivo);
    }
}
