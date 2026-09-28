package com.ganera.core.contacto;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class ContactoRepositoryTest {

    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void buscaPorTelefonoSinNecesidadDeConocerLaGestoriaDeAntemano() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria telefono"));
        contactoRepository.save(nuevoContacto(gestoria, "+34600111222", true));
        entityManager.flush();
        entityManager.clear();

        Optional<Contacto> encontrado = contactoRepository.findByTelefono("+34600111222");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getGestoria().getId()).isEqualTo(gestoria.getId());
        assertThat(encontrado.get().isActivo()).isTrue();
    }

    @Test
    void telefonoEsUnicoGlobalmenteNoPorGestoria() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B"));
        contactoRepository.saveAndFlush(nuevoContacto(gestoriaA, "+34600333444", true));

        Contacto duplicadoEnOtraGestoria = nuevoContacto(gestoriaB, "+34600333444", true);

        assertThrows(DataIntegrityViolationException.class,
                () -> contactoRepository.saveAndFlush(duplicadoEnOtraGestoria));
    }

    @Test
    void telefonoTampocoSePuedeRepetirDentroDeLaMismaGestoria() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria duplicado"));
        contactoRepository.saveAndFlush(nuevoContacto(gestoria, "+34600333555", true));

        Contacto duplicado = nuevoContacto(gestoria, "+34600333555", true);

        assertThrows(DataIntegrityViolationException.class,
                () -> contactoRepository.saveAndFlush(duplicado));
    }

    @Test
    void buscarPorGestoriaYTelefonoSoloEncuentraDentroDeSuGestoria() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A tel"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B tel"));
        contactoRepository.save(nuevoContacto(gestoriaA, "+34600444555", true));
        entityManager.flush();
        entityManager.clear();

        assertThat(contactoRepository.findByGestoriaIdAndTelefono(gestoriaA.getId(), "+34600444555")).isPresent();
        assertThat(contactoRepository.findByGestoriaIdAndTelefono(gestoriaB.getId(), "+34600444555")).isEmpty();
    }

    @Test
    void buscarPorIdYGestoriaNoDevuelveContactosDeOtraGestoria() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A id"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B id"));
        Contacto contactoA = contactoRepository.save(nuevoContacto(gestoriaA, "+34600444666", true));
        entityManager.flush();
        entityManager.clear();

        assertThat(contactoRepository.findByIdAndGestoriaId(contactoA.getId(), gestoriaA.getId())).isPresent();
        assertThat(contactoRepository.findByIdAndGestoriaId(contactoA.getId(), gestoriaB.getId())).isEmpty();
    }

    @Test
    void listarActivosExcluyeInactivosYContactosDeOtraGestoria() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A listado"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B listado"));
        contactoRepository.save(nuevoContacto(gestoriaA, "+34600555001", true));
        contactoRepository.save(nuevoContacto(gestoriaA, "+34600555002", false));
        contactoRepository.save(nuevoContacto(gestoriaB, "+34600555003", true));
        entityManager.flush();
        entityManager.clear();

        Page<Contacto> activos = contactoRepository.findByGestoriaIdAndActivoTrue(
                gestoriaA.getId(), PageRequest.of(0, 20));
        Page<Contacto> todos = contactoRepository.findByGestoriaId(gestoriaA.getId(), PageRequest.of(0, 20));

        assertThat(activos.getContent()).extracting(Contacto::getTelefono).containsExactly("+34600555001");
        assertThat(todos.getContent()).extracting(Contacto::getTelefono)
                .containsExactlyInAnyOrder("+34600555001", "+34600555002");
    }

    @Test
    void unContactoNuevoEstaActivoPorDefecto() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria defecto"));
        Contacto contacto = new Contacto();
        contacto.setGestoria(gestoria);
        contacto.setTelefono("+34600555999");
        contacto.setNombre("Sin activo explicito");
        Contacto guardado = contactoRepository.saveAndFlush(contacto);
        entityManager.clear();

        assertThat(contactoRepository.findByIdAndGestoriaId(guardado.getId(), gestoria.getId())
                .orElseThrow().isActivo()).isTrue();
    }

    private static Contacto nuevoContacto(Gestoria gestoria, String telefono, boolean activo) {
        Contacto contacto = new Contacto();
        contacto.setGestoria(gestoria);
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto de prueba");
        contacto.setActivo(activo);
        return contacto;
    }
}
