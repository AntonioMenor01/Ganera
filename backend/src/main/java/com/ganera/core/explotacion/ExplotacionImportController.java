package com.ganera.core.explotacion;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.web.MotivoErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
public class ExplotacionImportController {

    private final ExplotacionImportService explotacionImportService;
    private final GestoriaRepository gestoriaRepository;

    public ExplotacionImportController(
            ExplotacionImportService explotacionImportService,
            GestoriaRepository gestoriaRepository) {
        this.explotacionImportService = explotacionImportService;
        this.gestoriaRepository = gestoriaRepository;
    }

    /** 400 {@code {"motivo": "..."}} si el fichero no se puede abrir como .xlsx (un .xls antiguo
     * tiene su propio motivo) o le falta una hoja obligatoria -- {@link
     * FicheroImportacionInvalidoException} de ExplotacionImportService.importar, con el motivo ya
     * en espanol. Solo esa excepcion: cualquier otro fallo inesperado del procesado se deja subir
     * sin capturar (500), igual que StripeException en FacturacionController (D6.3). */
    @PostMapping(value = "/explotaciones/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @RequestPart("archivo") MultipartFile archivo) throws IOException {
        Gestoria gestoria = gestoriaRepository.getReferenceById(principal.gestoriaId());
        try {
            ImportResumenResponse resumen = explotacionImportService.importar(archivo, gestoria);
            return ResponseEntity.ok(resumen);
        } catch (FicheroImportacionInvalidoException e) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(e.getMessage()));
        }
    }
}
