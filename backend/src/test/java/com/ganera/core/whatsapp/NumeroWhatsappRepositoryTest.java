package com.ganera.core.whatsapp;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class NumeroWhatsappRepositoryTest {

    @Autowired
    private NumeroWhatsappRepository numeroWhatsappRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void resuelveElNumeroDeDestinoSinConocerLaGestoriaDeAntemano() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria numero"));
        numeroWhatsappRepository.save(nuevoNumero(gestoria, "+34910000001"));
        entityManager.flush();
        entityManager.clear();

        Optional<NumeroWhatsapp> encontrado = numeroWhatsappRepository.findByNumero("+34910000001");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getGestoria().getId()).isEqualTo(gestoria.getId());
        assertThat(encontrado.get().isActivo()).isTrue();
        assertThat(numeroWhatsappRepository.existsByNumero("+34910000001")).isTrue();
        assertThat(numeroWhatsappRepository.findByNumero("+34999999999")).isEmpty();
    }

    @Test
    void elNumeroEsUnicoGlobalmente() {
        Gestoria gestoriaA = gestoriaRepository.save(new Gestoria("Gestoria A num"));
        Gestoria gestoriaB = gestoriaRepository.save(new Gestoria("Gestoria B num"));
        numeroWhatsappRepository.saveAndFlush(nuevoNumero(gestoriaA, "+34910000002"));

        NumeroWhatsapp duplicado = nuevoNumero(gestoriaB, "+34910000002");

        assertThrows(DataIntegrityViolationException.class,
                () -> numeroWhatsappRepository.saveAndFlush(duplicado));
    }

    @Test
    void laGestoriaEsObligatoria() {
        NumeroWhatsapp sinGestoria = new NumeroWhatsapp();
        sinGestoria.setNumero("+34910000003");
        sinGestoria.setCreatedAt(Instant.now());

        assertThrows(Exception.class, () -> numeroWhatsappRepository.saveAndFlush(sinGestoria));
    }

    private static NumeroWhatsapp nuevoNumero(Gestoria gestoria, String numero) {
        NumeroWhatsapp n = new NumeroWhatsapp();
        n.setGestoria(gestoria);
        n.setNumero(numero);
        n.setActivo(true);
        n.setCreatedAt(Instant.now());
        return n;
    }
}
