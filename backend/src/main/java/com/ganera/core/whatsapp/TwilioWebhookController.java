package com.ganera.core.whatsapp;

import com.twilio.security.RequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.TreeMap;

@RestController
public class TwilioWebhookController {

    private final String authToken;
    private final MensajeCampoRepository mensajeCampoRepository;

    public TwilioWebhookController(
            @Value("${twilio.auth-token:}") String authToken,
            MensajeCampoRepository mensajeCampoRepository) {
        this.authToken = authToken;
        this.mensajeCampoRepository = mensajeCampoRepository;
    }

    @PostMapping(value = "/webhooks/twilio/whatsapp", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<Void> recibirMensaje(
            HttpServletRequest request,
            @RequestHeader(value = "X-Twilio-Signature", required = false) String firma,
            @RequestParam("MessageSid") String messageSid,
            @RequestParam("From") String from,
            @RequestParam("Body") String body) {

        // RequestValidator se construye por request (no en el constructor / campo estático):
        // con TWILIO_AUTH_TOKEN en blanco (aun sin cuenta de Twilio configurada), el SDK
        // lanza IllegalArgumentException si se construye al arrancar la app.
        if (!authToken.isBlank()) {
            RequestValidator validator = new RequestValidator(authToken);
            Map<String, String> params = new TreeMap<>();
            params.put("MessageSid", messageSid);
            params.put("From", from);
            params.put("Body", body);
            boolean firmaValida = firma != null
                    && validator.validate(request.getRequestURL().toString(), params, firma);
            if (!firmaValida) {
                return ResponseEntity.status(403).build();
            }
        }

        if (debeGuardarNuevoMensaje(messageSid)) {
            MensajeCampo mensaje = new MensajeCampo();
            mensaje.setMessageSid(messageSid);
            mensaje.setTelefonoOrigen(from);
            mensaje.setCuerpo(body);
            mensajeCampoRepository.save(mensaje);
        }

        return ResponseEntity.ok().build();
    }

    /** Lógica de idempotencia pura, sin tocar el SDK de Twilio: testeable sin mocks. */
    boolean debeGuardarNuevoMensaje(String messageSid) {
        return !mensajeCampoRepository.existsByMessageSid(messageSid);
    }
}
