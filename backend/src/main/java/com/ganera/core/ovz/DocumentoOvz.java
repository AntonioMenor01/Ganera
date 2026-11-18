package com.ganera.core.ovz;

import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.shared.tenant.GestoriaScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Metadatos de un justificante OVZ guardado en la ficha del ganadero (escalon 2, T3; plan 2026-11-17, §9). El PDF
 * vive en {@link DocumentoOvzContenido} (1:1). Tenant-scoped ({@code gestoria_id}); el aislamiento al descargar lo
 * garantiza el controlador cargando SIEMPRE con {@code findByIdAndGestoriaId}.
 *
 * <p>Invariante: ganadero, tramite y explotacion son de la misma gestoria que el documento (lo asegura el servicio
 * de guardado). {@code createdAt} (de {@link GestoriaScopedEntity}) es la fecha del documento, puesta con el Clock.
 */
@Entity
@Table(name = "documento_ovz")
@Getter
@Setter
@NoArgsConstructor
public class DocumentoOvz extends GestoriaScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ganadero_id", nullable = false)
    private Ganadero ganadero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tramite_id", nullable = false)
    private com.ganera.core.tramite.Tramite tramite;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "explotacion_id", nullable = false)
    private Explotacion explotacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoDocumentoOvz tipo;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    /** Tamaño del PDF en bytes (max 10 MB, lo cierra el CHECK y el validador). */
    @Column(name = "tamano", nullable = false)
    private int tamano;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    /** SHA-256 del PDF en hexadecimal (64 chars). */
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;
}
