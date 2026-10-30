package com.ganera.core.search;

import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.web.MotivoErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Buscador global (GET /buscar?q=): ganaderos, explotaciones y crotales de la gestoría autenticada
 * (gestoriaId SIEMPRE del JWT). q ausente, vacío o de menos de 2 caracteres devuelve 200 con grupos
 * vacíos (no es un error: el front pregunta en cada tecla). q demasiado largo da 400.
 */
@RestController
public class BuscadorController {

    /** Tope de longitud de q (tras recortar): evita patrones LIKE absurdos. Igual que /explotaciones. */
    static final int LONGITUD_MAXIMA = 100;
    static final String MOTIVO_Q_LARGA = "La búsqueda es demasiado larga.";

    private final BuscadorService buscadorService;

    public BuscadorController(BuscadorService buscadorService) {
        this.buscadorService = buscadorService;
    }

    @GetMapping("/buscar")
    public ResponseEntity<?> buscar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @RequestParam(required = false) String q) {
        String texto = q == null ? "" : q.strip();
        if (texto.length() > LONGITUD_MAXIMA) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_Q_LARGA));
        }
        return ResponseEntity.ok(buscadorService.buscar(principal.gestoriaId(), texto));
    }
}
