package com.ganera.core.contacto;

import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.explotacion.ExplotacionRepository;
import com.ganera.core.ganadero.Ganadero;
import com.ganera.core.ganadero.GanaderoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class ContactoExplotacionRepositoryTest {

    @Autowired
    private ContactoExplotacionRepository contactoExplotacionRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private ExplotacionRepository explotacionRepository;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void elRolSePersisteEnLaRelacion() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria rol"));
        Contacto contacto = nuevoContacto(gestoria, "+34611000001", true);
        Explotacion explotacion = nuevaExplotacion(gestoria, "ES-ROL-1");
        enlazar(gestoria, contacto, explotacion, RolContacto.EMPLEADO);
        entityManager.flush();
        entityManager.clear();

        ContactoExplotacion encontrado = contactoExplotacionRepository
                .findByContactoIdAndExplotacionIdAndGestoriaId(contacto.getId(), explotacion.getId(), gestoria.getId())
                .orElseThrow();

        assertThat(encontrado.getRol()).isEqualTo(RolContacto.EMPLEADO);
        assertThat(encontrado.getCreatedAt()).isNotNull();
    }

    @Test
    void unEmpleadoPuedeEstarEnlazadoAVariasExplotaciones() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria empleado varias"));
        Contacto empleado = nuevoContacto(gestoria, "+34611000002", true);
        Explotacion explotacion1 = nuevaExplotacion(gestoria, "ES-EMP-1");
        Explotacion explotacion2 = nuevaExplotacion(gestoria, "ES-EMP-2");
        enlazar(gestoria, empleado, explotacion1, RolContacto.EMPLEADO);
        enlazar(gestoria, empleado, explotacion2, RolContacto.EMPLEADO);
        entityManager.flush();
        entityManager.clear();

        List<ContactoExplotacion> enlaces = contactoExplotacionRepository
                .findByContactoIdInAndGestoriaId(List.of(empleado.getId()), gestoria.getId());

        assertThat(enlaces).hasSize(2);
    }

    @Test
    void buscarPorContactoYExplotacionNuncaDevuelveFilasDeOtraGestoria() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A enlace"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B enlace"));
        Contacto contactoA = nuevoContacto(gestoriaA, "+34611000003", true);
        Explotacion explotacionA = nuevaExplotacion(gestoriaA, "ES-A-1");
        enlazar(gestoriaA, contactoA, explotacionA, RolContacto.TITULAR);
        Contacto contactoB = nuevoContacto(gestoriaB, "+34611000009", true);
        Explotacion explotacionB = nuevaExplotacion(gestoriaB, "ES-B-1");
        enlazar(gestoriaB, contactoB, explotacionB, RolContacto.EMPLEADO);
        entityManager.flush();
        entityManager.clear();

        ContactoExplotacion enlaceA = contactoExplotacionRepository.findByContactoIdAndExplotacionIdAndGestoriaId(
                contactoA.getId(), explotacionA.getId(), gestoriaA.getId()).orElseThrow();
        ContactoExplotacion enlaceB = contactoExplotacionRepository.findByContactoIdAndExplotacionIdAndGestoriaId(
                contactoB.getId(), explotacionB.getId(), gestoriaB.getId()).orElseThrow();

        assertThat(enlaceA.getGestoria().getId()).isEqualTo(gestoriaA.getId());
        assertThat(enlaceA.getRol()).isEqualTo(RolContacto.TITULAR);
        assertThat(enlaceB.getGestoria().getId()).isEqualTo(gestoriaB.getId());
        assertThat(enlaceB.getRol()).isEqualTo(RolContacto.EMPLEADO);
        assertThat(contactoExplotacionRepository.findByContactoIdAndExplotacionIdAndGestoriaId(
                contactoA.getId(), explotacionA.getId(), gestoriaB.getId())).isEmpty();
        assertThat(contactoExplotacionRepository.findByContactoIdAndExplotacionIdAndGestoriaId(
                contactoB.getId(), explotacionB.getId(), gestoriaA.getId())).isEmpty();
    }

    @Test
    void cargaEnLotePorContactosNuncaDevuelveFilasDeOtraGestoria() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A lote"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B lote"));
        Contacto contactoA = nuevoContacto(gestoriaA, "+34611000004", true);
        Contacto contactoB = nuevoContacto(gestoriaB, "+34611000005", true);
        enlazar(gestoriaA, contactoA, nuevaExplotacion(gestoriaA, "ES-LOTE-A"), RolContacto.TITULAR);
        enlazar(gestoriaB, contactoB, nuevaExplotacion(gestoriaB, "ES-LOTE-B"), RolContacto.TITULAR);
        entityManager.flush();
        entityManager.clear();

        List<ContactoExplotacion> enlaces = contactoExplotacionRepository.findByContactoIdInAndGestoriaId(
                List.of(contactoA.getId(), contactoB.getId()), gestoriaA.getId());

        assertThat(enlaces).hasSize(1);
        assertThat(enlaces.get(0).getContacto().getId()).isEqualTo(contactoA.getId());
    }

    @Test
    void cargaPorExplotacionesExcluyeContactosInactivosYDeOtraGestoria() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A explot"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B explot"));
        Explotacion explotacionA = nuevaExplotacion(gestoriaA, "ES-EXP-A");
        Explotacion explotacionB = nuevaExplotacion(gestoriaB, "ES-EXP-B");
        Contacto activo = nuevoContacto(gestoriaA, "+34611000006", true);
        Contacto inactivo = nuevoContacto(gestoriaA, "+34611000007", false);
        Contacto deOtraGestoria = nuevoContacto(gestoriaB, "+34611000008", true);
        enlazar(gestoriaA, activo, explotacionA, RolContacto.TITULAR);
        enlazar(gestoriaA, inactivo, explotacionA, RolContacto.EMPLEADO);
        enlazar(gestoriaB, deOtraGestoria, explotacionB, RolContacto.TITULAR);
        entityManager.flush();
        entityManager.clear();

        List<ContactoExplotacion> enlaces = contactoExplotacionRepository
                .findByExplotacionIdInAndGestoriaIdAndContactoGestoriaIdAndContactoActivoTrue(
                        List.of(explotacionA.getId(), explotacionB.getId()), gestoriaA.getId(), gestoriaA.getId());

        assertThat(enlaces).hasSize(1);
        assertThat(enlaces.get(0).getContacto().getId()).isEqualTo(activo.getId());
    }

    /**
     * Defensa en profundidad: un enlace con gestoria_id de A pero cuyo Contacto es de B (dato
     * incoherente que la decision 15 impide en todas las escrituras actuales) no sale en el detalle.
     */
    @Test
    void cargaPorExplotacionesExcluyeEnlaceCuyoContactoEsDeOtraGestoria() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A incoherente"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B incoherente"));
        Explotacion explotacionA = nuevaExplotacion(gestoriaA, "ES-INC-A");
        Contacto deA = nuevoContacto(gestoriaA, "+34611000009", true);
        Contacto deB = nuevoContacto(gestoriaB, "+34611000010", true);
        enlazar(gestoriaA, deA, explotacionA, RolContacto.TITULAR);
        enlazar(gestoriaA, deB, explotacionA, RolContacto.EMPLEADO);
        entityManager.flush();
        entityManager.clear();

        List<ContactoExplotacion> enlaces = contactoExplotacionRepository
                .findByExplotacionIdInAndGestoriaIdAndContactoGestoriaIdAndContactoActivoTrue(
                        List.of(explotacionA.getId()), gestoriaA.getId(), gestoriaA.getId());

        assertThat(enlaces).singleElement()
                .satisfies(e -> assertThat(e.getContacto().getId()).isEqualTo(deA.getId()));
    }

    private Contacto nuevoContacto(Gestoria gestoria, String telefono, boolean activo) {
        Contacto contacto = new Contacto();
        contacto.setGestoria(gestoria);
        contacto.setTelefono(telefono);
        contacto.setNombre("Contacto de prueba");
        contacto.setActivo(activo);
        return contactoRepository.save(contacto);
    }

    private Explotacion nuevaExplotacion(Gestoria gestoria, String codigoRega) {
        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero " + codigoRega);
        ganaderoRepository.save(ganadero);

        Explotacion explotacion = new Explotacion();
        explotacion.setGestoria(gestoria);
        explotacion.setGanadero(ganadero);
        explotacion.setCodigoRega(codigoRega);
        explotacion.setNombre("Explotacion " + codigoRega);
        return explotacionRepository.save(explotacion);
    }

    private void enlazar(Gestoria gestoria, Contacto contacto, Explotacion explotacion, RolContacto rol) {
        ContactoExplotacion enlace = new ContactoExplotacion();
        enlace.setGestoria(gestoria);
        enlace.setContacto(contacto);
        enlace.setExplotacion(explotacion);
        enlace.setRol(rol);
        contactoExplotacionRepository.save(enlace);
    }
}
