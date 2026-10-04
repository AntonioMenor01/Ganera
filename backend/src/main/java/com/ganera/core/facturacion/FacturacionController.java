package com.ganera.core.facturacion;

import com.ganera.core.shared.security.GaneraUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solo lectura del estado de la Suscripcion de la Gestoria. La app ya no inicia pagos (plan
 * 2026-10-04): el cobro se hace desde la landing con Stripe y el servidor se entera por los
 * webhooks (StripeWebhookController). Ya no existe un endpoint de checkout en la app.
 */
@RestController
public class FacturacionController {

    private final SuscripcionRepository suscripcionRepository;
    private final SuscripcionService suscripcionService;

    public FacturacionController(
            SuscripcionRepository suscripcionRepository,
            SuscripcionService suscripcionService) {
        this.suscripcionRepository = suscripcionRepository;
        this.suscripcionService = suscripcionService;
    }

    /**
     * 404 si la Gestoria no tiene ninguna Suscripcion todavia -- fail-closed, igual que
     * puedeAprobarTramites: un GET no crea nada como efecto secundario (la Suscripcion la
     * crean el onboarding interno, el checkout del alta publica -- POST /gestorias/registro,
     * via obtenerOCrearSuscripcion -- y los webhooks de Stripe). El frontend distingue "nunca
     * tuvo suscripcion" (404) de un estado bloqueante existente (200 con
     * puedeAprobarTramites=false); en ambos casos el banner pide contactar con Ganera.
     */
    @GetMapping("/facturacion/suscripcion")
    public ResponseEntity<SuscripcionEstadoResponse> estado(@AuthenticationPrincipal GaneraUserPrincipal principal) {
        return suscripcionRepository.findByGestoriaId(principal.gestoriaId())
                .map(suscripcion -> ResponseEntity.ok(new SuscripcionEstadoResponse(
                        suscripcion.getEstado(),
                        suscripcionService.puedeAprobarTramites(principal.gestoriaId()),
                        suscripcion.getExplotacionesContratadas())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
