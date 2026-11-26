package com.ganera.core.auth;

import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.web.MotivoErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    /** Minimo de la contrasena nueva, igual que el registro (RegistroGestoriaValidacion). */
    static final int PASSWORD_LONGITUD_MINIMA = 8;

    private final AuthService authService;
    private final UsuarioRepository usuarioRepository;

    public AuthController(AuthService authService, UsuarioRepository usuarioRepository) {
        this.authService = authService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return authService.autenticar(request.email(), request.password())
                .map(token -> ResponseEntity.ok(new LoginResponse(token)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    /** El filtro gestoriaFilter ya está activo para esta request (TenantFilterActivationInterceptor,
     * Paso 1 Task 8) -- no hace falta ningun chequeo manual de gestoria_id aqui. */
    @GetMapping("/auth/me")
    public ResponseEntity<UsuarioActualResponse> me(@AuthenticationPrincipal GaneraUserPrincipal principal) {
        Usuario usuario = usuarioRepository.findById(principal.usuarioId()).orElseThrow();
        return ResponseEntity.ok(aResponse(usuario));
    }

    /**
     * Cambia la contrasena del usuario autenticado (pensado para la contrasena temporal del alta automatica,
     * pero sirve para cualquiera). Orden de comprobaciones: 400 {motivo} sin cuerpo / sin contrasena actual /
     * contrasena nueva invalida (en el controlador, antes de tocar la BD) -> 400 {motivo} si la actual no
     * coincide -> 404 sin cuerpo si el usuario del token no existe -> 200 con el usuario ya actualizado
     * (debeCambiarPassword en false). La respuesta nunca lleva la contrasena.
     */
    @PostMapping("/auth/cambiar-password")
    public ResponseEntity<?> cambiarPassword(@AuthenticationPrincipal GaneraUserPrincipal principal,
                                             @RequestBody(required = false) CambioPasswordRequest request) {
        if (request == null || request.passwordActual() == null || request.passwordActual().isBlank()) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse("Indica tu contraseña actual."));
        }
        if (request.passwordNueva() == null || request.passwordNueva().length() < PASSWORD_LONGITUD_MINIMA) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse(
                    "La contraseña nueva debe tener al menos " + PASSWORD_LONGITUD_MINIMA + " caracteres."));
        }
        try {
            Usuario usuario = authService.cambiarPassword(principal, request.passwordActual(), request.passwordNueva());
            return ResponseEntity.ok(aResponse(usuario));
        } catch (AuthService.PasswordActualIncorrectaException e) {
            return ResponseEntity.badRequest().body(new MotivoErrorResponse("La contraseña actual no es correcta."));
        } catch (AuthService.UsuarioNoEncontradoException e) {
            return ResponseEntity.status(404).build();
        }
    }

    private static UsuarioActualResponse aResponse(Usuario usuario) {
        return new UsuarioActualResponse(usuario.getId(), usuario.getEmail(), usuario.getNombre(),
                usuario.getGestoria().getId(), usuario.isActivo(), usuario.isDebeCambiarPassword());
    }
}
