package com.ganera.core.auth;

import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

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
        return ResponseEntity.ok(new UsuarioActualResponse(
                usuario.getId(), usuario.getEmail(), usuario.getNombre(),
                usuario.getGestoria().getId(), usuario.isActivo()));
    }
}
