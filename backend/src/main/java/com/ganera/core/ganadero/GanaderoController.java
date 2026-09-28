package com.ganera.core.ganadero;

import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.web.MotivoErrorResponse;
import com.ganera.core.shared.web.OrdenacionPermitida;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Ganaderos de la Gestoria autenticada (gestoriaId siempre del JWT), solo lectura. 404 sin cuerpo
 * si el Ganadero no existe o es de otra Gestoria (decision 8). Las respuestas son DTOs explicitos:
 * nunca se serializa la entidad (tiene las credenciales OVZ).
 */
@RestController
public class GanaderoController {

    private final GanaderoConsultaService ganaderoConsultaService;

    public GanaderoController(GanaderoConsultaService ganaderoConsultaService) {
        this.ganaderoConsultaService = ganaderoConsultaService;
    }

    /** Solo se puede ordenar por estos campos (nunca por las credenciales OVZ). */
    static final Set<String> CAMPOS_ORDENACION = Set.of("nombre", "nif", "id");

    /** Orden por defecto {nombre, id}: estable aunque dos Ganaderos compartan nombre. */
    @GetMapping("/ganaderos")
    public ResponseEntity<?> listar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PageableDefault(sort = {"nombre", "id"}) Pageable pageable) {
        if (!OrdenacionPermitida.esValida(pageable.getSort(), CAMPOS_ORDENACION)) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(OrdenacionPermitida.MOTIVO));
        }
        return ResponseEntity.ok(ganaderoConsultaService.listar(principal.gestoriaId(), pageable));
    }

    @GetMapping("/ganaderos/{id}")
    public ResponseEntity<GanaderoDetalleResponse> detalle(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id) {
        return ganaderoConsultaService.detalle(principal.gestoriaId(), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
