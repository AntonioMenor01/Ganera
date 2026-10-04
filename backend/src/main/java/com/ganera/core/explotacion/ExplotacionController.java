package com.ganera.core.explotacion;

import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.texto.NormalizadorBusqueda;
import com.ganera.core.shared.web.MotivoErrorResponse;
import com.ganera.core.shared.web.OrdenacionPermitida;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

    /** Longitud maxima de ?q= (tras recortar): evita patrones LIKE absurdos. */
    static final int LONGITUD_MAXIMA_BUSQUEDA = 100;

    static final String MOTIVO_BUSQUEDA_DEMASIADO_LARGA =
            "La búsqueda no puede tener más de " + LONGITUD_MAXIMA_BUSQUEDA + " caracteres.";

    /** Maximo de palabras distintas de ?q= tras normalizar (D4): cada una es un AND con dos LIKE. */
    static final int MAXIMO_PALABRAS_BUSQUEDA = 8;

    static final String MOTIVO_DEMASIADAS_PALABRAS =
            "La búsqueda admite como máximo " + MAXIMO_PALABRAS_BUSQUEDA + " palabras.";

    /** Explotaciones de la Gestoria autenticada, con el gestoriaId del JWT como parametro real de
     * la query (no solo el gestoriaFilter ambiente). Orden por defecto {codigoRega, id}: estable
     * (codigo_rega es UNIQUE). 400 con motivo si ?sort= pide un campo fuera de la lista blanca.
     * ?q= (opcional) busca por palabras, sin distinguir mayusculas ni tildes (plan de la busqueda
     * sin tildes): q se recorta y se parte en palabras normalizadas (NormalizadorBusqueda.palabras:
     * sin tildes, enie -> n, sin puntuacion ni comodines, sin repetidas); cada palabra debe
     * aparecer ("contiene") en el codigo REGA o el nombre de la Explotacion o en el nombre del
     * Ganadero. Orden de comprobaciones: 1) lista blanca de sort (igual que sin q); 2) longitud de
     * q recortado (400 si pasa de 100 caracteres); 3) numero de palabras distintas (400 si pasa
     * de 8). q ausente, vacio o en blanco = sin filtro; q no en blanco cuyas palabras quedan todas
     * vacias (p. ej. "%%" o "---") = pagina vacia con el pageable pedido, no el listado completo. */
    @GetMapping("/explotaciones")
    public ResponseEntity<?> listar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @RequestParam(required = false) String q,
            @PageableDefault(sort = {"codigoRega", "id"}) Pageable pageable) {
        if (!OrdenacionPermitida.esValida(pageable.getSort(), CAMPOS_ORDENACION)) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(OrdenacionPermitida.MOTIVO));
        }
        String texto = q == null ? "" : q.strip();
        if (texto.length() > LONGITUD_MAXIMA_BUSQUEDA) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_BUSQUEDA_DEMASIADO_LARGA));
        }
        List<String> palabras = NormalizadorBusqueda.palabras(texto);
        if (palabras.size() > MAXIMO_PALABRAS_BUSQUEDA) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_DEMASIADAS_PALABRAS));
        }
        Long gestoriaId = principal.gestoriaId();
        if (texto.isEmpty()) {
            return ResponseEntity.ok(
                    explotacionRepository.findByGestoriaId(gestoriaId, pageable).map(ExplotacionResponse::from));
        }
        if (palabras.isEmpty()) {
            return ResponseEntity.ok(Page.<ExplotacionResponse>empty(pageable));
        }
        return ResponseEntity.ok(explotacionRepository
                .findAll(ExplotacionBusquedaSpecification.porPalabras(gestoriaId, palabras), pageable)
                .map(ExplotacionResponse::from));
    }

    /** Detalle de una Explotacion (mismo DTO que el listado). 404 sin cuerpo si no existe o es de
     * otra Gestoria: findByIdAndGestoriaId con el gestoriaId del JWT, nunca findById a secas. */
    @GetMapping("/explotaciones/{id}")
    public ResponseEntity<ExplotacionResponse> detalle(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id) {
        return explotacionRepository.findByIdAndGestoriaId(id, principal.gestoriaId())
                .map(ExplotacionResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
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
