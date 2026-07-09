package com.ganera.core.auth;

import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Optional<String> autenticar(String email, String password) {
        // BCrypt lanza IllegalArgumentException con password null: sin este guard, un body
        // malformado responderia 500 solo cuando el email existe (oraculo de enumeracion).
        if (email == null || password == null) {
            return Optional.empty();
        }
        return usuarioRepository.findByEmail(email)
                .filter(Usuario::isActivo)
                .filter(usuario -> passwordEncoder.matches(password, usuario.getPasswordHash()))
                .map(usuario -> jwtService.generarToken(
                        new GaneraUserPrincipal(usuario.getId(), usuario.getGestoria().getId(), usuario.getEmail())));
    }
}
