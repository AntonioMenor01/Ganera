package com.ganera.core.registro;

/** Unico cuerpo de error de POST /gestorias/registro, para cualquier causa de fallo (email
 * duplicado, password debil, email mal formado, campo en blanco) -- el mismo mensaje generico
 * para todas, para no permitir enumerar que emails ya estan registrados. */
public record RegistroErrorResponse(String mensaje) {
}
