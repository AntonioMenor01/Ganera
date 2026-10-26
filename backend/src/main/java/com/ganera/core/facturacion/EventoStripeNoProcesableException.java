package com.ganera.core.facturacion;

/**
 * Un evento de Stripe mapeado no se pudo procesar porque falta un campo imprescindible (p. ej. no se
 * pudo extraer el JSON crudo del objeto, o un invoice sin subscription ni
 * parent.subscription_details.subscription). El controller la traduce en un 5xx para que Stripe
 * REINTENTE, en vez de un 200 silencioso que perderia el evento. El motivo ya se registra en ERROR
 * (con el id y el tipo del evento) en el punto del fallo, asi que aqui no hace falta mas.
 */
class EventoStripeNoProcesableException extends RuntimeException {

    EventoStripeNoProcesableException(String mensaje) {
        super(mensaje);
    }
}
