package com.ganera.core.ovz;

import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.tramite.Tramite;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Guarda el justificante de una baja presentada en OVZ (escalon 2, T3; plan 2026-11-17, §9). Lo usará el worker (T4)
 * tras descargar el PDF de Documentos. Valida que sea un PDF de como mucho 10 MB, lo guarda EN POSTGRES (metadatos +
 * binario, {@link DocumentoOvz} y {@link DocumentoOvzContenido}) y deja una fila de auditoria
 * {@code OVZ_DOCUMENTO_GUARDADO} <b>sin el contenido</b> (solo el tamaño en bytes).
 *
 * <p>Las tres entidades (tramite, ganadero, explotacion) deben ser de la misma gestoria: lo exige el servicio (el
 * worker siempre las carga por su gestoria). El actor lo pasa quien llama (el worker: {@link Actor#sistema()}).
 */
@Service
public class DocumentoOvzService {

    private final DocumentoOvzRepository documentoOvzRepository;
    private final DocumentoOvzContenidoRepository contenidoRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public DocumentoOvzService(DocumentoOvzRepository documentoOvzRepository,
                               DocumentoOvzContenidoRepository contenidoRepository,
                               AuditoriaService auditoriaService,
                               Clock clock) {
        this.documentoOvzRepository = documentoOvzRepository;
        this.contenidoRepository = contenidoRepository;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    /**
     * Guarda el justificante. Valida el PDF ANTES de escribir nada ({@link DocumentoOvzInvalidoException} si no es un
     * PDF, esta vacio o supera 10 MB). La auditoria va en la MISMA transaccion (sin contenido). Devuelve el documento.
     */
    @Transactional
    public DocumentoOvz guardar(Tramite tramite, Ganadero ganadero, Explotacion explotacion,
                                TipoDocumentoOvz tipo, String nombre, byte[] contenido, Actor actor) {
        Objects.requireNonNull(tramite, "tramite");
        Objects.requireNonNull(ganadero, "ganadero");
        Objects.requireNonNull(explotacion, "explotacion");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(actor, "actor");
        ValidadorPdf.exigirPdfValido(contenido);

        Long gestoriaId = tramite.getGestoria().getId();
        if (!gestoriaId.equals(ganadero.getGestoria().getId())
                || !gestoriaId.equals(explotacion.getGestoria().getId())) {
            // Invariante: nunca se mezclan gestorias en un documento (aislamiento multi-tenant).
            throw new IllegalArgumentException("El trámite, el ganadero y la explotación deben ser de la misma gestoría.");
        }

        Instant ahora = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        DocumentoOvz documento = new DocumentoOvz();
        documento.setGestoria(tramite.getGestoria());
        documento.setGanadero(ganadero);
        documento.setTramite(tramite);
        documento.setExplotacion(explotacion);
        documento.setTipo(tipo);
        documento.setNombre(nombre);
        documento.setTamano(contenido.length);
        documento.setContentType("application/pdf");
        documento.setSha256(sha256Hex(contenido));
        documento.setCreatedAt(ahora);
        documentoOvzRepository.save(documento);
        contenidoRepository.save(new DocumentoOvzContenido(documento, contenido));

        auditoriaService.registrar(AccionAuditoria.OVZ_DOCUMENTO_GUARDADO, gestoriaId, actor,
                EntidadAuditada.DOCUMENTO_OVZ, documento.getId(), null,
                DatosAuditoria.con(Clave.TAMANO, (long) contenido.length));
        return documento;
    }

    /** Metadatos de los documentos de un ganadero (ficha), los mas recientes primero. Sin el binario. */
    @Transactional(readOnly = true)
    public List<DocumentoOvzResponse> listarDeGanadero(Long gestoriaId, Long ganaderoId) {
        return documentoOvzRepository.findByGanaderoIdAndGestoriaIdOrderByCreatedAtDescIdDesc(ganaderoId, gestoriaId)
                .stream().map(DocumentoOvzResponse::from).toList();
    }

    private static String sha256Hex(byte[] contenido) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(contenido);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e); // nunca en una JVM estandar
        }
    }
}
