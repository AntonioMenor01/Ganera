package com.ganera.core.explotacion;

import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import com.ganera.core.shared.web.MotivoErrorResponse;
import com.ganera.core.shared.web.OrdenacionPermitida;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

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
    private AnimalRepository animalRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    /**
     * Decision 21 / Global Constraints: el listado lleva el gestoriaId del JWT como parametro real
     * de la query. Aqui el gestoriaFilter NO se activa a proposito (en @DataJpaTest no lo activa
     * nadie): si el controlador volviera a depender solo del filtro ambiente (findAll), veria
     * tambien la Explotacion de B y este test fallaria.
     */
    @Test
    void listaSoloLasExplotacionesDeLaGestoriaDelUsuarioSinDependerDelFiltroAmbiente() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B"));

        Ganadero ganaderoA = nuevoGanadero(gestoriaA, "Ganadero A");
        Ganadero ganaderoB = nuevoGanadero(gestoriaB, "Ganadero B");

        nuevaExplotacion(gestoriaA, ganaderoA, "ES010000000001", "Finca A1");
        nuevaExplotacion(gestoriaB, ganaderoB, "ES020000000001", "Finca B1");

        entityManager.flush();
        entityManager.clear();

        ExplotacionController controller = new ExplotacionController(explotacionRepository, animalRepository);

        Page<ExplotacionResponse> pagina = pagina(controller.listar(principal(gestoriaA), PageRequest.of(0, 10)));
        Page<ExplotacionResponse> paginaB = pagina(controller.listar(principal(gestoriaB), PageRequest.of(0, 10)));

        assertThat(pagina.getContent()).extracting(ExplotacionResponse::codigoRega).containsExactly("ES010000000001");
        assertThat(paginaB.getContent()).extracting(ExplotacionResponse::codigoRega).containsExactly("ES020000000001");
    }

    @Test
    void ordenarPorUnCampoNoPermitidoDevuelve400ConMotivo() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria orden no permitido"));
        ExplotacionController controller = new ExplotacionController(explotacionRepository, animalRepository);

        ResponseEntity<?> respuesta = controller.listar(principal(gestoria),
                PageRequest.of(0, 10, Sort.by("ganadero.ovzUsuario")));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody()).isEqualTo(new MotivoErrorResponse(OrdenacionPermitida.MOTIVO));
    }

    @Test
    void listaPaginadaDevuelveElResponseConDatosDelGanadero() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria paginacion"));
        Ganadero ganadero = nuevoGanadero(gestoria, "Ganadero Paginado");
        nuevaExplotacion(gestoria, ganadero, "ES030000000001", "Finca Paginada");

        ExplotacionController controller = new ExplotacionController(explotacionRepository, animalRepository);

        Page<ExplotacionResponse> pagina = pagina(controller.listar(principal(gestoria), PageRequest.of(0, 10)));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        ExplotacionResponse respuesta = pagina.getContent().get(0);
        assertThat(respuesta.nombreGanadero()).isEqualTo("Ganadero Paginado");
        assertThat(respuesta.ganaderoId()).isEqualTo(ganadero.getId());
    }

    private static GaneraUserPrincipal principal(Gestoria gestoria) {
        return new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com");
    }

    @SuppressWarnings("unchecked")
    private static Page<ExplotacionResponse> pagina(ResponseEntity<?> respuesta) {
        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        return (Page<ExplotacionResponse>) respuesta.getBody();
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
