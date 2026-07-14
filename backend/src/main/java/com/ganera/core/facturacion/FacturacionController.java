package com.ganera.core.facturacion;

import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.stripe.exception.StripeException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
public class FacturacionController {

    private final StripeCheckoutService stripeCheckoutService;
    private final SuscripcionRepository suscripcionRepository;
    private final SuscripcionService suscripcionService;

    public FacturacionController(
            StripeCheckoutService stripeCheckoutService,
            SuscripcionRepository suscripcionRepository,
            SuscripcionService suscripcionService) {
        this.stripeCheckoutService = stripeCheckoutService;
        this.suscripcionRepository = suscripcionRepository;
        this.suscripcionService = suscripcionService;
    }

    /**
     * 404 si la Gestoria no tiene ninguna Suscripcion todavia -- fail-closed, igual que
     * puedeAprobarTramites: un GET no crea nada como efecto secundario (eso solo lo hace
     * el propio checkout, via obtenerOCrearSuscripcion, cuando la Gestoria decide
     * empezar). El frontend distingue "nunca tuvo suscripcion" (404, ofrece el trial) de
     * un estado bloqueante existente (200 con puedeAprobarTramites=false).
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

    /**
     * 503 si Stripe no esta configurado todavia (clave o price id en blanco).
     * Una StripeException real (fallo de red, credenciales invalidas, etc.) se
     * deja subir sin capturar: Spring la traduce en un 500 generico sin
     * filtrar detalles de la cuenta de Stripe en el cuerpo de la respuesta.
     */
    @PostMapping("/facturacion/checkout")
    public ResponseEntity<CheckoutResponse> crearCheckout(@AuthenticationPrincipal GaneraUserPrincipal principal)
            throws StripeException {
        Optional<String> url = stripeCheckoutService.crearSesionCheckout(principal.gestoriaId());
        return url.map(u -> ResponseEntity.ok(new CheckoutResponse(u)))
                .orElseGet(() -> ResponseEntity.status(503).build());
    }
}
