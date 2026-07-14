package com.ganera.core.explotacion;

import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class ExplotacionControllerTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    @Test
    void listaSoloLasExplotacionesDeLaGestoriaConElFiltroDeTenantActivo() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B"));

        Ganadero ganaderoA = nuevoGanadero(gestoriaA, "Ganadero A");
        Ganadero ganaderoB = nuevoGanadero(gestoriaB, "Ganadero B");

        nuevaExplotacion(gestoriaA, ganaderoA, "ES010000000001", "Finca A1");
        nuevaExplotacion(gestoriaB, ganaderoB, "ES020000000001", "Finca B1");

        entityManager.flush();
        entityManager.clear();

        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("gestoriaFilter").setParameter("gestoriaId", gestoriaA.getId());

        ExplotacionController controller = new ExplotacionController(explotacionRepository);

        Page<ExplotacionResponse> pagina = controller.listar(PageRequest.of(0, 10));

        assertThat(pagina.getContent()).hasSize(1);
        assertThat(pagina.getContent().get(0).codigoRega()).isEqualTo("ES010000000001");
    }

    @Test
    void listaPaginadaDevuelveElResponseConDatosDelGanadero() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria paginacion"));
        Ganadero ganadero = nuevoGanadero(gestoria, "Ganadero Paginado");
        nuevaExplotacion(gestoria, ganadero, "ES030000000001", "Finca Paginada");

        ExplotacionController controller = new ExplotacionController(explotacionRepository);

        Page<ExplotacionResponse> pagina = controller.listar(PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        ExplotacionResponse respuesta = pagina.getContent().get(0);
        assertThat(respuesta.nombreGanadero()).isEqualTo("Ganadero Paginado");
        assertThat(respuesta.ganaderoId()).isEqualTo(ganadero.getId());
    }

    private Ganadero nuevoGanadero(Gestoria gestoria, String nombre) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre(nombre);
        return ganaderoRepository.save(ganadero);
    }

    private Explotacion nuevaExplotacion(Gestoria gestoria, Ganadero ganadero, String codigoRega, String nombre) {
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(codigoRega);
        explotacion.setNombre(nombre);
        return explotacionRepository.save(explotacion);
    }
}
