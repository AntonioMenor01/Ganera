package com.ganera.core.explotacion;

import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "animal", uniqueConstraints = @UniqueConstraint(
        name = "uq_animal_gestoria_crotal", columnNames = {"gestoria_id", "crotal"}))
public class Animal extends GestoriaScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "explotacion_id", nullable = false)
    private Explotacion explotacion;

    /** Crotal completo, formato ES123456789012. UNIQUE(gestoria_id, crotal): el mismo animal real puede
     * estar en varias gestorias (V43). */
    @Column(nullable = false)
    private String crotal;

    /** Últimos dígitos, indexados aparte porque es lo que el Contacto escribe por WhatsApp. */
    @Column(name = "crotal_ultimos_digitos", nullable = false)
    private String crotalUltimosDigitos;
}
