package com.ganera.core.facturacion;

import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una Suscripcion por Gestoria (UNIQUE(gestoria_id)). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "suscripcion")
public class Suscripcion extends GestoriaScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSuscripcion estado;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", unique = true)
    private String stripeSubscriptionId;

    @Column(name = "explotaciones_contratadas")
    private Integer explotacionesContratadas;

    /**
     * Guard monotonico contra re-entrega tardia / desorden de webhooks de Stripe:
     * epoch-seconds del event.created mas reciente aplicado a esta Suscripcion.
     */
    @Column(name = "stripe_ultimo_evento_epoch")
    private Long stripeUltimoEventoEpoch;
}
