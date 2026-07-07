package com.ganera.core.explotacion;

import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "explotacion")
public class Explotacion extends GestoriaScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ganadero_id", nullable = false)
    private Ganadero ganadero;

    @Column(name = "codigo_rega", nullable = false, unique = true)
    private String codigoRega;

    @Column(nullable = false)
    private String nombre;
}
