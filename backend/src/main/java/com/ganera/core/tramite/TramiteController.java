package com.ganera.core.tramite;

import com.ganera.core.facturacion.SuscripcionService;
import com.ganera.core.shared.security.GaneraUserPrincipal;
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

    public TramiteController(TramiteRepository tramiteRepository, SuscripcionService suscripcionService) {
        this.tramiteRepository = tramiteRepository;
        this.suscripcionService = suscripcionService;
    }

    /** El filtro gestoriaFilter ya esta activo para esta request -- solo ve los Tramites de la Gestoria autenticada. */
    @GetMapping("/tramites")
    public Page<TramiteResponse> listar(
            @RequestParam(required = false) EstadoTramite estado,
            Pageable pageable) {
        Page<Tramite> pagina = estado != null
                ? tramiteRepository.findByEstado(estado, pageable)
                : tramiteRepository.findAll(pageable);
        return pagina.map(TramiteResponse::from);
    }

    /** No dispara OvzAutomationService todavia (Prompt 3c) -- solo cambia el estado en BD. */
    @PostMapping("/tramites/{id}/aprobar")
    public ResponseEntity<TramiteResponse> aprobar(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id) {
        if (!suscripcionService.puedeAprobarTramites(principal.gestoriaId())) {
            return ResponseEntity.status(403).build();
        }
        Optional<Tramite> tramite = tramiteRepository.findById(id);
        if (tramite.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        tramite.get().setEstado(EstadoTramite.APROBADO);
        tramiteRepository.save(tramite.get());
        return ResponseEntity.ok(TramiteResponse.from(tramite.get()));
    }

    @PostMapping("/tramites/{id}/rechazar")
    public ResponseEntity<TramiteResponse> rechazar(@PathVariable Long id) {
        Optional<Tramite> tramite = tramiteRepository.findById(id);
        if (tramite.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        tramite.get().setEstado(EstadoTramite.RECHAZADO);
        tramiteRepository.save(tramite.get());
        return ResponseEntity.ok(TramiteResponse.from(tramite.get()));
    }
}
