package com.ganera.core.tramite;

import com.ganera.core.ganadero.Ganadero;
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

import java.time.Instant;

/**
 * Trabajo de presentación automática de la baja por muerte en OVZ (escalón 2, T2; migración V33). Es la cola:
 * se encola al <b>aprobar</b> un {@code BAJA_MUERTE} cuyo ganadero tiene credenciales y no usa certificado (D4/D8),
 * en la misma transacción que la aprobación; el worker (T4) lo reclama y lo presenta.
 *
 * <p><b>Un solo trabajo por trámite</b> (clave de idempotencia): {@code UNIQUE(tramite_id)} en la base de datos, así
 * que reencolar reutiliza la misma fila (nunca inserta una segunda) y un doble encolado lo corta el constraint. El
 * trabajo y el trámite pertenecen a la misma gestoría (tenant-scoped, {@code gestoria_id} explícito en toda consulta).
 *
 * <p>{@code claseError} solo tiene valor cuando {@code estado = ERROR} (CHECK {@code ck_trabajo_ovz_clase_error});
 * gobierna el reencolado (D11). {@code motivoError} es un texto corto para el gestor (lo fija el worker, T4); <b>nunca
 * va a auditoría</b> (ahí solo van estados/códigos, no texto libre).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "trabajo_ovz")
public class TrabajoOvz extends GestoriaScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tramite_id", nullable = false, unique = true)
    private Tramite tramite;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ganadero_id", nullable = false)
    private Ganadero ganadero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoTrabajoOvz tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTrabajoOvz estado;

    /** Clase del error (SEGURO/INCIERTO); null salvo cuando {@code estado = ERROR} (CHECK). Lo fija el worker (T4). */
    @Enumerated(EnumType.STRING)
    @Column(name = "clase_error", length = 20)
    private TipoErrorOvz claseError;

    /** Motivo del error, texto corto para el gestor (lo fija el worker, T4). Nunca va a auditoría ni a logs. */
    @Column(name = "motivo_error", length = 500)
    private String motivoError;

    /** Intentos de PRESENTACIÓN hechos por el worker (no cuenta los reencolados). Lo lleva el worker (T4). */
    @Column(nullable = false)
    private int intentos = 0;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;
}
