package com.ganera.core.facturacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Pago de la landing (ganera-web) que todavia no esta enlazado a una Gestoria. Se crea al recibir
 * {@code checkout.session.completed} sin {@code client_reference_id} cuando la sesion viene de la
 * landing (metadata {@code source=ganera-web}) o un price suyo esta en el mapa configurable de
 * Payment Links. El alta la completa Antonio a mano con {@code POST /internal/onboarding/gestoria}
 * pasando el {@code stripeSubscriptionId}; entonces esta fila pasa a {@link EstadoAltaPendiente#ENLAZADA}.
 *
 * <p>NO es multi-tenant: existe antes de que exista la Gestoria, asi que no extiende
 * {@code GestoriaScopedEntity} ni tiene {@code gestoria_id}. Nunca guarda cuerpo de mensajes ni datos
 * de tarjeta. La idempotencia frente a re-entregas de Stripe es {@code stripe_session_id} UNIQUE.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "alta_pendiente")
public class AltaPendiente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stripe_session_id", nullable = false, unique = true)
    private String stripeSessionId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "subscription_id")
    private String subscriptionId;

    private String email;

    private String nombre;

    private String nif;

    @Column(name = "direccion_facturacion")
    private String direccionFacturacion;

    /** Plan contratado ({@code base}/{@code gestion}/{@code plus}); de la metadata o del mapa de precios. */
    private String plan;

    /** Periodo ({@code mensual}/{@code anual}). */
    private String periodo;

    /** Si el alta nacio con prueba de 15 dias ({@code metadata.prueba}); puede ser null si no llego. */
    private Boolean prueba;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoAltaPendiente estado;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
}
