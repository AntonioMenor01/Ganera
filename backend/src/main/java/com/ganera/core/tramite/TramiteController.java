package com.ganera.core.tramite;

import com.ganera.core.contacto.RecursoNoEncontradoException;
import com.ganera.core.facturacion.SuscripcionService;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.web.MotivoErrorResponse;
import com.ganera.core.shared.web.OrdenacionPermitida;
import com.ganera.core.whatsapp.MensajeCampo;
import com.ganera.core.whatsapp.MensajeCampoRepository;
import com.ganera.core.whatsapp.WhatsappDetalleService;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@RestController
public class TramiteController {

    static final String MOTIVO_TIPO_INVALIDO = "Tipo de trámite no válido.";
    static final String MOTIVO_FALTA_VERSION =
            "Falta la versión del trámite (campo version). Vuelve a cargarlo e inténtalo de nuevo.";
    /** Motivo del 403 de aprobar (mini-prompt tras A2; texto cambiado en el plan 2026-10-04, D2,
     * al quitar el pago de la app). Un unico texto, identico al de reserva del frontend: no
     * distingue prueba terminada / suspendida / sin Suscripcion (el banner de suscripcion del
     * layout ya lo dice), pide contactar con Ganera en vez de remitir a una pagina de pago, y no
     * depende del Tramite, asi que no revela nada de el. */
    static final String MOTIVO_SUSCRIPCION_NO_PERMITE_APROBAR =
            "Tu suscripción no permite aprobar trámites ahora mismo (prueba terminada o suscripción "
                    + "suspendida). Ponte en contacto con Ganera para regularizarla.";
    static final String MOTIVO_CONCURRENCIA =
            "El trámite se estaba modificando a la vez desde otra sesión. Vuelve a cargarlo e inténtalo de nuevo.";

    private final TramiteRepository tramiteRepository;
    private final SuscripcionService suscripcionService;
    private final MensajeCampoRepository mensajeCampoRepository;
    private final TramiteCrotalService tramiteCrotalService;
    private final TramiteRevisionService tramiteRevisionService;
    private final WhatsappDetalleService whatsappDetalleService;

    public TramiteController(
            TramiteRepository tramiteRepository,
            SuscripcionService suscripcionService,
            MensajeCampoRepository mensajeCampoRepository,
            TramiteCrotalService tramiteCrotalService,
            TramiteRevisionService tramiteRevisionService,
            WhatsappDetalleService whatsappDetalleService) {
        this.tramiteRepository = tramiteRepository;
        this.suscripcionService = suscripcionService;
        this.mensajeCampoRepository = mensajeCampoRepository;
        this.tramiteCrotalService = tramiteCrotalService;
        this.tramiteRevisionService = tramiteRevisionService;
        this.whatsappDetalleService = whatsappDetalleService;
    }

    /** Decision 21: solo se puede ordenar por estos campos (nunca por el Contacto, la
     * Explotacion/Ganadero o la Gestoria). */
    static final Set<String> CAMPOS_ORDENACION = Set.of("id", "estado", "createdAt");

