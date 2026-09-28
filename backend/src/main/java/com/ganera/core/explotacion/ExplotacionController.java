package com.ganera.core.explotacion;

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

@RestController
public class ExplotacionController {

    private final ExplotacionRepository explotacionRepository;
    private final AnimalRepository animalRepository;

    public ExplotacionController(ExplotacionRepository explotacionRepository, AnimalRepository animalRepository) {
        this.explotacionRepository = explotacionRepository;
        this.animalRepository = animalRepository;
    }

    /** Decision 21: solo se puede ordenar por estos campos (nunca por el Ganadero ni sus
     * credenciales OVZ, ni por la Gestoria). */
    static final Set<String> CAMPOS_ORDENACION = Set.of("codigoRega", "nombre", "id");

    /** Explotaciones de la Gestoria autenticada, con el gestoriaId del JWT como parametro real de
     * la query (no solo el gestoriaFilter ambiente). Orden por defecto {codigoRega, id}: estable
     * (codigo_rega es UNIQUE). 400 con motivo si ?sort= pide un campo fuera de la lista blanca. */
    @GetMapping("/explotaciones")
    public ResponseEntity<?> listar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PageableDefault(sort = {"codigoRega", "id"}) Pageable pageable) {
        if (!OrdenacionPermitida.esValida(pageable.getSort(), CAMPOS_ORDENACION)) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(OrdenacionPermitida.MOTIVO));
        }
        return ResponseEntity.ok(
                explotacionRepository.findByGestoriaId(principal.gestoriaId(), pageable).map(ExplotacionResponse::from));
    }

    /** Solo se puede ordenar los Animales por estos campos (crotal es UNIQUE: orden estable). */
    static final Set<String> CAMPOS_ORDENACION_ANIMALES = Set.of("crotal", "id");

    /** 404 sin cuerpo si la Explotacion no existe o es de otra Gestoria. Ambas consultas llevan el
     * gestoriaId del JWT como parametro real (nunca findById ni el filtro ambiente solo). 400 con
     * motivo si ?sort= pide un campo fuera de la lista blanca. */
    @GetMapping("/explotaciones/{id}/animales")
    public ResponseEntity<?> animales(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id,
            @PageableDefault(sort = "crotal") Pageable pageable) {
        if (!OrdenacionPermitida.esValida(pageable.getSort(), CAMPOS_ORDENACION_ANIMALES)) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(OrdenacionPermitida.MOTIVO));
        }
        Long gestoriaId = principal.gestoriaId();
        if (explotacionRepository.findByIdAndGestoriaId(id, gestoriaId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(
                animalRepository.findByExplotacionIdAndGestoriaId(id, gestoriaId, pageable).map(AnimalResponse::from));
    }
}
