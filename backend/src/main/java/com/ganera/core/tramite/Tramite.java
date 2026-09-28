package com.ganera.core.tramite;

import com.ganera.core.contacto.Contacto;
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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tramite")
public class Tramite extends GestoriaScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contacto_id", nullable = false)
    private Contacto contacto;

    /** Null si el contacto tiene varias explotaciones y el mensaje no desambigua. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "explotacion_id")
    private Explotacion explotacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_tramite")
    private TipoTramite tipoTramite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTramite estado = EstadoTramite.PENDIENTE_EXTRACCION;

    @Column(name = "motivo_error")
    private String motivoError;

    /**
     * Version optimista (decision 27). La gestiona Hibernate: 0 al insertar y +1 en cada UPDATE del
     * Tramite. TramiteRevisionService la compara con la que mostraba la pantalla y fuerza el
     * incremento cuando la escritura solo toca tramite_crotal. Se deja null en una instancia nueva
     * a proposito: con un valor inicial, Spring Data trataria el Tramite nuevo como existente
     * (merge en vez de persist).
     */
    @Version
    @Column(nullable = false)
    private Long version;
}
