package com.ganera.core.contacto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class ContactoRepositoryTest {

    @Autowired
    private ContactoRepository contactoRepository;

    @Test
    void buscaPorTelefonoSinNecesidadDeConocerLaGestoriaDeAntemano() {
        Contacto contacto = new Contacto();
        contacto.setTelefono("+34600111222");
        contacto.setNombre("Titular de prueba");
        contacto.setTipo(TipoContacto.TITULAR);
        contactoRepository.save(contacto);

        Optional<Contacto> encontrado = contactoRepository.findByTelefono("+34600111222");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getTipo()).isEqualTo(TipoContacto.TITULAR);
    }

    @Test
    void telefonoEsUnicoGlobalmenteNoPorGestoria() {
        Contacto contacto = new Contacto();
        contacto.setTelefono("+34600333444");
        contacto.setNombre("Otro contacto");
        contacto.setTipo(TipoContacto.TRABAJADOR);
        contactoRepository.saveAndFlush(contacto);

        Contacto duplicado = new Contacto();
        duplicado.setTelefono("+34600333444");
        duplicado.setNombre("Intento duplicado");
        duplicado.setTipo(TipoContacto.TRABAJADOR);

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> contactoRepository.saveAndFlush(duplicado));
    }
}
