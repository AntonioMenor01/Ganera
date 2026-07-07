package com.ganera.core.gestoria;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class GestoriaFilterIsolationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    @Test
    void unaGestoriaNoVeUsuariosDeOtraCuandoElFiltroEstaActivo() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B"));

        usuarioRepository.save(nuevoUsuario(gestoriaA, "a@gestoriaA.com"));
        usuarioRepository.save(nuevoUsuario(gestoriaB, "b@gestoriaB.com"));

        entityManager.flush();
        entityManager.clear();

        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("gestoriaFilter").setParameter("gestoriaId", gestoriaA.getId());

        List<Usuario> visibles = usuarioRepository.findAll();

        assertThat(visibles).hasSize(1);
        assertThat(visibles.get(0).getEmail()).isEqualTo("a@gestoriaA.com");
    }

    @Test
    void sinFiltroActivoSeVenTodasLasFilas() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B"));

        usuarioRepository.save(nuevoUsuario(gestoriaA, "a2@gestoriaA.com"));
        usuarioRepository.save(nuevoUsuario(gestoriaB, "b2@gestoriaB.com"));

        entityManager.flush();
        entityManager.clear();

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
