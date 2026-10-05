package com.ganera.core.whatsapp;

import com.twilio.security.RequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Webhook de WhatsApp de Twilio. Orden de comprobaciones, antes de mirar ningun dato del mensaje:
 * <ol>
 *   <li>Sin {@code TWILIO_AUTH_TOKEN} o sin {@code TWILIO_WEBHOOK_URL} → 503 y no se guarda nada
 *       (fail-closed: sin forma de validar la firma no se acepta ningun POST).</li>
 *   <li>Firma ausente o que no valida → 403.</li>
 *   <li>Solo entonces se leen MessageSid/From/Body/NumMedia (400 si faltan MessageSid o From) y
 *       {@link MensajeEntranteService} guarda el {@link MensajeCampo} y, si procede, crea el Tramite
 *       (idempotente por MessageSid).</li>
 * </ol>
 * Respuesta: TwiML ({@code text/xml}). Con Tramite creado, el acuse {@link #ACUSE_RECIBO} en un
 * {@code <Message>} (lo envia Twilio, sin llamar a su API); con numero desconocido, Contacto inactivo
 * o MessageSid repetido, {@code <Response/>} vacio: ni acuse ni nada. Si falla la creacion del
 * Tramite, el mensaje se guarda aparte con ERROR_RECEPCION ({@link MensajeRescateService}) y tambien
 * {@code <Response/>}; si ni eso se puede, 500.
 * La firma se valida con <b>todos</b> los parametros del formulario (Twilio firma todos:
 * AccountSid, To, NumMedia, ProfileName, WaId...) y con la URL publica configurada, nunca con
 * {@code getRequestURL()}: detras de un tunel o proxy esa es {@code http://localhost:8080/...}, no
 * la URL https que Twilio firmo. Por eso la URL configurada no debe llevar query string (sus
 * parametros entrarian en getParameterMap y la firma no cuadraria nunca).
 */
@Slf4j
@RestController
public class TwilioWebhookController {

    /** Acuse de recibo al Contacto (D7 del Prompt B1), solo cuando se ha creado un Tramite. */
    static final String ACUSE_RECIBO = "Recibido, tu gestoría lo revisará.";

    private static final String DECLARACION_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    static final String TWIML_VACIO = DECLARACION_XML + "<Response/>";
    static final String TWIML_ACUSE = DECLARACION_XML + "<Response><Message>" + escaparXml(ACUSE_RECIBO)
            + "</Message></Response>";
    private static final MediaType TEXT_XML_UTF8 = MediaType.parseMediaType("text/xml;charset=UTF-8");

    private final String authToken;
    private final String webhookUrl;
    private final MensajeEntranteService mensajeEntranteService;
    private final MensajeRescateService mensajeRescateService;
    private final MensajeCampoRepository mensajeCampoRepository;

    public TwilioWebhookController(
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${ganera.twilio.webhook-url:}") String webhookUrl,
            MensajeEntranteService mensajeEntranteService,
            MensajeRescateService mensajeRescateService,
            MensajeCampoRepository mensajeCampoRepository) {
        this.authToken = authToken == null ? "" : authToken.trim();
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.mensajeEntranteService = mensajeEntranteService;
        this.mensajeRescateService = mensajeRescateService;
        this.mensajeCampoRepository = mensajeCampoRepository;
        avisarDeLaConfiguracion();
    }

    /**
     * Al arrancar: con que host se validaran las firmas (solo el host, nunca path, query ni token),
     * para que en el smoke una URL mal escrita no se confunda con firmas falsas (las dos dan 403).
     */
    private void avisarDeLaConfiguracion() {
        if (webhookUrl.isEmpty()) {
            log.warn("TWILIO_WEBHOOK_URL no esta configurada: el webhook de WhatsApp respondera 503");
            return;
        }
        String host = null;
        try {
            host = URI.create(webhookUrl).getHost();
        } catch (IllegalArgumentException e) {
            // Sin repetir el valor: podria no ser una URL sino otra cosa pegada por error.
        }
        if (host == null) {
            log.warn("TWILIO_WEBHOOK_URL no parece una URL valida (no se reconoce el host): "
                    + "todas las firmas daran 403");
        } else {
            log.info("Webhook de WhatsApp: las firmas de Twilio se validan contra el host {}", host);
        }
    }

    @PostMapping(value = "/webhooks/twilio/whatsapp", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<String> recibirMensaje(
            HttpServletRequest request,
            @RequestHeader(value = "X-Twilio-Signature", required = false) String firma) {

        if (authToken.isEmpty() || webhookUrl.isEmpty()) {
            // Sin datos del mensaje en el log (ni texto ni telefono): solo que falta configuracion.
            log.warn("Webhook de Twilio sin configurar (falta TWILIO_AUTH_TOKEN o TWILIO_WEBHOOK_URL): "
                    + "mensaje rechazado con 503 y no guardado");
            return ResponseEntity.status(503).build();
        }

        Map<String, String> parametros = primerValorDeCadaParametro(request.getParameterMap());

        // RequestValidator se construye por peticion (nunca en el constructor ni en un campo
        // estatico): con el token en blanco el SDK lanza IllegalArgumentException al construirlo, y
        // la app tiene que arrancar sin TWILIO_AUTH_TOKEN.
        boolean firmaValida = firma != null && !firma.isBlank()
                && new RequestValidator(authToken).validate(webhookUrl, parametros, firma);
        if (!firmaValida) {
            log.warn("Webhook de Twilio con firma ausente o invalida: rechazado con 403");
            return ResponseEntity.status(403).build();
        }

        String messageSid = parametros.get("MessageSid");
        String from = parametros.get("From");
        if (messageSid == null || messageSid.isBlank() || from == null || from.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        // Un mensaje solo con foto o audio no trae Body.
        MensajeEntrante entrante = MensajeEntrante.desdeFormulario(
                messageSid, from, parametros.get("Body"), parametros.get("NumMedia"));

        RecepcionMensaje recepcion;
        try {
            recepcion = mensajeEntranteService.registrar(entrante);
        } catch (RuntimeException e) {
            // Fuera del @Transactional (ya deshecho entero). Una violacion de integridad solo es un
            // duplicado si el MessageSid esta de verdad guardado (otra entrega simultanea gano la
            // carrera). Cualquier otro fallo: se rescata el mensaje para no perderlo.
            if (e instanceof DataIntegrityViolationException && mensajeCampoRepository.existsByMessageSid(messageSid)) {
                recepcion = RecepcionMensaje.deDuplicado();
            } else {
                return rescatar(entrante, e);
            }
        }

        if (recepcion.duplicado()) {
            log.info("Entrega repetida de un mensaje de WhatsApp ya recibido: se ignora sin acuse");
        }
        return twiml(recepcion.acusar() ? TWIML_ACUSE : TWIML_VACIO);
    }

    /**
     * Fallo la recepcion normal: se guarda el mensaje aparte (ERROR_RECEPCION, sin Tramite) y se
     * responde 200 con {@code <Response/>} vacio (no hay Tramite, asi que no hay acuse). Un reintento
     * posterior de Twilio con el mismo MessageSid sera un duplicado: aceptable, el mensaje ya esta
     * guardado para revisarlo a mano. Si el rescate tambien falla (p. ej. BD caida), 500.
     * Los logs llevan solo el MessageSid y el tipo de excepcion: nunca su mensaje, que puede llevar
     * el texto o el telefono (p. ej. el detalle de una violacion de PostgreSQL).
     */
    private ResponseEntity<String> rescatar(MensajeEntrante entrante, RuntimeException fallo) {
        try {
            mensajeRescateService.guardarConError(entrante);
        } catch (RuntimeException falloDelRescate) {
            log.error("No se pudo recibir el mensaje de WhatsApp ni guardarlo aparte: messageSid={}, "
                            + "excepcion={}, excepcionDelRescate={}; responde 500",
                    entrante.messageSid(), fallo.getClass().getName(), falloDelRescate.getClass().getName());
            return ResponseEntity.status(500).build();
        }
        log.error("No se pudo crear el tramite del mensaje de WhatsApp; guardado con resultado {}: "
                        + "messageSid={}, excepcion={}",
                ResultadoMensaje.ERROR_RECEPCION, entrante.messageSid(), fallo.getClass().getName());
        return twiml(TWIML_VACIO);
    }

    private static ResponseEntity<String> twiml(String cuerpo) {
        return ResponseEntity.ok().contentType(TEXT_XML_UTF8).body(cuerpo);
    }

    /** Escapa un texto para meterlo en un elemento XML. */
    static String escaparXml(String texto) {
        StringBuilder resultado = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '&' -> resultado.append("&amp;");
                case '<' -> resultado.append("&lt;");
                case '>' -> resultado.append("&gt;");
                case '"' -> resultado.append("&quot;");
                case '\'' ->resultado.append("&apos;");
                default -> resultado.append(c);
            }
        }
        return resultado.toString();
    }

    /** Twilio manda cada parametro una sola vez; si llegara repetido, cuenta el primer valor. */
    static Map<String, String> primerValorDeCadaParametro(Map<String, String[]> parameterMap) {
        Map<String, String> resultado = new HashMap<>();
        parameterMap.forEach((nombre, valores) -> {
            if (valores != null && valores.length > 0) {
                resultado.put(nombre, valores[0]);
            }
        });
        return resultado;
    }
}
