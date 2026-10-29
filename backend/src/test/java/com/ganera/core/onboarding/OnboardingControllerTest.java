package com.ganera.core.onboarding;

import com.ganera.core.facturacion.AltaPendienteRepository;
import com.ganera.core.facturacion.EnlaceStripeService;
import com.ganera.core.facturacion.EstadoSuscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.ActorTipo;
import com.ganera.core.shared.auditoria.Auditoria;
import com.ganera.core.shared.auditoria.AuditoriaRepository;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.tiempo.RelojConfig;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({AuditoriaService.class, RelojConfig.class})
class OnboardingControllerTest {

    @Autowired
    private GestoriaRepository gestoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private SuscripcionRepository suscripcionRepository;
    @Autowired
    private AuditoriaService auditoriaService;
    @Autowired
    private AuditoriaRepository auditoriaRepository;
    @Autowired
    private AltaPendienteRepository altaPendienteRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** Enlace a Stripe SIN configurar (clave vacia): los tests de aqui usan el alta directa, sin stripeSubscriptionId. */
    private EnlaceStripeService enlaceStripeSinConfigurar() {
        return new EnlaceStripeService(altaPendienteRepository, "", "");
    }

    @Test
    void secretoCorrectoEsValido() {
        OnboardingController controller = new OnboardingController(
                gestoriaRepository, usuarioRepository, suscripcionRepository, enlaceStripeSinConfigurar(), passwordEncoder, auditoriaService, "secreto-correcto");

        assertThat(controller.secretoValido("secreto-correcto")).isTrue();
    }

    @Test
    void secretoIncorrectoNoEsValido() {
        OnboardingController controller = new OnboardingController(
                gestoriaRepository, usuarioRepository, suscripcionRepository, enlaceStripeSinConfigurar(), passwordEncoder, auditoriaService, "secreto-correcto");

        assertThat(controller.secretoValido("otro-secreto")).isFalse();
    }

    @Test
    void cabeceraAusenteNoEsValida() {
        OnboardingController controller = new OnboardingController(
                gestoriaRepository, usuarioRepository, suscripcionRepository, enlaceStripeSinConfigurar(), passwordEncoder, auditoriaService, "secreto-correcto");

        assertThat(controller.secretoValido(null)).isFalse();
    }

    @Test
    void secretoConfiguradoEnBlancoFallaCerradoAunqueLaCabeceraTambienEsteEnBlanco() {
        OnboardingController controller = new OnboardingController(
                gestoriaRepository, usuarioRepository, suscripcionRepository, enlaceStripeSinConfigurar(), passwordEncoder, auditoriaService, "");

        assertThat(controller.secretoValido("")).isFalse();
    }

    @Test
    void creaGestoriaUsuarioYSuscripcionActivaEnUnaTransaccion() {
        OnboardingController controller = new OnboardingController(
                gestoriaRepository, usuarioRepository, suscripcionRepository, enlaceStripeSinConfigurar(), passwordEncoder, auditoriaService, "secreto-onboarding");

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
    void elAltaDejaFilasDeAuditoriaGestoriaCreadaUsuarioCreadoYSuscripcionCreadaConActorSistema() {
        OnboardingController controller = new OnboardingController(
                gestoriaRepository, usuarioRepository, suscripcionRepository, enlaceStripeSinConfigurar(), passwordEncoder, auditoriaService, "secreto-onboarding");
        OnboardingRequest request = new OnboardingRequest(
                "Gestoria Auditada", "auditada@piloto.com", "password-en-claro", "Empleado Auditado");

        OnboardingResponse cuerpo = controller.crearGestoria("secreto-onboarding", request).getBody();

        Long suscripcionId = suscripcionRepository.findByGestoriaId(cuerpo.gestoriaId()).orElseThrow().getId();
        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(cuerpo.gestoriaId());
        assertThat(filas).extracting(Auditoria::getAccion).containsExactly(
                AccionAuditoria.GESTORIA_CREADA, AccionAuditoria.USUARIO_CREADO, AccionAuditoria.SUSCRIPCION_CREADA);
        filas.forEach(fila -> assertThat(fila.getActorTipo()).isEqualTo(ActorTipo.SISTEMA));
        // Alta manual (sin peticion HTTP): sin ip ni request_id.
        filas.forEach(fila -> {
            assertThat(fila.getUsuarioId()).isNull();
            assertThat(fila.getIp()).isNull();
            assertThat(fila.getRequestId()).isNull();
        });
        assertThat(filas.get(0).getEntidadTipo()).isEqualTo(EntidadAuditada.GESTORIA);
        assertThat(filas.get(0).getEntidadId()).isEqualTo(cuerpo.gestoriaId());
        assertThat(filas.get(1).getEntidadTipo()).isEqualTo(EntidadAuditada.USUARIO);
        assertThat(filas.get(1).getEntidadId()).isEqualTo(cuerpo.usuarioId());
        assertThat(filas.get(1).getDespues()).contains("\"activo\":true");
        assertThat(filas.get(2).getEntidadTipo()).isEqualTo(EntidadAuditada.SUSCRIPCION);
        assertThat(filas.get(2).getEntidadId()).isEqualTo(suscripcionId);
        assertThat(filas.get(2).getDespues()).contains("\"estado\":\"ACTIVA\"");
    }

    @Test
    void secretoInvalidoRechazaConHttp401YNoCreaNada() {
        OnboardingController controller = new OnboardingController(
                gestoriaRepository, usuarioRepository, suscripcionRepository, enlaceStripeSinConfigurar(), passwordEncoder, auditoriaService, "secreto-onboarding");

        OnboardingRequest request = new OnboardingRequest(
                "Gestoria Rechazada", "rechazado@piloto.com", "password", "Nombre");

        ResponseEntity<OnboardingResponse> response = controller.crearGestoria("secreto-incorrecto", request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(usuarioRepository.findByEmail("rechazado@piloto.com")).isEmpty();
    }
}
