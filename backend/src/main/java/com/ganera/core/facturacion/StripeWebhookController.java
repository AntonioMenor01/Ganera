package com.ganera.core.facturacion;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StripeWebhookController {

    private final String webhookSecret;
    private final StripeWebhookService stripeWebhookService;

    public StripeWebhookController(@Value("${stripe.webhook-secret:}") String webhookSecret,
                                    StripeWebhookService stripeWebhookService) {
        this.webhookSecret = webhookSecret;
        this.stripeWebhookService = stripeWebhookService;
    }

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<Void> recibirEvento(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String firma) {

        Event evento;
        try {
            evento = Webhook.constructEvent(payload, firma, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().build();
        }

        stripeWebhookService.procesarEvento(evento);
        return ResponseEntity.ok().build();
    }
}
