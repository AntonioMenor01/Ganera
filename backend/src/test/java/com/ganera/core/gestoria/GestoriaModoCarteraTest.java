package com.ganera.core.gestoria;

import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class GestoriaModoCarteraTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void modoCarteraPorDefectoEsFalse() {
        Gestoria guardada = gestoriaRepository.save(new Gestoria("Gestoria por defecto"));

        entityManager.flush();
        entityManager.clear();

        Gestoria releida = gestoriaRepository.findById(guardada.getId()).orElseThrow();
        assertThat(releida.isModoCartera()).isFalse();
    }

    @Test
    void modoCarteraSePuedeActivarYPersiste() {
        Gestoria gestoria = new Gestoria("Gestoria con cartera");
        gestoria.setModoCartera(true);
        Gestoria guardada = gestoriaRepository.save(gestoria);

        entityManager.flush();
        entityManager.clear();

        Gestoria releida = gestoriaRepository.findById(guardada.getId()).orElseThrow();
        assertThat(releida.isModoCartera()).isTrue();
    }

    @Test
    void usuarioExplotacionSePersisteComoScaffoldingInerte() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria cartera"));

        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail("empleado@cartera.com");
        usuario.setPasswordHash("hash");
        usuario.setNombre("Empleado de cartera");
        usuarioRepository.save(usuario);

        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero de prueba");
        ganaderoRepository.save(ganadero);

        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega("ES-CARTERA-001");
        explotacion.setNombre("Explotacion de cartera");
        explotacionRepository.save(explotacion);

        UsuarioExplotacion vinculo = new UsuarioExplotacion();
        vinculo.setUsuario(usuario);
        vinculo.setExplotacion(explotacion);
        entityManager.persist(vinculo);

        entityManager.flush();
        entityManager.clear();

        UsuarioExplotacion releido = entityManager.find(UsuarioExplotacion.class, vinculo.getId());
        assertThat(releido.getUsuario().getId()).isEqualTo(usuario.getId());
        assertThat(releido.getExplotacion().getId()).isEqualTo(explotacion.getId());
    }
}
