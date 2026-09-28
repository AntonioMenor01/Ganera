package com.ganera.core.contacto;

import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.web.MotivoErrorResponse;
import com.ganera.core.shared.web.OrdenacionPermitida;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Contactos de la Gestoria autenticada (gestoriaId siempre del JWT). Formato de errores
 * (decision 8): 400/409 con { "motivo" }, 404 sin cuerpo para cualquier recurso inexistente o de
 * otra Gestoria. Toda la logica vive en ContactoService.
 */
@RestController
public class ContactoController {

    static final String MOTIVO_TELEFONO_INVALIDO = "El teléfono no es válido.";
    static final String MOTIVO_NOMBRE_OBLIGATORIO = "El nombre es obligatorio.";
    static final int LONGITUD_MAXIMA_NOMBRE = 255;
    static final String MOTIVO_NOMBRE_DEMASIADO_LARGO = "El nombre no puede superar los 255 caracteres.";
    static final String MOTIVO_ROL_INVALIDO = "El rol debe ser TITULAR o EMPLEADO.";
    static final String MOTIVO_EXPLOTACION_OBLIGATORIA = "Falta la explotación.";
    static final String MOTIVO_CONTACTO_INACTIVO = "El contacto está dado de baja; reactívalo antes de enlazarlo.";

    private final ContactoService contactoService;

    public ContactoController(ContactoService contactoService) {
        this.contactoService = contactoService;
    }

    /** Revision 7a M1 (como la decision 21): solo se ordena por campos que el DTO ya expone y que
     * son del propio Contacto -- nunca por sus Explotaciones, su Gestoria ni campos internos
     * (createdAt). */
    static final Set<String> CAMPOS_ORDENACION = Set.of("nombre", "telefono", "id");

    /** Orden por defecto {nombre, id}: alfabetico, que es como se busca a un Contacto en pantalla,
     * y estable aunque dos Contactos se llamen igual (id desempata). 400 con motivo si ?sort= pide
     * un campo fuera de la lista blanca. */
    @GetMapping("/contactos")
    public ResponseEntity<?> listar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @PageableDefault(sort = {"nombre", "id"}) Pageable pageable) {
        if (!OrdenacionPermitida.esValida(pageable.getSort(), CAMPOS_ORDENACION)) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(OrdenacionPermitida.MOTIVO));
        }
        return ResponseEntity.ok(contactoService.listar(principal.gestoriaId(), incluirInactivos, pageable));
    }

    @PostMapping("/contactos")
    public ResponseEntity<?> crear(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @RequestBody ContactoRequest request) {
        Optional<String> telefono = TelefonoNormalizador.normalizar(request.telefono());
        Optional<ResponseEntity<?>> invalido = validar(telefono, request.nombre());
        if (invalido.isPresent()) {
            return invalido.get();
        }
        return conTelefonoUnico(() -> ResponseEntity.status(HttpStatus.CREATED)
                .body(contactoService.crear(principal.gestoriaId(), telefono.get(), request.nombre().strip())));
    }

    @PutMapping("/contactos/{id}")
    public ResponseEntity<?> actualizar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id,
            @RequestBody ContactoRequest request) {
        Optional<String> telefono = TelefonoNormalizador.normalizar(request.telefono());
        Optional<ResponseEntity<?>> invalido = validar(telefono, request.nombre());
        if (invalido.isPresent()) {
            return invalido.get();
        }
        return conTelefonoUnico(() -> ResponseEntity.ok(
                contactoService.actualizar(principal.gestoriaId(), id, telefono.get(), request.nombre().strip())));
    }

    @DeleteMapping("/contactos/{id}")
    public ResponseEntity<Void> desactivar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id) {
        try {
            contactoService.desactivar(principal.gestoriaId(), id);
            return ResponseEntity.noContent().build();
        } catch (RecursoNoEncontradoException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/contactos/{id}/reactivar")
    public ResponseEntity<ContactoResponse> reactivar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id) {
        try {
            return ResponseEntity.ok(contactoService.reactivar(principal.gestoriaId(), id));
        } catch (RecursoNoEncontradoException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/contactos/{id}/explotaciones")
    public ResponseEntity<?> enlazar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id,
            @RequestBody EnlaceExplotacionRequest request) {
        if (request.explotacionId() == null) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_EXPLOTACION_OBLIGATORIA));
        }
        Optional<RolContacto> rol = parsearRol(request.rol());
        if (rol.isEmpty()) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_ROL_INVALIDO));
        }
        try {
            return ResponseEntity.ok(
                    contactoService.enlazar(principal.gestoriaId(), id, request.explotacionId(), rol.get()));
        } catch (RecursoNoEncontradoException e) {
            return ResponseEntity.notFound().build();
        } catch (ContactoInactivoException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new MotivoErrorResponse(MOTIVO_CONTACTO_INACTIVO));
        }
    }

    @DeleteMapping("/contactos/{id}/explotaciones/{explotacionId}")
    public ResponseEntity<Void> desenlazar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long explotacionId) {
        try {
            contactoService.desenlazar(principal.gestoriaId(), id, explotacionId);
            return ResponseEntity.noContent().build();
        } catch (RecursoNoEncontradoException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * La DataIntegrityViolationException del UNIQUE(telefono) sale del @Transactional del
     * servicio (ya revertido) y se captura aqui, fuera de la transaccion -- mismo patron que
     * RegistroGestoriaController. Sin consulta previa: nunca se pregunta si el telefono existe.
     */
    private static ResponseEntity<?> conTelefonoUnico(Supplier<ResponseEntity<?>> operacion) {
        try {
            return operacion.get();
        } catch (RecursoNoEncontradoException e) {
            return ResponseEntity.notFound().build();
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new MotivoErrorResponse(ContactoService.MOTIVO_TELEFONO_EN_USO));
        }
    }

    private static Optional<ResponseEntity<?>> validar(Optional<String> telefono, String nombre) {
        if (telefono.isEmpty()) {
            return Optional.of(ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_TELEFONO_INVALIDO)));
        }
        if (nombre == null || nombre.isBlank()) {
            return Optional.of(ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_NOMBRE_OBLIGATORIO)));
        }
        // Sin esto, un nombre > VARCHAR(255) fallaria en el flush como DataIntegrityViolationException
        // y se confundiria con el 409 de telefono duplicado.
        if (nombre.strip().length() > LONGITUD_MAXIMA_NOMBRE) {
            return Optional.of(ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_NOMBRE_DEMASIADO_LARGO)));
        }
        return Optional.empty();
    }

    /** Sin distinguir mayusculas; null o fuera de TITULAR/EMPLEADO -> vacio (400 con motivo). */
    private static Optional<RolContacto> parsearRol(String rol) {
        if (rol == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(RolContacto.valueOf(rol.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
