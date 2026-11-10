package com.ganera.core.ovz;

import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.web.MotivoErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Preferencias y listas OVZ de la gestoría autenticada. gestoriaId siempre del JWT.
 */
@RestController
public class ConfiguracionOvzController {

    private final PreferenciaOvzService preferenciaOvzService;

    public ConfiguracionOvzController(PreferenciaOvzService preferenciaOvzService) {
        this.preferenciaOvzService = preferenciaOvzService;
    }

    @GetMapping("/configuracion/preferencias-ovz")
    public PreferenciaOvzResponse preferencias(@AuthenticationPrincipal GaneraUserPrincipal principal) {
        return preferenciaOvzService.obtener(principal.gestoriaId());
    }

    @PutMapping("/configuracion/preferencias-ovz")
    public ResponseEntity<?> guardarPreferencias(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @RequestBody(required = false) PreferenciaOvzRequest body) {
        try {
            return ResponseEntity.ok(preferenciaOvzService.guardar(principal.gestoriaId(), principal.usuarioId(), body));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST && e.getReason() != null) {
                return ResponseEntity.badRequest().body(new MotivoErrorResponse(e.getReason()));
            }
            throw e;
        }
    }

    @GetMapping("/configuracion/listas-ovz")
    public ListasOvz.ListasOvzResponse listas(@AuthenticationPrincipal GaneraUserPrincipal principal) {
        // principal solo autentica: las listas no son por tenant.
        return ListasOvz.todas();
    }
}
