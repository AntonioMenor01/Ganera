package com.ganera.core.tramite;

import com.ganera.core.explotacion.Animal;
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
 * Crotal mencionado en un Tramite (V16). Se guarda crotalIndicado aparte de crotal porque, al
 * cambiar la Explotacion del Tramite, hay que volver a resolver desde lo que se escribio y no desde
 * el crotal completado para la Explotacion anterior. Solo lo escribe TramiteCrotalService.
 * UNIQUE(tramite_id, crotal_indicado).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tramite_crotal")
public class TramiteCrotal extends GestoriaScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tramite_id", nullable = false, updatable = false)
    private Tramite tramite;

    /** Lo que se escribio, normalizado por CrotalNormalizador. Nunca cambia. */
    @Column(name = "crotal_indicado", nullable = false, length = 30, updatable = false)
    private String crotalIndicado;

    /** Crotal completo si se resolvio a un unico Animal; si no, igual a crotalIndicado. */
    @Column(nullable = false, length = 30)
    private String crotal;

    /** Solo con resolucion EN_INVENTARIO; siempre de la Explotacion del Tramite y su Gestoria. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "animal_id")
    private Animal animal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResolucionCrotal resolucion;
}
