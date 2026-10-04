package com.ganera.core.registro;

/** Respuesta de POST /gestorias/registro: URL de la Stripe Checkout Session, con forma { url }. */
public record RegistroGestoriaResponse(String url) {
}
