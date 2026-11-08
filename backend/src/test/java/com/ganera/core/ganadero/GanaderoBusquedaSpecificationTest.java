package com.ganera.core.ganadero;

import com.ganera.core.explotacion.Animal;
import com.ganera.core.explotacion.AnimalRepository;
import com.ganera.core.explotacion.AnimalResultado;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Protege el aislamiento multi-tenant del buscador global en su capa de datos, SIN el gestoriaFilter
 * ambiente (en un {@code @DataJpaTest} nadie lo activa). Si {@link GanaderoBusquedaSpecification} o
 * {@link AnimalRepository#buscarPorCrotal} dejaran de llevar su predicado EXPLÍCITO {@code gestoria.id},
 * cada gestoría vería (y contaría) también los datos de la otra y estos tests fallarían. Los E2E de dos
 * gestorías no bastan: allí el filtro ambiente enmascara la pérdida del predicado (ver CLAUDE.md).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class GanaderoBusquedaSpecificationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private AnimalRepository animalRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    /** Dos ganaderos homónimos («Ana García»), uno por gestoría: la búsqueda por nombre de A no puede
     * devolver el de B aunque el nombre coincida en ambas. */
    @Test
    void laSpecificationPorNombreSoloDevuelveLosDeSuGestoriaSinFiltroAmbiente() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Busqueda Ganadero A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Busqueda Ganadero B"));
        Ganadero ganaderoA = nuevoGanadero(gestoriaA, "Ana García", "11111111H");
        nuevoGanadero(gestoriaB, "Ana García", "22222222J");
        entityManager.flush();
        entityManager.clear();

        Page<Ganadero> deA = ganaderoRepository.findAll(
                GanaderoBusquedaSpecification.porNombreONif(gestoriaA.getId(), List.of("garcia"), null),
                PageRequest.of(0, 10));

        assertThat(deA.getTotalElements()).isEqualTo(1);
        assertThat(deA.getContent()).extracting(Ganadero::getId).containsExactly(ganaderoA.getId());
    }

    /** La búsqueda por NIF (token tal cual, en mayúsculas) tampoco cruza de gestoría. */
    @Test
    void laSpecificationPorNifSoloDevuelveLosDeSuGestoriaSinFiltroAmbiente() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Busqueda NIF A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Busqueda NIF B"));
        Ganadero ganaderoA = nuevoGanadero(gestoriaA, "Finca A", "X1234567L");
        nuevoGanadero(gestoriaB, "Finca B", "X1234567L".replace("1234567", "7654321")); // NIF distinto, mismo prefijo
        Ganadero ganaderoBMismoToken = nuevoGanadero(gestoriaB, "Finca B2", "B1234567X");
        entityManager.flush();
        entityManager.clear();

        // El token "1234567" aparece en el NIF de A y en el de ganaderoBMismoToken: solo debe salir el de A.
        Page<Ganadero> deA = ganaderoRepository.findAll(
                GanaderoBusquedaSpecification.porNombreONif(gestoriaA.getId(), List.of(), "1234567"),
                PageRequest.of(0, 10));

        assertThat(deA.getContent()).extracting(Ganadero::getId).containsExactly(ganaderoA.getId());
        assertThat(deA.getContent()).extracting(Ganadero::getId).doesNotContain(ganaderoBMismoToken.getId());
    }

    /** Dos animales con un crotal que comparte el fragmento «1111», uno por gestoría: buscarPorCrotal de
     * A no puede devolver el de B. */
    @Test
    void buscarPorCrotalSoloDevuelveLosDeSuGestoriaSinFiltroAmbiente() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Busqueda Crotal A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Busqueda Crotal B"));
        nuevoAnimal(gestoriaA, "ES111111111111");
        nuevoAnimal(gestoriaB, "ES111111112222");
        entityManager.flush();
        entityManager.clear();

        List<AnimalResultado> deA =
                animalRepository.buscarPorCrotal(gestoriaA.getId(), "%1111%", PageRequest.of(0, 50));

        assertThat(deA).extracting(AnimalResultado::crotal).containsExactly("ES111111111111");
    }

    private Ganadero nuevoGanadero(Gestoria gestoria, String nombre, String nif) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre(nombre);
        ganadero.setNif(nif);
        return ganaderoRepository.save(ganadero);
    }

    private void nuevoAnimal(Gestoria gestoria, String crotal) {
        Ganadero ganadero = nuevoGanadero(gestoria, "Titular " + crotal, null);
        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega("ES" + crotal.substring(2, 12));
        explotacion.setNombre("Finca " + crotal);
        explotacionRepository.save(explotacion);

        Animal animal = new Animal();
        animal.setGestoria(gestoria);
        animal.setExplotacion(explotacion);
        animal.setCrotal(crotal);
        animal.setCrotalUltimosDigitos(crotal.substring(crotal.length() - 6));
        animalRepository.save(animal);
    }
}
