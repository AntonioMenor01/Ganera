package com.ganera.core.explotacion;

import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import com.ganera.core.shared.texto.NormalizadorBusqueda;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
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

    /**
     * NormalizadorBusqueda.textoExplotacion(codigoRega, nombre): REGA y nombre en minusculas,
     * sin tildes ni puntuacion. La mantienen setCodigoRega y setNombre (sin setter publico;
     * nada de @PrePersist/@PreUpdate, ver el plan de la busqueda sin tildes, D1) y la rellena la
     * migracion V18 para las filas anteriores. La usa la busqueda de GET /explotaciones?q=.
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "busqueda", nullable = false)
    private String busqueda;

    public void setCodigoRega(String codigoRega) {
        this.codigoRega = codigoRega;
        this.busqueda = NormalizadorBusqueda.textoExplotacion(this.codigoRega, this.nombre);
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
        this.busqueda = NormalizadorBusqueda.textoExplotacion(this.codigoRega, this.nombre);
    }
}
