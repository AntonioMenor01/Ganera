package com.ganera.core.ganadero;

/**
 * Cuerpo de POST /ganaderos (alta manual desde la UI). Solo nombre y NIF: las credenciales OVZ y las
 * explotaciones no se dan de alta aquí (las explotaciones siguen llegando por el Excel/WhatsApp).
 */
public record GanaderoCrearRequest(String nombre, String nif) {
}
