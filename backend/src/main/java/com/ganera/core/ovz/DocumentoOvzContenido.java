package com.ganera.core.ovz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * El binario del justificante OVZ (PDF), 1:1 con {@link DocumentoOvz} y con su misma PK ({@code @MapsId}). Se guarda
 * en Postgres como {@code BYTEA} (no {@code @Lob}: con {@code @Lob} pgjdbc usaria un OID/large object). Aparte de los
 * metadatos para que el listado de la ficha no cargue el binario.
 *
 * <p>No es {@code GestoriaScopedEntity}: no lleva {@code gestoria_id}. Solo es accesible a traves de su
 * {@link DocumentoOvz}, que SI se carga con {@code findByIdAndGestoriaId} antes de servir el contenido, asi que el
 * aislamiento multi-tenant lo da el documento, no esta tabla.
 */
@Entity
@Table(name = "documento_ovz_contenido")
@Getter
@NoArgsConstructor
public class DocumentoOvzContenido {

    @Id
    @Column(name = "documento_ovz_id")
    private Long documentoOvzId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_ovz_id")
    private DocumentoOvz documento;

    @Column(name = "contenido", nullable = false)
    private byte[] contenido;

    public DocumentoOvzContenido(DocumentoOvz documento, byte[] contenido) {
        this.documento = documento;
        this.contenido = contenido;
    }
}
