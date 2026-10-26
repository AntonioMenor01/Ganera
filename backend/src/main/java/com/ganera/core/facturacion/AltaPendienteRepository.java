package com.ganera.core.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio de {@link AltaPendiente}. Tabla NO multi-tenant (los webhooks de Stripe llegan sin
 * Authentication, el gestoriaFilter no esta activo): las busquedas son por los ids globales de Stripe.
 */
public interface AltaPendienteRepository extends JpaRepository<AltaPendiente, Long> {

    /** Idempotencia: una fila por Checkout Session (UNIQUE). */
    Optional<AltaPendiente> findByStripeSessionId(String stripeSessionId);

    /** Para marcarla ENLAZADA al dar de alta la Gestoria con ese stripeSubscriptionId. */
    Optional<AltaPendiente> findBySubscriptionId(String subscriptionId);
}
