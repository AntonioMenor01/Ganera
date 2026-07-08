package com.ganera.core.facturacion;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
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
class SuscripcionFilterIsolationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;

    @Test
    void unaGestoriaNoVeSuscripcionesDeOtraCuandoElFiltroEstaActivo() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B"));

        suscripcionRepository.save(nuevaSuscripcion(gestoriaA, EstadoSuscripcion.ACTIVA));
        suscripcionRepository.save(nuevaSuscripcion(gestoriaB, EstadoSuscripcion.ACTIVA));

        entityManager.flush();
        entityManager.clear();

        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("gestoriaFilter").setParameter("gestoriaId", gestoriaA.getId());

        List<Suscripcion> visibles = suscripcionRepository.findAll();

        assertThat(visibles).hasSize(1);
        assertThat(visibles.get(0).getGestoria().getId()).isEqualTo(gestoriaA.getId());
    }

    @Test
    void sinFiltroActivoSeVenTodasLasSuscripciones() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A2"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B2"));

        suscripcionRepository.save(nuevaSuscripcion(gestoriaA, EstadoSuscripcion.TRIAL));
        suscripcionRepository.save(nuevaSuscripcion(gestoriaB, EstadoSuscripcion.TRIAL));

        entityManager.flush();
        entityManager.clear();

        assertThat(suscripcionRepository.findAll()).hasSize(2);
    }

    private static Suscripcion nuevaSuscripcion(Gestoria gestoria, EstadoSuscripcion estado) {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(estado);
        return suscripcion;
    }
}
