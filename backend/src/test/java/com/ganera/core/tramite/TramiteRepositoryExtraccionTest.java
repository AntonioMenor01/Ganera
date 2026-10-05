package com.ganera.core.tramite;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Los dos UPDATE JPQL de la extraccion (B1, T2; revision m4): el predicado de gestoriaId del WHERE
 * esta fijado por test (el id de un Tramite de A con la gestoria de B no cambia nada) y ninguno de
 * los dos toca la version. Sin gestoriaFilter: el predicado es lo unico que aisla.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class TramiteRepositoryExtraccionTest {

    private static final Instant PROXIMO = Instant.parse("2031-03-10T09:00:00Z");

    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private EntityManager entityManager;

    private Gestoria gestoriaA;
    private Gestoria gestoriaB;
    private Tramite tramiteA;

    @BeforeEach
    void preparar() {
        gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria repo extraccion A"));
        gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria repo extraccion B"));
        Contacto contacto = new Contacto();
        contacto.setTelefono("+34600990001");
        contacto.setNombre("Contacto repo extraccion");
        contacto.setGestoria(gestoriaA);
        contactoRepository.save(contacto);

        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoriaA);
        tramite.setContacto(contacto);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        tramite.setEstadoExtraccion(EstadoExtraccion.FALLIDA);
        tramite.setIntentosExtraccion(1);
        tramite.setProximoIntentoExtraccion(PROXIMO);
        tramiteA = tramiteRepository.saveAndFlush(tramite);
        entityManager.clear();
    }

    @Test
    void elIntentoFallidoConLaGestoriaDeBNoCambiaNada() {
        int filas = tramiteRepository.registrarIntentoFallidoSinCambiarVersion(tramiteA.getId(), gestoriaB.getId(), null);

        assertThat(filas).isZero();
        assertSinCambios();
    }

    @Test
    void dejarDeReintentarConLaGestoriaDeBNoCambiaNada() {
        int filas = tramiteRepository.dejarDeReintentarExtraccion(tramiteA.getId(), gestoriaB.getId());

        assertThat(filas).isZero();
        assertSinCambios();
    }

    @Test
    void conSuGestoriaLosDosActualizanUnaFilaSinTocarLaVersion() {
        long version = tramiteA.getVersion();

        assertThat(tramiteRepository.registrarIntentoFallidoSinCambiarVersion(
                tramiteA.getId(), gestoriaA.getId(), PROXIMO.plusSeconds(300))).isEqualTo(1);
        entityManager.clear();
        Tramite trasFallo = tramiteRepository.findByIdAndGestoriaId(tramiteA.getId(), gestoriaA.getId()).orElseThrow();
        assertThat(trasFallo.getIntentosExtraccion()).isEqualTo(2);
        assertThat(trasFallo.getProximoIntentoExtraccion()).isEqualTo(PROXIMO.plusSeconds(300));
        assertThat(trasFallo.getVersion()).isEqualTo(version);

        assertThat(tramiteRepository.dejarDeReintentarExtraccion(tramiteA.getId(), gestoriaA.getId())).isEqualTo(1);
        entityManager.clear();
        Tramite detenido = tramiteRepository.findByIdAndGestoriaId(tramiteA.getId(), gestoriaA.getId()).orElseThrow();
        assertThat(detenido.getProximoIntentoExtraccion()).isNull();
        assertThat(detenido.getIntentosExtraccion()).isEqualTo(2);
        assertThat(detenido.getVersion()).isEqualTo(version);
    }

    private void assertSinCambios() {
        entityManager.clear();
        Tramite releido = tramiteRepository.findByIdAndGestoriaId(tramiteA.getId(), gestoriaA.getId()).orElseThrow();
        assertThat(releido.getIntentosExtraccion()).isEqualTo(1);
        assertThat(releido.getProximoIntentoExtraccion()).isEqualTo(PROXIMO);
        assertThat(releido.getVersion()).isEqualTo(tramiteA.getVersion());
    }
}
