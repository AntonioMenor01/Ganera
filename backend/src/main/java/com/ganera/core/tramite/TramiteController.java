package com.ganera.core.tramite;

import com.ganera.core.facturacion.SuscripcionService;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.whatsapp.MensajeCampo;
import com.ganera.core.whatsapp.MensajeCampoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
public class TramiteController {

    private final TramiteRepository tramiteRepository;
    private final SuscripcionService suscripcionService;
    private final MensajeCampoRepository mensajeCampoRepository;

    public TramiteController(
            TramiteRepository tramiteRepository,
            SuscripcionService suscripcionService,
            MensajeCampoRepository mensajeCampoRepository) {
        this.tramiteRepository = tramiteRepository;
        this.suscripcionService = suscripcionService;
        this.mensajeCampoRepository = mensajeCampoRepository;
    }

    /** El filtro gestoriaFilter ya esta activo para esta request -- solo ve los Tramites de la Gestoria autenticada.
     * findAll/findByEstado son queries derivadas de resultado, asi que SI respetan el filtro (a
     * diferencia de findById, ver el comentario en aprobar/rechazar/detalle mas abajo). */
    @GetMapping("/tramites")
    public Page<TramiteResponse> listar(
            @RequestParam(required = false) EstadoTramite estado,
            Pageable pageable) {
        Page<Tramite> pagina = estado != null
                ? tramiteRepository.findByEstado(estado, pageable)
                : tramiteRepository.findAll(pageable);
        return pagina.map(TramiteResponse::from);
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
        String mensajeOriginal = mensajeCampoRepository.findFirstByTramiteIdOrderByCreatedAtDesc(id)
                .map(MensajeCampo::getCuerpo)
                .orElse(null);
        return ResponseEntity.ok(TramiteDetalleResponse.from(tramite.get(), mensajeOriginal));
    }

    /** No dispara OvzAutomationService todavia (Prompt 3c) -- solo cambia el estado en BD.
     * findByIdAndGestoriaId, no findById(id) a secas -- ver comentario en detalle(). */
    @PostMapping("/tramites/{id}/aprobar")
    public ResponseEntity<TramiteResponse> aprobar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id) {
        if (!suscripcionService.puedeAprobarTramites(principal.gestoriaId())) {
            return ResponseEntity.status(403).build();
        }
        Optional<Tramite> tramite = tramiteRepository.findByIdAndGestoriaId(id, principal.gestoriaId());
        if (tramite.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        tramite.get().setEstado(EstadoTramite.APROBADO);
        tramiteRepository.save(tramite.get());
        return ResponseEntity.ok(TramiteResponse.from(tramite.get()));
    }

    /** findByIdAndGestoriaId, no findById(id) a secas -- ver comentario en detalle(). */
    @PostMapping("/tramites/{id}/rechazar")
    public ResponseEntity<TramiteResponse> rechazar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id) {
        Optional<Tramite> tramite = tramiteRepository.findByIdAndGestoriaId(id, principal.gestoriaId());
        if (tramite.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        tramite.get().setEstado(EstadoTramite.RECHAZADO);
        tramiteRepository.save(tramite.get());
        return ResponseEntity.ok(TramiteResponse.from(tramite.get()));
    }
}
