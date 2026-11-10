package com.ganera.core.ganadero;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.crypto.CifradorAesGcm;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * T6 de M2 (garantia de reposo): tras quitar {@code @Convert}, lo que queda EN LA COLUMNA debe ser ciphertext
 * recuperable, nunca el texto plano. Se comprueba leyendo la columna CRUDA por JDBC (no por el getter), que es
 * lo que de verdad hay en reposo. El setter de {@code Ganadero} solo acepta un {@code CredencialOvzCifrada}
 * producido por {@link CifradorAesGcm#cifrar}; pasar un {@code String} plano no compila (garantia de tipo).
 *
 * <p>Clave INVENTADA solo para el test (32 bytes ASCII, base64); nunca la del entorno.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(CifradorAesGcm.class)
@TestPropertySource(properties = "ganera.encryption.key=" + GanaderoRepositoryTest.CLAVE_DE_TEST)
class GanaderoRepositoryTest {

    /** Base64 de 32 bytes ASCII. Falsa a proposito. */
    static final String CLAVE_DE_TEST =
            "Y2xhdmUtZGUtdGVzdC1nYW5hZGVyby1yZXBvLTMyISE=";

    private static final String PLANO = "password-ovz-en-claro-inventada";

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private GanaderoRepository ganaderoRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CifradorAesGcm cifrador;

    @Test
    void laPasswordDeOvzSeGuardaCifradaEnReposoYNoEnClaro() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria de prueba"));

        Ganadero ganadero = new Ganadero();
        ganadero.setGestoria(gestoria);
        ganadero.setNombre("Ganadero de prueba");
        ganadero.setOvzUsuario("usuario.ovz");
        ganadero.setOvzPasswordCifrada(cifrador.cifrar(PLANO));
        Ganadero guardado = ganaderoRepository.save(ganadero);

        entityManager.flush();
        entityManager.clear();

        // Lo que hay EN REPOSO en la columna: ciphertext, nunca el plano, pero descifrable.
        String columnaCruda = jdbcTemplate.queryForObject(
                "SELECT ovz_password_cifrada FROM ganadero WHERE id = ?",
                String.class, guardado.getId());
        assertThat(columnaCruda).isNotBlank();
        assertThat(columnaCruda).isNotEqualTo(PLANO);
        assertThat(columnaCruda).doesNotContain(PLANO);
        assertThat(cifrador.descifrar(columnaCruda)).isEqualTo(PLANO);

        // Al releer la entidad, el getter devuelve el ciphertext tal cual (ya NO se descifra al cargar).
        Ganadero releido = ganaderoRepository.findById(guardado.getId()).orElseThrow();
        assertThat(releido.getOvzPasswordCifrada()).isEqualTo(columnaCruda);
    }
}
