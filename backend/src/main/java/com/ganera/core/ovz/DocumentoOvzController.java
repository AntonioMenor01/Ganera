package com.ganera.core.ovz;

import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.shared.auditoria.AccesoAjeno;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.auditoria.OperacionAuditada;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Justificantes OVZ (escalon 2, T3). Dos endpoints, siempre con la gestoria del JWT:
 * <ul>
 *   <li>{@code GET /ganaderos/{id}/documentos}: metadatos de los justificantes de un ganadero (ficha). Un ganadero de
 *       otra gestoria o inexistente -> 404 sin cuerpo + fila {@code ACCESO_OTRA_GESTORIA} ({@code VER_GANADERO}).</li>
 *   <li>{@code GET /documentos-ovz/{id}/contenido}: descarga del PDF como adjunto. Un documento de otra gestoria o
 *       inexistente -> 404 sin cuerpo + {@code ACCESO_OTRA_GESTORIA} ({@code DESCARGAR_DOCUMENTO_OVZ}). Un 200 deja
 *       una fila {@code LECTURA_DOCUMENTO_OVZ} (lectura sensible, D4). El contenido se carga con el id ya validado
 *       por gestoria: NUNCA un {@code findById} desnudo sobre el documento.</li>
 * </ul>
 */
@RestController
public class DocumentoOvzController {

    private final DocumentoOvzService documentoOvzService;
    private final DocumentoOvzRepository documentoOvzRepository;
    private final DocumentoOvzContenidoRepository contenidoRepository;
    private final GanaderoRepository ganaderoRepository;
    private final AuditoriaService auditoriaService;
    private final AccesoAjeno accesoAjeno;

    public DocumentoOvzController(DocumentoOvzService documentoOvzService,
                                  DocumentoOvzRepository documentoOvzRepository,
                                  DocumentoOvzContenidoRepository contenidoRepository,
                                  GanaderoRepository ganaderoRepository,
                                  AuditoriaService auditoriaService,
                                  AccesoAjeno accesoAjeno) {
        this.documentoOvzService = documentoOvzService;
        this.documentoOvzRepository = documentoOvzRepository;
        this.contenidoRepository = contenidoRepository;
        this.ganaderoRepository = ganaderoRepository;
        this.auditoriaService = auditoriaService;
        this.accesoAjeno = accesoAjeno;
    }

    @GetMapping("/ganaderos/{id}/documentos")
    public ResponseEntity<List<DocumentoOvzResponse>> deGanadero(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id) {
        if (ganaderoRepository.findByIdAndGestoriaId(id, principal.gestoriaId()).isEmpty()) {
            accesoAjeno.ganadero(principal.usuarioId(), principal.gestoriaId(), id, OperacionAuditada.VER_GANADERO);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(documentoOvzService.listarDeGanadero(principal.gestoriaId(), id));
    }

    @GetMapping("/documentos-ovz/{id}/contenido")
    public ResponseEntity<Resource> contenido(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id) {
        Optional<DocumentoOvz> documento = documentoOvzRepository.findByIdAndGestoriaId(id, principal.gestoriaId());
        if (documento.isEmpty()) {
            accesoAjeno.documentoOvz(principal.usuarioId(), principal.gestoriaId(), id,
                    OperacionAuditada.DESCARGAR_DOCUMENTO_OVZ);
            return ResponseEntity.notFound().build();
        }
        DocumentoOvz doc = documento.get();
        byte[] bytes = contenidoRepository.findById(id).orElseThrow().getContenido();

        auditoriaService.registrarAparte(AccionAuditoria.LECTURA_DOCUMENTO_OVZ, principal.gestoriaId(),
                Actor.usuario(principal.usuarioId()), EntidadAuditada.DOCUMENTO_OVZ, id, null, null);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(doc.getNombre(), StandardCharsets.UTF_8).build());
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(new ByteArrayResource(bytes), headers, 200);
    }
}
