package com.ganera.core.facturacion;

import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.security.GaneraUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class FacturacionControllerTest {

    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;

    private FacturacionController nuevoController() {
        StripeCheckoutService stripeCheckoutServiceNoUsado = new StripeCheckoutService(
                null, null, null, "", "", "", "");
        SuscripcionService suscripcionService = new SuscripcionService(suscripcionRepository, gestoriaRepository);
        return new FacturacionController(stripeCheckoutServiceNoUsado, suscripcionRepository, suscripcionService);
    }

    @Test
    void estadoDevuelve200ConLosDatosDeLaSuscripcionActiva() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria con suscripcion"));
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcion.setExplotacionesContratadas(5);
        suscripcionRepository.save(suscripcion);

        ResponseEntity<SuscripcionEstadoResponse> respuesta = nuevoController()
                .estado(new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        SuscripcionEstadoResponse cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.estado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(cuerpo.puedeAprobarTramites()).isTrue();
        assertThat(cuerpo.explotacionesContratadas()).isEqualTo(5);
    }

    @Test
    void estadoDevuelvePuedeAprobarTramitesFalsoCuandoLaSuscripcionEstaSuspendida() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria suspendida"));
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        suscripcion.setEstado(EstadoSuscripcion.SUSPENDIDA);
        suscripcionRepository.save(suscripcion);

        ResponseEntity<SuscripcionEstadoResponse> respuesta = nuevoController()
                .estado(new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody().estado()).isEqualTo(EstadoSuscripcion.SUSPENDIDA);
        assertThat(respuesta.getBody().puedeAprobarTramites()).isFalse();
    }

    @Test
    void estadoDevuelve404CuandoLaGestoriaNoTieneSuscripcionTodavia() {
        Gestoria gestoria = gestoriaRepository.save(new Gestoria("Gestoria sin suscripcion"));

        ResponseEntity<SuscripcionEstadoResponse> respuesta = nuevoController()
                .estado(new GaneraUserPrincipal(1L, gestoria.getId(), "empleado@test.com"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
    }
}
