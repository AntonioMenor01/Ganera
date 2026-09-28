package com.ganera.core.contacto;

import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vinculo Contacto-Explotacion con el rol del Contacto en esa Explotacion
 * (TITULAR o EMPLEADO). El rol vive en la relacion, no en el Contacto: la misma
 * persona puede ser titular de una Explotacion y empleado en otra. Ni un titular
 * ni un empleado estan limitados a una unica Explotacion.
 * UNIQUE(contacto_id, explotacion_id) (V7): como mucho un enlace por pareja.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contacto_explotacion")
public class ContactoExplotacion extends GestoriaScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contacto_id", nullable = false)
    private Contacto contacto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "explotacion_id", nullable = false)
    private Explotacion explotacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RolContacto rol;
}
