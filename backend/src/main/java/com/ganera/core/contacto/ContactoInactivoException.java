package com.ganera.core.contacto;

/** Se intento enlazar un Contacto dado de baja (activo=false). El controlador la traduce a 409. */
class ContactoInactivoException extends RuntimeException {
}
