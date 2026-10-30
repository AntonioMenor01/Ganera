package com.ganera.core.shared.auditoria;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * Una fila de auditoria (tabla de V25, plan 2026-10-21). Solo insercion: {@code @Immutable} y todas las columnas
 * {@code updatable = false}; en PostgreSQL ademas lo imponen los permisos de ganera_app y los triggers de V26. Solo la
 * construye {@link AuditoriaService}.
 *
 * <p>{@code usuario_id} se mapea como {@code Long} y no como {@code @ManyToOne Usuario}: la fila nunca navega al
 * usuario (no hay pantalla), el servicio ya ha comprobado con {@code findByIdAndGestoriaId} que es de la misma
 * gestoria, y asi no queda un proxy LAZY colgando de una entidad inmutable. La FK la impone la base de datos.
 *
 * <p>{@code createdAt} (heredado) va a la columna {@code creado_en} con el Instant del Clock truncado a
 * microsegundos (como TramiteEvento). Los setters publicos heredados de GestoriaScopedEntity no sirven de nada aqui:
 * con {@code @Immutable} Hibernate no escribe ningun cambio de una fila ya insertada.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Immutable
@Table(name = "auditoria")
@AttributeOverride(name = "createdAt", column = @Column(name = "creado_en", nullable = false, updatable = false))
public class Auditoria extends GestoriaScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_tipo", nullable = false, updatable = false, length = 20)
    private ActorTipo actorTipo;

    @Column(name = "usuario_id", updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 60)
    private AccionAuditoria accion;

    @Enumerated(EnumType.STRING)
    @Column(name = "entidad_tipo", nullable = false, updatable = false, length = 40)
    private EntidadAuditada entidadTipo;

    @Column(name = "entidad_id", updatable = false)
    private Long entidadId;

    @Column(updatable = false, length = 4000)
    private String antes;

    @Column(updatable = false, length = 4000)
    private String despues;

    @Column(updatable = false, length = 45)
    private String ip;

    @Column(name = "request_id", updatable = false, length = 36)
    private String requestId;

    @SuppressWarnings("java:S107") // una fila de auditoria tiene estas columnas; solo la construye el servicio
    Auditoria(Gestoria gestoria, Instant creadoEn, ActorTipo actorTipo, Long usuarioId, AccionAuditoria accion,
              EntidadAuditada entidadTipo, Long entidadId, String antes, String despues, String ip, String requestId) {
        setGestoria(gestoria);
        setCreatedAt(creadoEn);
        this.actorTipo = actorTipo;
        this.usuarioId = usuarioId;
        this.accion = accion;
        this.entidadTipo = entidadTipo;
        this.entidadId = entidadId;
        this.antes = antes;
        this.despues = despues;
        this.ip = ip;
        this.requestId = requestId;
    }
}
