package com.ganera.core.ovz;

/**
 * El contenido que se intenta guardar como justificante OVZ no es valido (vacio, mayor de 10 MB, o no es un PDF). La
 * lanza {@link ValidadorPdf} antes de escribir nada. El mensaje NUNCA incluye el contenido.
 */
public class DocumentoOvzInvalidoException extends RuntimeException {
    public DocumentoOvzInvalidoException(String message) {
        super(message);
    }
}
