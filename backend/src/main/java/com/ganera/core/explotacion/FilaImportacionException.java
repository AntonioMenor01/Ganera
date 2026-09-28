package com.ganera.core.explotacion;

/**
 * Error de fila esperado del importador (hoja Contactos): su mensaje es apto para el usuario y
 * se muestra tal cual en el resumen. Cualquier otra excepcion se muestra con un texto generico,
 * para no exponer mensajes internos (ver ExplotacionImportService.motivoContactoDe).
 */
class FilaImportacionException extends RuntimeException {

    FilaImportacionException(String motivo) {
        super(motivo);
    }
}
