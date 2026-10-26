package com.ganera.core.onboarding;

import com.ganera.core.facturacion.EnlaceStripeService;
import com.ganera.core.facturacion.EstadoSuscripcion;
import com.ganera.core.facturacion.Suscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.stripe.exception.StripeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Alta manual de Gestorias piloto. SOLUCION TEMPORAL DE BOOTSTRAP: no hay
 * registro publico ni panel de administracion todavia, asi que este
 * endpoint se protege con un secreto compartido simple en vez de un JWT
 * (no existe ningun Usuario admin todavia en el momento en que se llama).
 * Sustituir por un panel de administracion real con su propio control de
 * acceso mas adelante -- no construir mas funcionalidad encima de este
 * patron de secreto compartido.
 */
@RestController
public class OnboardingController {

    private final GestoriaRepository gestoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final EnlaceStripeService enlaceStripeService;
    private final PasswordEncoder passwordEncoder;
    private final String onboardingSecret;

    public OnboardingController(
            GestoriaRepository gestoriaRepository,
            UsuarioRepository usuarioRepository,
            SuscripcionRepository suscripcionRepository,
            EnlaceStripeService enlaceStripeService,
            PasswordEncoder passwordEncoder,
            @Value("${ganera.onboarding.secret:}") String onboardingSecret) {
        this.gestoriaRepository = gestoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.enlaceStripeService = enlaceStripeService;
        this.passwordEncoder = passwordEncoder;
        this.onboardingSecret = onboardingSecret;
    }

    @PostMapping("/internal/onboarding/gestoria")
    @Transactional
    public ResponseEntity<OnboardingResponse> crearGestoria(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secretoRecibido,
            @RequestBody OnboardingRequest request) {

        if (!secretoValido(secretoRecibido)) {
            return ResponseEntity.status(401).build();
        }

        String stripeSubscriptionId = normalizar(request.stripeSubscriptionId());
        // Si se pide enlazar con Stripe pero no esta configurado, 503 explicito ANTES de crear nada.
        if (stripeSubscriptionId != null && !enlaceStripeService.stripeConfigurado()) {
            return ResponseEntity.status(503).build();
        }

        Gestoria gestoria = gestoriaRepository.save(new Gestoria(request.nombreGestoria()));

        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(request.emailUsuario());
        usuario.setPasswordHash(passwordEncoder.encode(request.passwordUsuario()));
        usuario.setNombre(request.nombreUsuario());
        usuario.setActivo(true);
        usuarioRepository.save(usuario);

        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        if (stripeSubscriptionId == null) {
            // Piloto directo, sin Stripe: ACTIVA como hasta ahora.
            suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        } else {
            // Alta de la landing: enlaza con la Subscription de Stripe y recalcula el estado desde
            // Stripe (no depende del orden de los webhooks). Un fallo al hablar con Stripe revierte
            // toda la transaccion (gestoria + usuario + suscripcion) por ser @Transactional.
            try {
                enlaceStripeService.enlazar(suscripcion, stripeSubscriptionId);
            } catch (StripeException e) {
                // Rollback de toda la transaccion: se lanza (no se devuelve) para que @Transactional
                // revierta la gestoria y el usuario ya creados. 502: fallo hablando con Stripe.
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se ha podido consultar la suscripcion en Stripe");
            }
        }
        suscripcionRepository.save(suscripcion);

        return ResponseEntity.ok(new OnboardingResponse(gestoria.getId(), usuario.getId()));
    }

    private static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    /** Comparacion en tiempo constante; falla cerrado si ONBOARDING_SECRET no esta configurado. */
    boolean secretoValido(String secretoRecibido) {
        if (onboardingSecret.isBlank() || secretoRecibido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                onboardingSecret.getBytes(StandardCharsets.UTF_8),
                secretoRecibido.getBytes(StandardCharsets.UTF_8));
    }
}
