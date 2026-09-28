package com.ganera.core.contacto;

/**
 * Contacto, Explotacion o enlace que no existe o no es de la Gestoria autenticada. El
 * controlador la traduce SIEMPRE a 404 sin cuerpo: no distingue "no existe" de "es de otra
 * Gestoria", para no revelar la existencia de datos ajenos. Publica porque la lanza
 * ContactoService.comprobarMismaGestoria, que tambien usa el importador Excel.
 */
public class RecursoNoEncontradoException extends RuntimeException {
}
