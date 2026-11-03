package com.ganera.core.shared.auditoria;

/**
 * Tipo de la entidad a la que se refiere una fila de auditoria (columna {@code auditoria.entidad_tipo}, sin CHECK:
 * la cierra este enum). {@code IMPORTACION} es la fila resumen de una importacion de Excel (sin id propio).
 */
public enum EntidadAuditada {
    TRAMITE,
    RESPUESTA_WHATSAPP,
    CONTACTO,
    CONTACTO_EXPLOTACION,
    GANADERO,
    EXPLOTACION,
    ANIMAL,
    GESTORIA,
    USUARIO,
    SUSCRIPCION,
    MENSAJE_CAMPO,
    IMPORTACION
}
