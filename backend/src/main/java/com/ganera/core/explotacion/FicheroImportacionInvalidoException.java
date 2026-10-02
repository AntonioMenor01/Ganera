package com.ganera.core.explotacion;

/**
 * El fichero subido a POST /explotaciones/importar no se puede importar como tal: no se pudo abrir
 * como .xlsx (un .xls antiguo, un fichero vacio, texto, un ZIP roto...) o le falta una hoja
 * obligatoria. Su mensaje es el {@code motivo} en espanol que se devuelve al cliente con un 400
 * ({@link ExplotacionImportController}); nunca lleva el mensaje tecnico de POI.
 *
 * <p>Es una excepcion propia (y no IllegalArgumentException, que es lo que lanzaba antes el
 * importador y lo que lanza POI) para que el controller convierta en 400 <b>solo</b> estos casos:
 * cualquier otro error inesperado del procesado sube sin capturar (500), en vez de disfrazarse de
 * "fichero invalido" (decision D6.3 del mini-prompt de backend tras A2).
 */
public class FicheroImportacionInvalidoException extends RuntimeException {

    public FicheroImportacionInvalidoException(String motivo) {
        super(motivo);
    }

    public FicheroImportacionInvalidoException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
