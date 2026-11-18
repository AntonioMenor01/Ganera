package com.ganera.core.tramite;

import java.util.Locale;

/**
 * Modo del worker de OVZ (T4). {@code ENSAYO} (por defecto) recorre todo hasta ANTES de «Aceptar» y sale sin
 * presentar nada; {@code REAL} pulsa «Aceptar». El parseo es seguro: solo «REAL» (sin importar mayúsculas) es REAL.
 */
public enum ModoWorkerOvz {
    ENSAYO,
    REAL;

    public static ModoWorkerOvz desde(String valor) {
        if (valor != null && valor.trim().toUpperCase(Locale.ROOT).equals("REAL")) {
            return REAL;
        }
        return ENSAYO;
    }
}
