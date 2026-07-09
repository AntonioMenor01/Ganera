package com.ganera.core.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {
    Optional<Suscripcion> findByGestoriaId(Long gestoriaId);

    /**
     * Busqueda deliberadamente SIN scope de tenant (como ContactoRepository.findByTelefono):
     * los webhooks de Stripe llegan sin Authentication, asi que el gestoriaFilter no esta
     * activo y el stripe_subscription_id (UNIQUE global) es la unica via de resolver a que
     * Suscripcion pertenece el evento. No "arreglar" esto añadiendo scope por gestoria.
     */
    Optional<Suscripcion> findByStripeSubscriptionId(String stripeSubscriptionId);

    List<Suscripcion> findByEstadoIn(Collection<EstadoSuscripcion> estados);
}
