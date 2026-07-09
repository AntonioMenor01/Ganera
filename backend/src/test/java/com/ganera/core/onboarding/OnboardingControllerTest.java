package com.ganera.core.onboarding;

import com.ganera.core.facturacion.EstadoSuscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class OnboardingControllerTest {

    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void secretoCorrectoEsValido() {
        OnboardingController controller = new OnboardingController(
                gestoriaRepository, usuarioRepository, suscripcionRepository, passwordEncoder, "secreto-correcto");

        assertThat(controller.secretoValido("secreto-correcto")).isTrue();
    }

    @Test
    void secretoIncorrectoNoEsValido() {
        OnboardingController controller = new OnboardingController(
                gestoriaRepository, usuarioRepository, suscripcionRepository, passwordEncoder, "secreto-correcto");

        assertThat(controller.secretoValido("otro-secreto")).isFalse();
    }

    @Test
    void cabeceraAusenteNoEsValida() {
        OnboardingController controller = new OnboardingController(
                gestoriaRepository, usuarioRepository, suscripcionRepository, passwordEncoder, "secreto-correcto");

        assertThat(controller.secretoValido(null)).isFalse();
    }

    @Test
    void secretoConfiguradoEnBlancoFallaCerradoAunqueLaCabeceraTambienEsteEnBlanco() {
        OnboardingController controller = new OnboardingController(
                gestoriaRepository, usuarioRepository, suscripcionRepository, passwordEncoder, "");

        assertThat(controller.secretoValido("")).isFalse();
    }

    @Test
    void creaGestoriaUsuarioYSuscripcionActivaEnUnaTransaccion() {
        OnboardingController controller = new OnboardingController(
                gestoriaRepository, usuarioRepository, suscripcionRepository, passwordEncoder, "secreto-onboarding");

        OnboardingRequest request = new OnboardingRequest(
                "Gestoria Piloto", "primer.empleado@piloto.com", "password-en-claro", "Primer Empleado");

        ResponseEntity<OnboardingResponse> response = controller.crearGestoria("secreto-onboarding", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        OnboardingResponse cuerpo = response.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.gestoriaId()).isNotNull();
        assertThat(cuerpo.usuarioId()).isNotNull();

        Usuario usuarioGuardado = usuarioRepository.findById(cuerpo.usuarioId()).orElseThrow();
        assertThat(usuarioGuardado.getEmail()).isEqualTo("primer.empleado@piloto.com");
        assertThat(usuarioGuardado.getPasswordHash()).isNotEqualTo("password-en-claro");
        assertThat(passwordEncoder.matches("password-en-claro", usuarioGuardado.getPasswordHash())).isTrue();

        assertThat(suscripcionRepository.findByGestoriaId(cuerpo.gestoriaId()).orElseThrow().getEstado())
                .isEqualTo(EstadoSuscripcion.ACTIVA);
    }

    @Test
    void secretoInvalidoRechazaConHttp401YNoCreaNada() {
        OnboardingController controller = new OnboardingController(
                gestoriaRepository, usuarioRepository, suscripcionRepository, passwordEncoder, "secreto-onboarding");

        OnboardingRequest request = new OnboardingRequest(
                "Gestoria Rechazada", "rechazado@piloto.com", "password", "Nombre");

        ResponseEntity<OnboardingResponse> response = controller.crearGestoria("secreto-incorrecto", request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(usuarioRepository.findByEmail("rechazado@piloto.com")).isEmpty();
    }
}
