package com.ganera.core.onboarding;

import com.ganera.core.facturacion.EstadoSuscripcion;
import com.ganera.core.facturacion.Suscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

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
    private final PasswordEncoder passwordEncoder;
    private final String onboardingSecret;

    public OnboardingController(
            GestoriaRepository gestoriaRepository,
            UsuarioRepository usuarioRepository,
            SuscripcionRepository suscripcionRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ganera.onboarding.secret:}") String onboardingSecret) {
        this.gestoriaRepository = gestoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.suscripcionRepository = suscripcionRepository;
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
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcionRepository.save(suscripcion);

        return ResponseEntity.ok(new OnboardingResponse(gestoria.getId(), usuario.getId()));
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
