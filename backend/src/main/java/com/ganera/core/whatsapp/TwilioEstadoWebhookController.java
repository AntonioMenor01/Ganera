package com.ganera.core.whatsapp;

import com.twilio.security.RequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * Callback de estado de Twilio para las respuestas por WhatsApp (B2 reducido, T4): {@code POST
 * /webhooks/twilio/whatsapp/estado}, sin JWT. Orden de comprobaciones:
 * <ol>
 *   <li>Tamano: mas de 8 KB → 413, antes de leer el cuerpo ({@link LimiteCuerpoCallbackEstadoFilter}, antes de
 *       Spring Security).</li>
 *   <li>Sin {@code TWILIO_AUTH_TOKEN} o sin {@code TWILIO_STATUS_CALLBACK_URL} valida → 503 y nada se toca
 *       (fail-closed, D3).</li>
 *   <li>Firma {@code X-Twilio-Signature} ausente o invalida → 403. Se valida con TODOS los parametros y con la URL
 *       CONFIGURADA (la misma que el envio pide a Twilio), nunca con {@code getRequestURL()}.
 *       {@link RequestValidator} se construye por peticion (footgun de Twilio: con el token en blanco lanza al
 *       construirse y la app tiene que arrancar sin el).</li>
 *   <li>Sin {@code MessageSid} o sin {@code MessageStatus} → 400.</li>
 *   <li>{@link EstadoRespuestaWhatsappService#aplicar} con SOLO {@code MessageSid}, {@code MessageStatus} y
 *       {@code ErrorCode}; ningun otro parametro se lee. 200 sin cuerpo, cambie algo o no (SID desconocido,
 *       estado que no avanza...): Twilio no tiene nada que reintentar.</li>
 * </ol>
 * Logs: nunca parametros de la peticion salvo el SID (en el servicio); nada de telefonos ni texto.
 */
@Slf4j
@RestController
public class TwilioEstadoWebhookController {

    private final String authToken;
    private final URI urlCallback;
    private final EstadoRespuestaWhatsappService estadoRespuestaWhatsappService;

    public TwilioEstadoWebhookController(
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${ganera.twilio.status-callback-url:}") String urlCallback,
            EstadoRespuestaWhatsappService estadoRespuestaWhatsappService) {
        this.authToken = authToken == null ? "" : authToken.trim();
        this.urlCallback = CallbackEstadoWhatsapp.urlCallback(urlCallback);
        this.estadoRespuestaWhatsappService = estadoRespuestaWhatsappService;
        if (this.urlCallback == null) {
            log.warn("TWILIO_STATUS_CALLBACK_URL vacia o no valida: las respuestas por WhatsApp no piden callback "
                    + "de estado y su endpoint respondera 503");
        } else {
            log.info("Callback de estado de WhatsApp: las firmas de Twilio se validan contra el host {}",
                    this.urlCallback.getHost());
        }
    }

    @PostMapping(value = LimiteCuerpoCallbackEstadoFilter.RUTA, consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<Void> recibirEstado(
            HttpServletRequest request,
            @RequestHeader(value = "X-Twilio-Signature", required = false) String firma) {

        if (authToken.isEmpty() || urlCallback == null) {
            log.warn("Callback de estado de Twilio sin configurar (falta TWILIO_AUTH_TOKEN o "
                    + "TWILIO_STATUS_CALLBACK_URL): rechazado con 503");
            return ResponseEntity.status(503).build();
        }

        Map<String, String> parametros = TwilioWebhookController.primerValorDeCadaParametro(request.getParameterMap());

        boolean firmaValida = firma != null && !firma.isBlank()
                && new RequestValidator(authToken).validate(urlCallback.toString(), parametros, firma);
        if (!firmaValida) {
            log.warn("Callback de estado de Twilio con firma ausente o invalida: rechazado con 403");
            return ResponseEntity.status(403).build();
        }

        String messageSid = parametros.get("MessageSid");
        String messageStatus = parametros.get("MessageStatus");
        if (messageSid == null || messageSid.isBlank() || messageStatus == null || messageStatus.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        estadoRespuestaWhatsappService.aplicar(messageSid.strip(), messageStatus, parametros.get("ErrorCode"));
        return ResponseEntity.ok().build();
    }
}
