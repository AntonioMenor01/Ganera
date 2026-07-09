package com.ganera.core.facturacion;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Fija la clave estatica del SDK de Stripe al arrancar. Misma leccion que el
 * footgun de Twilio (ver CLAUDE.md): una clave en blanco NO debe lanzar aqui
 * -- la app debe poder arrancar sin STRIPE_API_KEY configurado. Stripe.apiKey
 * es solo una asignacion de campo estatico; el SDK unicamente falla cuando se
 * hace una llamada real de red (ver StripeCheckoutService.crearSesionEnStripe).
 */
@Configuration
public class StripeConfig {

    private final String apiKey;

    public StripeConfig(@Value("${stripe.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @PostConstruct
    public void inicializarClaveStripe() {
        Stripe.apiKey = apiKey;
    }
}
