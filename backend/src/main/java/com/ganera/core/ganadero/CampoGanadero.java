package com.ganera.core.ganadero;

/**
 * Campo de un Ganadero que puede cambiar en una edicion (clave {@code CAMPOS_CAMBIADOS} de la fila
 * {@code GANADERO_MODIFICADO}, plan 2026-11-10). Se guarda el codigo, nunca el valor (el telefono jamas llega a
 * la auditoria). Hoy solo el telefono; el nombre y el NIF no se editan.
 */
public enum CampoGanadero {
    TELEFONO
}
