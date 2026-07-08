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

/** Una Suscripcion por Gestoria (UNIQUE(gestoria_id)). Sin stripeSubscriptionId
 * ni explotacionesContratadas todavia -- los añaden Prompt 2.5 y 2.7. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "suscripcion")
public class Suscripcion extends GestoriaScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSuscripcion estado;
}
