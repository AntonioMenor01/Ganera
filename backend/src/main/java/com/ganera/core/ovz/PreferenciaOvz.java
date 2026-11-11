package com.ganera.core.ovz;

import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Preferencias fijas de una gestoría para rellenar formularios de OVZ (oficina DI/MER, transporte,
 * teléfono de la guía…). Una fila por gestoría ({@code UNIQUE(gestoria_id)}). Todos los campos son
 * opcionales: la gestoría rellena lo que usa. Plan 2026-11-10-bloques-piloto-ovz.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "preferencia_ovz")
public class PreferenciaOvz extends GestoriaScopedEntity {

    @Column(name = "oficina_di", length = 80)
    private String oficinaDi;

    @Column(name = "oficina_mer", length = 80)
    private String oficinaMer;

    @Column(name = "emision_di", length = 80)
    private String emisionDi;

    @Column(name = "tipo_identificador", length = 80)
    private String tipoIdentificador;

    @Column(name = "telefono_movimiento", length = 30)
    private String telefonoMovimiento;

    @Column(name = "medio_transporte", length = 40)
    private String medioTransporte;

    @Column(name = "matricula", length = 40)
    private String matricula;

    @Column(name = "transportista_sirentra", length = 40)
    private String transportistaSirentra;

    @Column(name = "tipo_responsable", length = 80)
    private String tipoResponsable;
}
