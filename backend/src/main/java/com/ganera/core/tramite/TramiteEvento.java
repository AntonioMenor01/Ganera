package com.ganera.core.tramite;

import com.ganera.core.gestoria.Usuario;
import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.AttributeOverride;
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
 * Historial de descartar / deshacer un Tramite (plan 2026-10-12, A6; tabla de V23). Una fila por accion,
 * insertada en la misma transaccion que el cambio de estado. {@code usuario} es SIEMPRE el del JWT;
 * {@code createdAt} (heredado) va a la columna {@code creado_en} con el Instant del Clock truncado a
 * microsegundos. Sin texto: la retencion no le afecta. Toda consulta lleva el gestoriaId explicito.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tramite_evento")
@AttributeOverride(name = "createdAt", column = @Column(name = "creado_en", nullable = false))
public class TramiteEvento extends GestoriaScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tramite_id", nullable = false)
    private Tramite tramite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoEventoTramite tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
}
