package com.ganera.core.shared.tenant;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(TenantFilterActivationInterceptor.class)
class TenantFilterActivationInterceptorTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private TenantFilterActivationInterceptor interceptor;

    @AfterEach
    void limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activaElFiltroConElGestoriaIdDelPrincipalAutenticado() throws Exception {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B"));

        usuarioRepository.save(nuevoUsuario(gestoriaA, "a@gestoriaA.com"));
        usuarioRepository.save(nuevoUsuario(gestoriaB, "b@gestoriaB.com"));

        entityManager.flush();
        entityManager.clear();

        GaneraUserPrincipal principal = new GaneraUserPrincipal(1L, gestoriaA.getId(), "empleado@gestoria.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        interceptor.preHandle(null, null, null);

        List<Usuario> visibles = usuarioRepository.findAll();

        assertThat(visibles).hasSize(1);
        assertThat(visibles.get(0).getEmail()).isEqualTo("a@gestoriaA.com");
    }

    @Test
    void noActivaNadaSinAutenticacionYNoLanzaExcepcion() throws Exception {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B"));

        usuarioRepository.save(nuevoUsuario(gestoriaA, "a2@gestoriaA.com"));
        usuarioRepository.save(nuevoUsuario(gestoriaB, "b2@gestoriaB.com"));

        entityManager.flush();
        entityManager.clear();

        interceptor.preHandle(null, null, null);

        assertThat(usuarioRepository.findAll()).hasSize(2);
    }

    private static Usuario nuevoUsuario(Gestoria gestoria, String email) {
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash("hash");
        usuario.setNombre("Usuario de prueba");
        return usuario;
    }
}
