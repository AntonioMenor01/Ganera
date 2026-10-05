package com.ganera.core.whatsapp;

import com.ganera.core.contacto.ContactoExplotacionRepository;
import com.ganera.core.contacto.ContactoRepository;
import com.ganera.core.tramite.TramiteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/** Comprobacion previa barata: un MessageSid ya guardado es un duplicado y no se vuelve a guardar. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class TwilioWebhookIdempotencyTest {

    @Autowired
    private MensajeCampoRepository mensajeCampoRepository;
    @Autowired
    private ContactoRepository contactoRepository;
    @Autowired
    private ContactoExplotacionRepository contactoExplotacionRepository;
    @Autowired
    private TramiteRepository tramiteRepository;

    @Test
    void unMessageSidYaGuardadoNoDebeVolverAGuardarse() {
        MensajeEntranteService servicio = new MensajeEntranteService(mensajeCampoRepository, contactoRepository,
                contactoExplotacionRepository, tramiteRepository, Clock.systemUTC());

        MensajeCampo existente = new MensajeCampo();
        existente.setMessageSid("SM999");
        existente.setTelefonoOrigen("+34600000000");
        existente.setCuerpo("mensaje original");
        mensajeCampoRepository.saveAndFlush(existente);

        RecepcionMensaje recepcion = servicio.registrar(new MensajeEntrante("SM999", "whatsapp:+34600000000", "otro", 0));

        assertThat(recepcion.duplicado()).isTrue();
        assertThat(mensajeCampoRepository.count()).isEqualTo(1);
        assertThat(mensajeCampoRepository.findAll()).singleElement()
                .satisfies(m -> assertThat(m.getCuerpo()).isEqualTo("mensaje original"));
    }
}
