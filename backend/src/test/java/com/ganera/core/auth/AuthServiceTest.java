package com.ganera.core.auth;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class AuthServiceTest {

    private static final String JWT_SECRET_DE_PRUEBA = "test-jwt-secret-de-al-menos-32-bytes-de-longitud";

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = new JwtService(JWT_SECRET_DE_PRUEBA, 480);

    private Usuario crearUsuario(String email, String passwordEnClaro, boolean activo) {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria de auth " + email));
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode(passwordEnClaro));
        usuario.setNombre("Usuario de prueba");
        usuario.setActivo(activo);
        return usuarioRepository.save(usuario);
    }

    @Test
    void credencialesValidasYUsuarioActivoDevuelveToken() {
        AuthService authService = new AuthService(usuarioRepository, passwordEncoder, jwtService);
        crearUsuario("valido@gestoria.com", "password-correcta", true);

        Optional<String> token = authService.autenticar("valido@gestoria.com", "password-correcta");

        assertThat(token).isPresent();
    }

    @Test
    void passwordIncorrectaNoDevuelveToken() {
        AuthService authService = new AuthService(usuarioRepository, passwordEncoder, jwtService);
        crearUsuario("passwordmala@gestoria.com", "password-correcta", true);

        Optional<String> token = authService.autenticar("passwordmala@gestoria.com", "password-incorrecta");

        assertThat(token).isEmpty();
    }

    @Test
    void emailDesconocidoNoDevuelveToken() {
        AuthService authService = new AuthService(usuarioRepository, passwordEncoder, jwtService);

        Optional<String> token = authService.autenticar("no-existe@gestoria.com", "cualquier-password");

        assertThat(token).isEmpty();
    }

    @Test
    void usuarioInactivoNoDevuelveTokenAunqueLaPasswordSeaCorrecta() {
        AuthService authService = new AuthService(usuarioRepository, passwordEncoder, jwtService);
        crearUsuario("inactivo@gestoria.com", "password-correcta", false);

        Optional<String> token = authService.autenticar("inactivo@gestoria.com", "password-correcta");

        assertThat(token).isEmpty();
    }
}
