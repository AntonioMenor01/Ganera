package com.ganera.core.whatsapp;

/**
 * Enmascara un telefono para los logs (R4 del Prompt B1): nunca se registra entero. Deja los tres
 * primeros caracteres (el prefijo, p. ej. {@code +34}) y los tres ultimos, y cambia el resto por
 * asteriscos: {@code +34600111222} → {@code +34******222}. Un valor de 6 caracteres o menos se
 * enmascara entero. Sin dependencias de Spring.
 */
public final class EnmascaradorTelefono {

    private static final int VISIBLES_AL_PRINCIPIO = 3;
    private static final int VISIBLES_AL_FINAL = 3;

    private EnmascaradorTelefono() {
    }

    public static String enmascarar(String telefono) {
        if (telefono == null || telefono.isEmpty()) {
            return "";
        }
        int longitud = telefono.length();
        if (longitud <= VISIBLES_AL_PRINCIPIO + VISIBLES_AL_FINAL) {
            return "*".repeat(longitud);
        }
        return telefono.substring(0, VISIBLES_AL_PRINCIPIO)
                + "*".repeat(longitud - VISIBLES_AL_PRINCIPIO - VISIBLES_AL_FINAL)
                + telefono.substring(longitud - VISIBLES_AL_FINAL);
    }
}
