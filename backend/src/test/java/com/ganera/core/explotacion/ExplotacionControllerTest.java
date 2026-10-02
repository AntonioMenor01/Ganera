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

import java.util.List;

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

        Page<ExplotacionResponse> pagina = pagina(controller.listar(principal(gestoriaA), null, PageRequest.of(0, 10)));
        Page<ExplotacionResponse> paginaB = pagina(controller.listar(principal(gestoriaB), null, PageRequest.of(0, 10)));

        assertThat(pagina.getContent()).extracting(ExplotacionResponse::codigoRega).containsExactly("ES010000000001");
        assertThat(paginaB.getContent()).extracting(ExplotacionResponse::codigoRega).containsExactly("ES020000000001");
    }

    @Test
    void ordenarPorUnCampoNoPermitidoDevuelve400ConMotivo() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria orden no permitido"));
        ExplotacionController controller = new ExplotacionController(explotacionRepository, animalRepository);

        ResponseEntity<?> respuesta = controller.listar(principal(gestoria), null,
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

        Page<ExplotacionResponse> pagina = pagina(controller.listar(principal(gestoria), null, PageRequest.of(0, 10)));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        ExplotacionResponse respuesta = pagina.getContent().get(0);
        assertThat(respuesta.nombreGanadero()).isEqualTo("Ganadero Paginado");
        assertThat(respuesta.ganaderoId()).isEqualTo(ganadero.getId());
    }

    // --- GET /explotaciones/{id} ---

    @Test
    void detalleDeExplotacionPropiaDevuelveSuResponse() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria detalle"));
        Ganadero ganadero = nuevoGanadero(gestoria, "Ganadero Detalle");
        Explotacion explotacion = nuevaExplotacion(gestoria, ganadero, "ES040000000001", "Finca Detalle");
        entityManager.flush();
        entityManager.clear();

        ResponseEntity<?> respuesta = controlador().detalle(principal(gestoria), explotacion.getId());

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isEqualTo(new ExplotacionResponse(
                explotacion.getId(), "ES040000000001", "Finca Detalle", ganadero.getId(), "Ganadero Detalle"));
    }

    /** Sin gestoriaFilter activo (nadie lo activa en @DataJpaTest): si el detalle usara findById a
     * secas, devolveria la Explotacion de A a B y este test fallaria. */
    @Test
    void detalleDeExplotacionDeOtraGestoriaOInexistenteDevuelve404SinCuerpo() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria detalle A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria detalle B"));
        Explotacion deA = nuevaExplotacion(gestoriaA, nuevoGanadero(gestoriaA, "Ganadero A"), "ES040000000002", "Finca A");
        entityManager.flush();
        entityManager.clear();

        ResponseEntity<?> ajena = controlador().detalle(principal(gestoriaB), deA.getId());
        ResponseEntity<?> inexistente = controlador().detalle(principal(gestoriaA), 999_999L);

        assertThat(ajena.getStatusCode().value()).isEqualTo(404);
        assertThat(ajena.getBody()).isNull();
        assertThat(inexistente.getStatusCode().value()).isEqualTo(404);
        assertThat(inexistente.getBody()).isNull();
    }

    // --- GET /explotaciones?q= ---

    @Test
    void qBuscaPorCodigoRegaSinDistinguirMayusculas() {
        Gestoria gestoria = gestoriaConExplotacionesDeBusqueda();

        assertThat(codigos(gestoria, "es0500000000")).containsExactly("ES050000000001", "ES050000000002", "ES050000000003");
        assertThat(codigos(gestoria, "0002")).containsExactly("ES050000000002");
    }

    @Test
    void qBuscaPorNombreDeLaExplotacionSinDistinguirMayusculas() {
        Gestoria gestoria = gestoriaConExplotacionesDeBusqueda();

        assertThat(codigos(gestoria, "ROBLE")).containsExactly("ES050000000001");
        assertThat(codigos(gestoria, "finca")).containsExactly("ES050000000001", "ES050000000002", "ES050000000003");
    }

    @Test
    void qBuscaPorNombreDelGanadero() {
        Gestoria gestoria = gestoriaConExplotacionesDeBusqueda();

        // "Pastora Lucia" solo es el nombre del Ganadero de la 3: no aparece en el REGA ni en el nombre de la finca.
        assertThat(codigos(gestoria, "PASTORA lu")).containsExactly("ES050000000003");
    }

    @Test
    void qNoQuitaAcentos() {
        Gestoria gestoria = gestoriaConExplotacionesDeBusqueda();

        assertThat(codigos(gestoria, "Lucía")).isEmpty();
        assertThat(codigos(gestoria, "Lucia")).containsExactly("ES050000000003");
    }

    @Test
    void qTrataPorcentajeGuionBajoYCaracterDeEscapeComoLiterales() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria comodines"));
        Ganadero ganadero = nuevoGanadero(gestoria, "Ganadero Comodines");
        nuevaExplotacion(gestoria, ganadero, "ES060000000001", "Finca 100% Bio");
        nuevaExplotacion(gestoria, ganadero, "ES060000000002", "Finca_Norte");
        nuevaExplotacion(gestoria, ganadero, "ES060000000003", "Finca Ole!");
        nuevaExplotacion(gestoria, ganadero, "ES060000000004", "Finca C:\\Sur");
        nuevaExplotacion(gestoria, ganadero, "ES060000000005", "Finca Normal");
        entityManager.flush();
        entityManager.clear();

        assertThat(codigos(gestoria, "%")).containsExactly("ES060000000001");
        assertThat(codigos(gestoria, "_")).containsExactly("ES060000000002");
        assertThat(codigos(gestoria, "!")).containsExactly("ES060000000003");
        assertThat(codigos(gestoria, "\\")).containsExactly("ES060000000004");
        assertThat(codigos(gestoria, "!%")).isEmpty();
        assertThat(codigos(gestoria, "100_")).isEmpty(); // con "_" comodin coincidiria con "100%"
    }

    @Test
    void qAusenteVacioOEnBlancoNoFiltra() {
        Gestoria gestoria = gestoriaConExplotacionesDeBusqueda();
        List<String> todas = List.of("ES050000000001", "ES050000000002", "ES050000000003");

        assertThat(codigos(gestoria, null)).containsExactlyElementsOf(todas);
        assertThat(codigos(gestoria, "")).containsExactlyElementsOf(todas);
        assertThat(codigos(gestoria, "   ")).containsExactlyElementsOf(todas);
    }

    @Test
    void qSeRecortaAntesDeBuscar() {
        Gestoria gestoria = gestoriaConExplotacionesDeBusqueda();

        assertThat(codigos(gestoria, "  roble  ")).containsExactly("ES050000000001");
    }

    @Test
    void qDeMasDeCienCaracteresDevuelve400ConMotivo() {
        Gestoria gestoria = gestoriaConExplotacionesDeBusqueda();

        ResponseEntity<?> respuesta = controlador().listar(principal(gestoria), "a".repeat(101), PageRequest.of(0, 10));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody())
                .isEqualTo(new MotivoErrorResponse(ExplotacionController.MOTIVO_BUSQUEDA_DEMASIADO_LARGA));
    }

    @Test
    void qDeCienCaracteresTrasRecortarEsValida() {
        Gestoria gestoria = gestoriaConExplotacionesDeBusqueda();

        ResponseEntity<?> respuesta = controlador().listar(principal(gestoria),
                "  " + "a".repeat(100) + "  ", PageRequest.of(0, 10));

        assertThat(pagina(respuesta).getTotalElements()).isZero();
    }

    @Test
    void qPaginadoCuentaTotalElementsSoloConLasCoincidenciasDeLaGestoria() {
        Gestoria gestoria = gestoriaConExplotacionesDeBusqueda();
        Gestoria otra = gestoriaRepository.save(new Gestoria("Gestoria otra"));
        nuevaExplotacion(otra, nuevoGanadero(otra, "Pastora Lucia Otra"), "ES050000000101", "Finca del Roble Otra");
        entityManager.flush();
        entityManager.clear();

        Page<ExplotacionResponse> primera = pagina(controlador().listar(principal(gestoria), "finca", PageRequest.of(0, 2)));
        Page<ExplotacionResponse> segunda = pagina(controlador().listar(principal(gestoria), "finca", PageRequest.of(1, 2)));

        assertThat(primera.getTotalElements()).isEqualTo(3);
        assertThat(primera.getTotalPages()).isEqualTo(2);
        assertThat(primera.getContent()).extracting(ExplotacionResponse::codigoRega)
                .containsExactly("ES050000000001", "ES050000000002");
        assertThat(segunda.getContent()).extracting(ExplotacionResponse::codigoRega).containsExactly("ES050000000003");
        assertThat(codigos(gestoria, "otra")).isEmpty();
    }

    @Test
    void qConOrdenacionPermitidaOrdenaPorElCampoDeLaExplotacion() {
        Gestoria gestoria = gestoriaConExplotacionesDeBusqueda();

        Page<ExplotacionResponse> porNombreDesc = pagina(controlador().listar(principal(gestoria), "finca",
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("nombre")))));
        Page<ExplotacionResponse> porIdDesc = pagina(controlador().listar(principal(gestoria), "finca",
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("id")))));

        // Por nombre de la finca: Roble (1) > Nueva (3) > Llana (2). Si "nombre" se resolviera contra
        // el Ganadero (Pastora Lucia > Juan Perez) la 3 saldria primera.
        assertThat(porNombreDesc.getContent()).extracting(ExplotacionResponse::nombre)
                .containsExactly("Finca Roble", "Finca Nueva", "Finca Llana");
        assertThat(porIdDesc.getContent()).extracting(ExplotacionResponse::codigoRega)
                .containsExactly("ES050000000003", "ES050000000002", "ES050000000001");
    }

    /** Orden de comprobaciones: primero la lista blanca de sort (como hasta ahora), despues la
     * longitud de q. Una peticion con las dos cosas mal recibe el motivo de ordenacion. */
    @Test
    void qConOrdenacionNoPermitidaDevuelve400DeOrdenacionAntesQueElDeLongitud() {
        Gestoria gestoria = gestoriaConExplotacionesDeBusqueda();

        ResponseEntity<?> conQValida = controlador().listar(principal(gestoria), "finca",
                PageRequest.of(0, 10, Sort.by("ganadero.nombre")));
        ResponseEntity<?> conQLarga = controlador().listar(principal(gestoria), "a".repeat(101),
                PageRequest.of(0, 10, Sort.by("ganadero.nombre")));

        assertThat(conQValida.getStatusCode().value()).isEqualTo(400);
        assertThat(conQValida.getBody()).isEqualTo(new MotivoErrorResponse(OrdenacionPermitida.MOTIVO));
        assertThat(conQLarga.getStatusCode().value()).isEqualTo(400);
        assertThat(conQLarga.getBody()).isEqualTo(new MotivoErrorResponse(OrdenacionPermitida.MOTIVO));
    }

    @Test
    void patronContieneEscapaComodinesYCaracterDeEscape() {
        assertThat(ExplotacionController.patronContiene("a%b_c!d")).isEqualTo("%a!%b!_c!!d%");
        assertThat(ExplotacionController.patronContiene("roble")).isEqualTo("%roble%");
    }

    /** Explotaciones 1 "Finca Roble" y 2 "Finca Llana" (Ganadero Juan Perez) y 3 "Finca Nueva"
     * (Ganadero Pastora Lucia), todas de la misma Gestoria. */
    private Gestoria gestoriaConExplotacionesDeBusqueda() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria busqueda"));
        Ganadero juan = nuevoGanadero(gestoria, "Juan Perez");
        Ganadero lucia = nuevoGanadero(gestoria, "Pastora Lucia");
        nuevaExplotacion(gestoria, juan, "ES050000000001", "Finca Roble");
        nuevaExplotacion(gestoria, juan, "ES050000000002", "Finca Llana");
        nuevaExplotacion(gestoria, lucia, "ES050000000003", "Finca Nueva");
        entityManager.flush();
        entityManager.clear();
        return gestoria;
    }

    private List<String> codigos(Gestoria gestoria, String q) {
        return pagina(controlador().listar(principal(gestoria), q, PageRequest.of(0, 50)))
                .getContent().stream().map(ExplotacionResponse::codigoRega).toList();
    }

    private ExplotacionController controlador() {
        return new ExplotacionController(explotacionRepository, animalRepository);
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
