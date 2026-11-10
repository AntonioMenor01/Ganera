package com.ganera.core.ganadero;

/**
 * No se pudo obtener la contrasena OVZ en claro (ciphertext corrupto, clave distinta, o sin credencial
 * configurada). Mensaje GENERICO a proposito: nunca el ciphertext ni el texto plano. La fila de auditoria
 * {@code CREDENCIAL_OVZ_DESCIFRADA} ya quedo (se registra antes de descifrar).
 */
public class DescifradoCredencialOvzException extends RuntimeException {

    public DescifradoCredencialOvzException() {
        super("No se pudo descifrar la credencial OVZ");
    }
}
