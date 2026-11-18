package com.ganera.core.ovz;

import java.time.Instant;

/**
 * Metadatos de un justificante OVZ para la ficha del ganadero ({@code GET /ganaderos/{id}/documentos}). Nunca el
 * binario ni ningun dato personal del contacto; el {@code nombre} es el del fichero. El contenido se descarga aparte
 * por {@code GET /documentos-ovz/{id}/contenido}.
 */
public record DocumentoOvzResponse(
        Long id,
        Long tramiteId,
        String tipo,
        String nombre,
        int tamano,
        String contentType,
        String sha256,
        Instant creadoEn) {

    public static DocumentoOvzResponse from(DocumentoOvz documento) {
        return new DocumentoOvzResponse(
                documento.getId(),
                documento.getTramite().getId(),
                documento.getTipo().name(),
                documento.getNombre(),
                documento.getTamano(),
                documento.getContentType(),
                documento.getSha256(),
                documento.getCreatedAt());
    }
}
