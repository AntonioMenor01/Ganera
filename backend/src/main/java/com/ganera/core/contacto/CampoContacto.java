package com.ganera.core.contacto;

/**
 * Campo de un Contacto que puede cambiar en un PUT (clave {@code CAMPOS_CAMBIADOS} de la fila
 * {@code CONTACTO_MODIFICADO}, plan 2026-10-21). Se guarda el codigo, nunca el valor (ni el telefono ni el
 * nombre llegan a la auditoria).
 */
public enum CampoContacto {
    NOMBRE,
    TELEFONO
}
