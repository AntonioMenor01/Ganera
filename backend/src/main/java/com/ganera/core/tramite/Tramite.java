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

import java.time.Instant;

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

    /** De donde viene (B1, D3). Null en los Tramites anteriores a V19. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrigenTramite origen;

    /** Estado de la extraccion por IA (B1, D3). Null en los Tramites anteriores a V19. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_extraccion", length = 20)
    private EstadoExtraccion estadoExtraccion;

    /** Fallos de extraccion ya contados para este Tramite (los cuenta el planificador de T2; un
     * acierto no suma). Con el ultimo de ganera.extraccion.reintentos se deja de reintentar. */
    @Column(name = "intentos_extraccion", nullable = false)
    private int intentosExtraccion = 0;

    /** Cuando puede el planificador volver a intentar la extraccion; null si no hay nada pendiente. */
    @Column(name = "proximo_intento_extraccion")
    private Instant proximoIntentoExtraccion;

    /** Version que dejo el primer fallo de la IA: si cambia, alguien toco el Tramite y un reintento
     * que acierte no se aplica (no se pisa una correccion humana). */
    @Column(name = "version_tras_fallo")
    private Long versionTrasFallo;

    /** Identificadores que devolvio la IA y no parecen crotales (o pasan del maximo): se descartan y
     * se cuentan para avisar en la cola (D5). */
    @Column(name = "crotales_descartados", nullable = false)
    private int crotalesDescartados = 0;
}
