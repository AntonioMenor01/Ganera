package com.ganera.core.whatsapp;

import com.ganera.core.contacto.RecursoNoEncontradoException;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.web.MotivoErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * POST /tramites/{id}/respuestas-whatsapp (B2 reducido, D4/D14): un Usuario envia al Contacto que escribio
 * el Tramite un texto que ha visto y editado. Nada se envia solo.
 *
 * <p>Orden: 400 {motivo} AQUI, antes de la BD e igual para un tramite propio, ajeno o inexistente (sin
 * clave o clave que no es un UUID → sin texto → mas de 1600 caracteres → hueco ___ sin completar) → 503 si
 * el envio no esta configurado (sin crear nada) → 404 sin cuerpo → misma clave: 200 con la guardada →
 * 409 {motivo} → envio → 201. Un fallo de Twilio NO es un error HTTP: es un 201 con la respuesta en
 * FALLIDO/SIN_CONFIRMAR y su motivo. Quien envia es SIEMPRE el usuario del JWT. Sin comprobacion de
 * suscripcion (D11).
 */
@RestController
public class RespuestaWhatsappController {

    static final String MOTIVO_FALTA_CLAVE = "Falta la clave de la respuesta.";
    static final String MOTIVO_FALTA_TEXTO = "Escribe el texto de la respuesta.";
    static final String MOTIVO_TEXTO_LARGO = "La respuesta admite como máximo 1600 caracteres.";
    static final String MOTIVO_HUECO = "Completa el hueco ___ antes de enviar.";
    static final String MOTIVO_NO_CONFIGURADO =
            "El envío por WhatsApp no está configurado. Ponte en contacto con Ganera.";

    /** Limite de Twilio para el cuerpo de un mensaje (D4), en caracteres UTF-16 (String.length()). */
    static final int MAXIMO_CARACTERES = 1600;
    static final String HUECO = "___";
    private static final Pattern UUID_CANONICO =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final RespuestaWhatsappService respuestaWhatsappService;

    public RespuestaWhatsappController(RespuestaWhatsappService respuestaWhatsappService) {
        this.respuestaWhatsappService = respuestaWhatsappService;
    }

    /** Cuerpo opcional para Spring (required = false) a proposito: un POST sin cuerpo da nuestro 400. */
    @PostMapping("/tramites/{id}/respuestas-whatsapp")
    public ResponseEntity<?> responder(
            @AuthenticationPrincipal GaneraUserPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) RespuestaWhatsappRequest request) {
        String clave = request != null ? request.claveIdempotencia() : null;
        String texto = request != null ? request.texto() : null;
        String motivo = motivoInvalido(clave, texto);
        if (motivo != null) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(motivo));
        }
        if (!respuestaWhatsappService.configurado()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new MotivoErrorResponse(MOTIVO_NO_CONFIGURADO));
        }
        try {
            RespuestaWhatsappService.Respuesta resultado = respuestaWhatsappService.responder(
                    principal.gestoriaId(), principal.usuarioId(), id, texto, clave.toLowerCase(Locale.ROOT));
            return ResponseEntity.status(resultado.nueva() ? HttpStatus.CREATED : HttpStatus.OK)
                    .body(resultado.respuesta());
        } catch (RecursoNoEncontradoException e) {
            return ResponseEntity.notFound().build();
        } catch (RespuestaWhatsappConflictoException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new MotivoErrorResponse(e.getMessage()));
        }
    }

    /** Validacion del cuerpo antes de tocar la BD; null si es valido. */
    static String motivoInvalido(String clave, String texto) {
        if (clave == null || !UUID_CANONICO.matcher(clave).matches()) {
            return MOTIVO_FALTA_CLAVE;
        }
        if (texto == null || texto.isBlank()) {
            return MOTIVO_FALTA_TEXTO;
        }
        if (texto.length() > MAXIMO_CARACTERES) {
            return MOTIVO_TEXTO_LARGO;
        }
        if (texto.contains(HUECO)) {
            return MOTIVO_HUECO;
        }
        return null;
    }
}
