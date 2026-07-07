package com.ganera.core.tramite;

import com.ganera.core.contacto.Contacto;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.contacto.TipoContacto;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class TramiteRepositoryTest {

    @Autowired
    private TramiteRepository tramiteRepository;
    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private ContactoRepository contactoRepository;

    @Test
    void unTramitePuedeGuardarseSinExplotacionAsignadaAunSabiendoLaGestoria() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria de prueba"));

        Contacto contacto = new Contacto();
        contacto.setTelefono("+34600555666");
        contacto.setNombre("Titular ambiguo");
        contacto.setTipo(TipoContacto.TITULAR);
        contactoRepository.save(contacto);

        Tramite tramite = new Tramite();
        tramite.setGestoria(gestoria);
        tramite.setContacto(contacto);
        tramite.setEstado(EstadoTramite.PENDIENTE_REVISION);
        Tramite guardado = tramiteRepository.save(tramite);

        Tramite releido = tramiteRepository.findById(guardado.getId()).orElseThrow();
        assertThat(releido.getExplotacion()).isNull();
        assertThat(releido.getEstado()).isEqualTo(EstadoTramite.PENDIENTE_REVISION);
    }
}
