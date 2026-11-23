package com.ganera.core.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
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

    /**
     * Altas en un estado dado, mas antiguas primero. Lo usa el alta automatica (rama feat/alta-automatica)
     * para recorrer las PENDIENTE. Tabla NO multi-tenant, sin scope por gestoria (igual que el resto de este
     * repositorio): las altas existen antes de que exista la Gestoria.
     */
    List<AltaPendiente> findByEstadoOrderByIdAsc(EstadoAltaPendiente estado);
}
