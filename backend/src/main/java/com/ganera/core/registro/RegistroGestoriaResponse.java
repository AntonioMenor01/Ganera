package com.ganera.core.registro;

/** Respuesta de POST /gestorias/registro: URL de la Stripe Checkout Session (misma forma que
 * CheckoutResponse de facturacion, para que el frontend reutilice el mismo tipo { url }). */
public record RegistroGestoriaResponse(String url) {
}
