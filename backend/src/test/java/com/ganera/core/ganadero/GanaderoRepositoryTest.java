package com.ganera.core.ganadero;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class GanaderoRepositoryTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void laPasswordDeOvzSeAlmacenaCifradaEnColumnaPeroSeLeeEnClaro() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria de prueba"));

        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero de prueba");
        ganadero.setOvzUsuario("usuario.ovz");
        ganadero.setOvzPasswordCifrada("password-en-claro");
        Ganadero guardado = ganaderoRepository.save(ganadero);

        entityManager.flush();
        entityManager.clear();

        String columnaCruda = jdbcTemplate.queryForObject(
                "SELECT ovz_password_cifrada FROM ganadero WHERE id = ?",
                String.class, guardado.getId());
        assertThat(columnaCruda).isNotEqualTo("password-en-claro");

        Ganadero releido = ganaderoRepository.findById(guardado.getId()).orElseThrow();
        assertThat(releido.getOvzPasswordCifrada()).isEqualTo("password-en-claro");
    }
}
