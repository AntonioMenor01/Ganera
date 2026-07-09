package com.ganera.core.facturacion;

/** Respuesta de POST /facturacion/checkout: URL de la Stripe Checkout Session. */
public record CheckoutResponse(String url) {
}
