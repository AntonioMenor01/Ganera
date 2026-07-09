package com.ganera.core.facturacion;

import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.stripe.exception.StripeException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
public class FacturacionController {

    private final StripeCheckoutService stripeCheckoutService;

    public FacturacionController(StripeCheckoutService stripeCheckoutService) {
        this.stripeCheckoutService = stripeCheckoutService;
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
