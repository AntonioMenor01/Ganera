package com.ganera.core.ganadero;

import com.ganera.core.shared.auditoria.AccesoAjeno;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.auditoria.OperacionAuditada;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.web.MotivoErrorResponse;
import com.ganera.core.shared.web.OrdenacionPermitida;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.Set;

/**
 * Ganaderos de la Gestoria autenticada (gestoriaId siempre del JWT). Lectura (listado y detalle) y
 * alta manual (POST, Antonio la abre). 404 sin cuerpo si el Ganadero no existe o es de otra Gestoria
 * (decision 8). Las respuestas son DTOs explicitos: nunca se serializa la entidad (tiene las
 * credenciales OVZ).
 */
@RestController
public class GanaderoController {

    static final String MOTIVO_NOMBRE_OBLIGATORIO = "El nombre del ganadero es obligatorio.";
    static final String MOTIVO_NIF_INVALIDO =
            "El NIF/CIF no es válido. Revísalo e inténtalo de nuevo.";

    private final GanaderoConsultaService ganaderoConsultaService;
    private final GanaderoAltaService ganaderoAltaService;
    private final AuditoriaService auditoriaService;
    private final AccesoAjeno accesoAjeno;

    public GanaderoController(GanaderoConsultaService ganaderoConsultaService,
                              GanaderoAltaService ganaderoAltaService,
                              AuditoriaService auditoriaService,
                              AccesoAjeno accesoAjeno) {
        this.ganaderoConsultaService = ganaderoConsultaService;
        this.ganaderoAltaService = ganaderoAltaService;
        this.auditoriaService = auditoriaService;
        this.accesoAjeno = accesoAjeno;
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

    /** Lectura sensible (D4): telefonos de los contactos sin enmascarar. Una fila LECTURA_GANADERO por GET servido;
     * un 404 (de otra Gestoria o inexistente) no deja fila de lectura, pero un recurso ajeno deja
     * ACCESO_OTRA_GESTORIA (D3) sin cambiar la respuesta (sigue 404 sin cuerpo). */
    @GetMapping("/ganaderos/{id}")
    public ResponseEntity<GanaderoDetalleResponse> detalle(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id) {
        Optional<GanaderoDetalleResponse> detalle = ganaderoConsultaService.detalle(principal.gestoriaId(), id);
        if (detalle.isEmpty()) {
            accesoAjeno.ganadero(principal.usuarioId(), principal.gestoriaId(), id, OperacionAuditada.VER_GANADERO);
            return ResponseEntity.notFound().build();
        }
        auditoriaService.registrarAparte(AccionAuditoria.LECTURA_GANADERO, principal.gestoriaId(),
                Actor.usuario(principal.usuarioId()), EntidadAuditada.GANADERO, id, null, null);
        return ResponseEntity.ok(detalle.get());
    }

    /**
     * Alta manual de Ganadero (nombre + NIF). gestoriaId del JWT. Orden de respuestas: 400 sin
     * nombre (cuerpo ausente, {} o nombre en blanco) -> 400 NIF inválido -> 409 NIF duplicado
     * (propio: motivo claro; de otra gestoría: motivo que no revela nada) -> 201 con la fila del
     * ganadero creado. Cuerpo opcional para Spring a proposito: un POST sin cuerpo da nuestro 400
     * con motivo y no el 400 generico de Boot.
     */
    @PostMapping("/ganaderos")
    public ResponseEntity<?> crear(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @RequestBody(required = false) GanaderoCrearRequest request) {
        String nombre = request != null ? request.nombre() : null;
        String nif = request != null ? request.nif() : null;
        if (nombre == null || nombre.isBlank()) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_NOMBRE_OBLIGATORIO));
        }
        if (!NifValidador.esValido(nif)) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_NIF_INVALIDO));
        }
        try {
            GanaderoResumenResponse creado =
                    ganaderoAltaService.crear(principal.gestoriaId(), principal.usuarioId(), nombre, nif);
            return ResponseEntity.status(HttpStatus.CREATED).body(creado);
        } catch (GanaderoDuplicadoException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new MotivoErrorResponse(e.getMessage()));
        }
    }
}
