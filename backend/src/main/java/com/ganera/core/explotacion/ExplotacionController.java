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
import org.springframework.web.bind.annotation.RequestParam;
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

    /** Longitud maxima de ?q= (tras recortar): evita patrones LIKE absurdos. */
    static final int LONGITUD_MAXIMA_BUSQUEDA = 100;

    static final String MOTIVO_BUSQUEDA_DEMASIADO_LARGA =
            "La búsqueda no puede tener más de " + LONGITUD_MAXIMA_BUSQUEDA + " caracteres.";

    /** Caracter de escape del LIKE de ExplotacionRepository.buscarPorTexto (debe coincidir con su ESCAPE). */
    private static final char ESCAPE_LIKE = '!';

    /** Explotaciones de la Gestoria autenticada, con el gestoriaId del JWT como parametro real de
     * la query (no solo el gestoriaFilter ambiente). Orden por defecto {codigoRega, id}: estable
     * (codigo_rega es UNIQUE). 400 con motivo si ?sort= pide un campo fuera de la lista blanca.
     * ?q= (opcional) filtra por "contiene", sin distinguir mayusculas ni quitar acentos, en el
     * codigo REGA, el nombre de la Explotacion y el nombre del Ganadero; se recorta, y ausente,
     * vacio o en blanco = sin filtro. Orden de comprobaciones: primero la lista blanca de sort
     * (igual que sin q), despues la longitud de q (400 con motivo si pasa de 100 caracteres). */
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
        Long gestoriaId = principal.gestoriaId();
        if (texto.isEmpty()) {
            return ResponseEntity.ok(
                    explotacionRepository.findByGestoriaId(gestoriaId, pageable).map(ExplotacionResponse::from));
        }
        return ResponseEntity.ok(explotacionRepository
                .buscarPorTexto(gestoriaId, patronContiene(texto), pageable).map(ExplotacionResponse::from));
    }

    /** Patron LIKE "contiene" para un texto del usuario: escapa el caracter de escape, {@code %} y
     * {@code _} con {@link #ESCAPE_LIKE} (para que coincidan literalmente) y lo envuelve en %...%. */
    static String patronContiene(String texto) {
        StringBuilder patron = new StringBuilder(texto.length() + 2).append('%');
        for (char c : texto.toCharArray()) {
            if (c == ESCAPE_LIKE || c == '%' || c == '_') {
                patron.append(ESCAPE_LIKE);
            }
            patron.append(c);
        }
        return patron.append('%').toString();
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