    /** Tramites de la Gestoria autenticada, con el gestoriaId del JWT como parametro real de la
     * query (no solo el gestoriaFilter ambiente). Orden por defecto {createdAt desc, id desc}: lo
     * mas reciente primero, estable aunque dos Tramites compartan instante. 400 con motivo si
     * ?sort= pide un campo fuera de la lista blanca.
     * Los crotales de toda la pagina se cargan en UNA consulta con el gestoriaId explicito del JWT
     * (no el filtro ambiente), nunca uno por Tramite. */
    @GetMapping("/tramites")
    public ResponseEntity<?> listar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @RequestParam(required = false) EstadoTramite estado,
            @PageableDefault(sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        if (!OrdenacionPermitida.esValida(pageable.getSort(), CAMPOS_ORDENACION)) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(OrdenacionPermitida.MOTIVO));
        }
        Long gestoriaId = principal.gestoriaId();
        Page<Tramite> pagina = estado != null
                ? tramiteRepository.findByGestoriaIdAndEstado(gestoriaId, estado, pageable)
                : tramiteRepository.findByGestoriaId(gestoriaId, pageable);
        List<Long> ids = pagina.getContent().stream().map(Tramite::getId).toList();
        Map<Long, List<TramiteCrotalResponse>> crotales = tramiteCrotalService.crotalesPorTramite(ids, gestoriaId);
        return ResponseEntity.ok(pagina.map(t -> TramiteResponse.from(t, crotales.get(t.getId()))));
    }

    /** Detalle para el modal de revision -- incluye el texto original de WhatsApp (null si 3b,
     * la extraccion IA, aun no lo ha generado) y el nombre/codigo de la Explotacion resuelta.
     * Usa findByIdAndGestoriaId, NUNCA findById(id) a secas: Hibernate no aplica gestoriaFilter a
     * una busqueda por clave primaria, asi que un id de otra Gestoria se resolveria igual de bien
     * sin el gestoriaId explicito aqui (encontrado en auditoria post-mortem, ver
     * TenantIsolationEndToEndTest). */
    @GetMapping("/tramites/{id}")
    public ResponseEntity<TramiteDetalleResponse> detalle(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id) {
        Optional<Tramite> tramite = tramiteRepository.findByIdAndGestoriaId(id, principal.gestoriaId());
        if (tramite.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // gestoriaId explicito: un mensaje enlazado al Tramite pero de otra gestoria nunca se muestra.
        String mensajeOriginal = mensajeCampoRepository
                .findFirstByTramiteIdAndGestoriaIdOrderByCreatedAtDesc(id, principal.gestoriaId())
                .map(MensajeCampo::getCuerpo)
                .orElse(null);
        return ResponseEntity.ok(TramiteDetalleResponse.from(
                tramite.get(), mensajeOriginal, crotalesDe(tramite.get(), principal.gestoriaId()),
                whatsappDetalleService.bloque(tramite.get(), principal.gestoriaId()),
                tramiteRevisionService.historialDescarte(id, principal.gestoriaId())));
    }

    /**
     * Revision del Tramite por la gestoria (Task 6). Toda la logica y el bloqueo de fila viven en
     * TramiteRevisionService; aqui solo se exige la version, se valida tipoTramite y se traducen
     * las excepciones que salen de su transaccion (ya revertida): 404 sin cuerpo, 400/409 con
     * { "motivo" }. Orden: 400 sin version -> 400 tipo invalido -> 404 -> 409 estado -> 409
     * version distinta -> 404 explotacion ajena / 400 crotal invalido -> 409 mismo animal.
     */
    @PatchMapping("/tramites/{id}")
    public ResponseEntity<?> actualizar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id,
            @RequestBody TramitePatchRequest request) {
        // Decision 27: sin version -> 400 ANTES de buscar el Tramite (misma respuesta para uno
        // propio, uno de otra Gestoria o uno inexistente: no revela nada).
        if (request.version() == null) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_FALTA_VERSION));
        }
        TipoTramite tipo = null;
        if (request.tipoTramite() != null) {
            Optional<TipoTramite> parseado = parsearTipo(request.tipoTramite());
            if (parseado.isEmpty()) {
                return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_TIPO_INVALIDO));
            }
            tipo = parseado.get();
        }
        TipoTramite tipoFinal = tipo;
        return traducirErrores(() -> ResponseEntity.ok(tramiteRevisionService.actualizar(
                principal.gestoriaId(), principal.usuarioId(), id, request.version(), request.explotacionId(),
                tipoFinal, request.crotales())));
    }

    /**
     * No dispara OvzAutomationService todavia (Prompt 3c) -- solo cambia el estado en BD.
     * Orden: 403 suscripcion (con { "motivo" } generico, mini-prompt tras A2) -> 400 sin version (cuerpo ausente o version null; antes de buscar
     * el Tramite, asi que no revela nada) -> 404 (gestoriaId explicito, nunca findById a secas) ->
     * 409 si no esta en PENDIENTE_REVISION -> 409 si la version no es la actual (decision 27) ->
     * 409 si la re-resolucion de crotales cambio algo (se guarda e incrementa la version) -> 409 si
     * faltan explotacion/tipo o algun crotal no es aprobable.
     * El cuerpo es opcional para Spring (required = false) a proposito: asi el 403 de suscripcion
     * sigue siendo lo primero y la falta de version da nuestro 400 con motivo.
     */
    @PostMapping("/tramites/{id}/aprobar")
    public ResponseEntity<?> aprobar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) TramiteAprobarRequest request) {
        if (!suscripcionService.puedeAprobarTramites(principal.gestoriaId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MotivoErrorResponse(MOTIVO_SUSCRIPCION_NO_PERMITE_APROBAR));
        }
        Long version = request != null ? request.version() : null;
        if (version == null) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_FALTA_VERSION));
        }
        return traducirErrores(() -> ResponseEntity.ok(
                tramiteRevisionService.aprobar(principal.gestoriaId(), principal.usuarioId(), id, version)));
    }

    /**
     * Sin requisitos de datos ni de suscripcion. Desde el mini-prompt tras A2 (punto 4) exige la
     * version que mostraba la pantalla, igual que PATCH y aprobar (decision 27). Orden: 400 sin
     * version (cuerpo ausente, {} o version null; antes de buscar el Tramite, asi que es la misma
     * respuesta para uno propio, uno de otra Gestoria o uno inexistente) -> 404 sin cuerpo ->
     * 409 si no esta en PENDIENTE_REVISION (decision 12) -> 409 si la version no es la actual ->
     * 200 con la version incrementada exactamente en 1.
     * Cuerpo opcional para Spring (required = false) a proposito: asi un POST sin cuerpo da
     * nuestro 400 con motivo y no el 400 generico de Boot.
     */
    @PostMapping("/tramites/{id}/rechazar")
    public ResponseEntity<?> rechazar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) TramiteRechazarRequest request) {
        Long version = request != null ? request.version() : null;
        if (version == null) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_FALTA_VERSION));
        }
        return traducirErrores(() -> ResponseEntity.ok(
                tramiteRevisionService.rechazar(principal.gestoriaId(), principal.usuarioId(), id, version)));
    }

    /**
     * «Hecho en OVZ» (2026-10-07): el gestor indica que EL ya metio en OVZ este tramite APROBADO;
     * Ganera guarda quien y cuando y no envia nada a OVZ. Quien marca es SIEMPRE el usuario del JWT
     * (principal.usuarioId()), nunca un dato del cuerpo. Sin comprobacion de suscripcion (A1 D30).
     * Orden: 400 sin version (cuerpo ausente, {} o version null; antes de buscar el Tramite) -> 404
     * sin cuerpo (otra Gestoria o no existe) -> 409 si no esta APROBADO (ya marcado: su motivo) ->
     * 409 version desfasada -> 200 TramiteResponse con la version + 1.
     */
    @PostMapping("/tramites/{id}/marcar-hecho-ovz")
    public ResponseEntity<?> marcarHechoEnOvz(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) TramiteHechoEnOvzRequest request) {
        Long version = request != null ? request.version() : null;
        if (version == null) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_FALTA_VERSION));
        }
        return traducirErrores(() -> ResponseEntity.ok(tramiteRevisionService.marcarHechoEnOvz(
                principal.gestoriaId(), principal.usuarioId(), id, version)));
    }

    /**
     * Quita la marca de «hecho en OVZ» (vuelve a APROBADO; en OVZ no cambia nada). Mismo orden que
     * marcar, con el 409 de estado si no esta HECHO_EN_OVZ. Sin comprobacion de suscripcion.
     */
    @PostMapping("/tramites/{id}/desmarcar-hecho-ovz")
    public ResponseEntity<?> desmarcarHechoEnOvz(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) TramiteHechoEnOvzRequest request) {
        Long version = request != null ? request.version() : null;
        if (version == null) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_FALTA_VERSION));
        }
        return traducirErrores(() -> ResponseEntity.ok(tramiteRevisionService.desmarcarHechoEnOvz(
                principal.gestoriaId(), principal.usuarioId(), id, version)));
    }

    /**
     * «Descartar» un mensaje sin tramite (plan 2026-10-12): RECHAZADO con motivo SIN_TRAMITE, sin enviar nada.
     * Quien descarta es SIEMPRE el usuario del JWT. Sin comprobacion de suscripcion (D8). Orden: 400 sin version
     * (cuerpo ausente, {} o version null; antes de buscar el Tramite) -> 404 sin cuerpo -> 409 no pendiente ->
     * 409 version desfasada -> 409 no descartable -> 200 TramiteDetalleResponse con la version + 1.
     */
    @PostMapping("/tramites/{id}/descartar")
    public ResponseEntity<?> descartar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) TramiteDescartarRequest request) {
        Long version = request != null ? request.version() : null;
        if (version == null) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_FALTA_VERSION));
        }
        return traducirErrores(() -> ResponseEntity.ok(tramiteRevisionService.descartar(
                principal.gestoriaId(), principal.usuarioId(), id, version)));
    }

    /**
     * Deshace un descarte (D7): vuelve a PENDIENTE_REVISION. Solo sobre un descartado; un rechazo normal da
     * 409. Orden: 400 sin version (antes de buscar el Tramite) -> 404 sin cuerpo -> 409 no es un descarte ->
     * 409 version desfasada -> 200 TramiteDetalleResponse con la version + 1. Sin comprobacion de suscripcion.
     */
    @PostMapping("/tramites/{id}/deshacer-descarte")
    public ResponseEntity<?> deshacerDescarte(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) TramiteDeshacerDescarteRequest request) {
        Long version = request != null ? request.version() : null;
        if (version == null) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(MOTIVO_FALTA_VERSION));
        }
        return traducirErrores(() -> ResponseEntity.ok(tramiteRevisionService.deshacerDescarte(
                principal.gestoriaId(), principal.usuarioId(), id, version)));
    }

    /**
     * Las excepciones se capturan AQUI, fuera del @Transactional del servicio (decision 26).
     * ConcurrencyFailureException cubre el bloqueo que no se consigue a tiempo y los conflictos
     * optimistas/filas obsoletas; DataIntegrityViolationException, un UNIQUE pisado por una
     * edicion simultanea. Ambos son conflictos de concurrencia -> 409, nunca 500.
     */
    private static ResponseEntity<?> traducirErrores(Supplier<ResponseEntity<?>> operacion) {
        try {
            return operacion.get();
        } catch (RecursoNoEncontradoException e) {
            return ResponseEntity.notFound().build();
        } catch (CrotalInvalidoException e) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(e.getMessage()));
        } catch (TramiteConflictoException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new MotivoErrorResponse(e.getMessage()));
        } catch (ConcurrencyFailureException | DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new MotivoErrorResponse(MOTIVO_CONCURRENCIA));
        }
    }

    /** Sin distinguir mayusculas ni espacios alrededor; vacio si no es un valor de TipoTramite. */
    private static Optional<TipoTramite> parsearTipo(String tipo) {
        try {
            return Optional.of(TipoTramite.valueOf(tipo.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private List<TramiteCrotalResponse> crotalesDe(Tramite tramite, Long gestoriaId) {
        return tramiteCrotalService.crotalesPorTramite(List.of(tramite.getId()), gestoriaId)
                .getOrDefault(tramite.getId(), List.of());
    }
}
