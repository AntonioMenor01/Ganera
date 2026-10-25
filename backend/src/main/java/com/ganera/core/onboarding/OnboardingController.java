package com.ganera.core.onboarding;

import com.ganera.core.facturacion.EstadoSuscripcion;
import com.ganera.core.facturacion.Suscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
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
    private final AuditoriaService auditoriaService;
    private final String onboardingSecret;

    public OnboardingController(
            GestoriaRepository gestoriaRepository,
            UsuarioRepository usuarioRepository,
            SuscripcionRepository suscripcionRepository,
            PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService,
            @Value("${ganera.onboarding.secret:}") String onboardingSecret) {
        this.gestoriaRepository = gestoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
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

        // Alta manual de una gestoria piloto: actor SISTEMA (no hay usuario autenticado todavia), la gestoria
        // de las tres filas es la recien creada. Todo en la misma transaccion del controlador.
        auditoriaService.registrar(AccionAuditoria.GESTORIA_CREADA, gestoria.getId(), Actor.sistema(),
                EntidadAuditada.GESTORIA, gestoria.getId(), null, null);
        auditoriaService.registrar(AccionAuditoria.USUARIO_CREADO, gestoria.getId(), Actor.sistema(),
                EntidadAuditada.USUARIO, usuario.getId(), null, DatosAuditoria.con(Clave.ACTIVO, Boolean.TRUE));
        auditoriaService.registrar(AccionAuditoria.SUSCRIPCION_CREADA, gestoria.getId(), Actor.sistema(),
                EntidadAuditada.SUSCRIPCION, suscripcion.getId(), null,
                DatosAuditoria.con(Clave.ESTADO, EstadoSuscripcion.ACTIVA));

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
