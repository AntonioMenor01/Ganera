package com.ganera.core.alta;

import com.ganera.core.facturacion.AltaPendiente;
import com.ganera.core.facturacion.AltaPendienteRepository;
import com.ganera.core.facturacion.EnlaceStripeFake;
import com.ganera.core.facturacion.EnlaceStripeService;
import com.ganera.core.facturacion.EstadoAltaPendiente;
import com.ganera.core.facturacion.EstadoSuscripcion;
import com.ganera.core.facturacion.Suscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.ActorTipo;
import com.ganera.core.shared.auditoria.Auditoria;
import com.ganera.core.shared.auditoria.AuditoriaRepository;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.GuardiaDmlExtension;
import com.ganera.core.shared.tiempo.RelojConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Alta automatica: creacion correcta, idempotencia (no duplica cuentas) y que sin Stripe no hace nada. Usa
 * {@link EnlaceStripeFake} para el enlace con Stripe (convencion test-sin-mocks-externos: no toca el SDK). Las
 * filas {@code alta_pendiente} se crean en el cuerpo del test: estan EXENTAS de la guardia DML (pre-tenant),
 * asi que no necesitan auditoria; las escrituras de negocio (gestoria/usuario/suscripcion) SI la llevan, y por
 * eso {@link GuardiaDmlExtension} valida que el alta automatica audita.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({AuditoriaService.class, RelojConfig.class})
@ExtendWith(GuardiaDmlExtension.class)
class AltaAutomaticaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-23T10:15:30Z");

    @Autowired
    private AltaPendienteRepository altaPendienteRepository;
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
    private PlatformTransactionManager transactionManager;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AltaAutomaticaService servicio(EnlaceStripeService enlace) {
        return new AltaAutomaticaService(altaPendienteRepository, gestoriaRepository, usuarioRepository,
                suscripcionRepository, enlace, passwordEncoder, auditoriaService,
                Clock.fixed(AHORA, ZoneOffset.UTC), transactionManager);
    }

    private EnlaceStripeService enlaceStripe(String status) {
        return new EnlaceStripeFake(altaPendienteRepository, status, "base", "mensual");
    }

    private AltaPendiente nuevaAlta(String sessionId, String subscriptionId, String email, String nombre) {
        AltaPendiente alta = new AltaPendiente();
        alta.setStripeSessionId(sessionId);
        alta.setSubscriptionId(subscriptionId);
        alta.setEmail(email);
        alta.setNombre(nombre);
        alta.setEstado(EstadoAltaPendiente.PENDIENTE);
        alta.setCreadoEn(AHORA);
        return altaPendienteRepository.save(alta);
    }

    @Test
    void creaGestoriaUsuarioYSuscripcionYMarcaElAltaEnlazada() {
        AltaPendiente alta = nuevaAlta("cs_1", "sub_1", "cliente@landing.test", "Cliente Uno");

        List<AltaAutomaticaService.CuentaCreada> creadas = servicio(enlaceStripe("active")).procesarPendientes();

        assertThat(creadas).hasSize(1);
        AltaAutomaticaService.CuentaCreada creada = creadas.get(0);
        assertThat(creada.altaId()).isEqualTo(alta.getId());
        assertThat(creada.gestoriaId()).isNotNull();
        assertThat(creada.usuarioId()).isNotNull();

        Usuario usuario = usuarioRepository.findById(creada.usuarioId()).orElseThrow();
        assertThat(usuario.getEmail()).isEqualTo("cliente@landing.test");
        assertThat(usuario.getNombre()).isEqualTo("Cliente Uno");
        assertThat(usuario.isActivo()).isTrue();

        Suscripcion suscripcion = suscripcionRepository.findByGestoriaId(creada.gestoriaId()).orElseThrow();
        assertThat(suscripcion.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(suscripcion.getStripeSubscriptionId()).isEqualTo("sub_1");
        assertThat(suscripcion.getPlan()).isEqualTo("base");

        AltaPendiente recargada = altaPendienteRepository.findById(alta.getId()).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoAltaPendiente.ENLAZADA);
        assertThat(recargada.getGestoriaId()).isEqualTo(creada.gestoriaId());
        assertThat(recargada.getProcesadaEn()).isEqualTo(AHORA);

        List<Auditoria> filas = auditoriaRepository.findByGestoriaIdOrderByIdAsc(creada.gestoriaId());
        assertThat(filas).extracting(Auditoria::getAccion).containsExactly(
                AccionAuditoria.GESTORIA_CREADA, AccionAuditoria.USUARIO_CREADO, AccionAuditoria.SUSCRIPCION_CREADA);
        filas.forEach(f -> assertThat(f.getActorTipo()).isEqualTo(ActorTipo.PLANIFICADOR));
    }

    @Test
    void laContrasenaTemporalEsAleatoriaSeGuardaHasheadaYNoApareceEnAuditoria() {
        nuevaAlta("cs_pwd", "sub_pwd", "pwd@landing.test", "Cliente Pwd");

        AltaAutomaticaService.CuentaCreada creada = servicio(enlaceStripe("trialing")).procesarPendientes().get(0);

        String passwordTemporal = creada.passwordTemporal();
        assertThat(passwordTemporal).isNotBlank();
        Usuario usuario = usuarioRepository.findById(creada.usuarioId()).orElseThrow();
        assertThat(usuario.getPasswordHash()).isNotEqualTo(passwordTemporal);
        assertThat(passwordEncoder.matches(passwordTemporal, usuario.getPasswordHash())).isTrue();

        // La contrasena en claro NUNCA debe acabar en una fila de auditoria.
        auditoriaRepository.findByGestoriaIdOrderByIdAsc(creada.gestoriaId()).forEach(fila -> {
            assertThat(fila.getAntes() == null ? "" : fila.getAntes()).doesNotContain(passwordTemporal);
            assertThat(fila.getDespues() == null ? "" : fila.getDespues()).doesNotContain(passwordTemporal);
        });
    }

    @Test
    void laReentregaNoCreaDosCuentas() {
        nuevaAlta("cs_re", "sub_re", "reentrega@landing.test", "Cliente Reentrega");
        AltaAutomaticaService servicio = servicio(enlaceStripe("active"));

        servicio.procesarPendientes();
        List<AltaAutomaticaService.CuentaCreada> segunda = servicio.procesarPendientes();

        // El alta ya esta ENLAZADA tras la primera pasada: la segunda no encuentra PENDIENTE y no crea nada.
        assertThat(segunda).isEmpty();
        assertThat(gestoriaRepository.count()).isEqualTo(1);
        assertThat(usuarioRepository.count()).isEqualTo(1);
        assertThat(suscripcionRepository.count()).isEqualTo(1);
    }

    @Test
    void siYaExisteSuscripcionConEseSubscriptionIdNoCreaOtraCuenta() {
        nuevaAlta("cs_a", "sub_dup", "a@landing.test", "Cliente A");
        AltaAutomaticaService servicio = servicio(enlaceStripe("active"));
        servicio.procesarPendientes(); // crea la cuenta de sub_dup

        // Segunda alta (otra session) con el MISMO subscriptionId: una re-entrega con otra session.
        AltaPendiente segundaAlta = nuevaAlta("cs_b", "sub_dup", "b@landing.test", "Cliente B");
        List<AltaAutomaticaService.CuentaCreada> creadas = servicio.procesarPendientes();

        assertThat(creadas).isEmpty();
        assertThat(gestoriaRepository.count()).isEqualTo(1);
        assertThat(usuarioRepository.count()).isEqualTo(1);
        assertThat(suscripcionRepository.count()).isEqualTo(1);
        AltaPendiente recargada = altaPendienteRepository.findById(segundaAlta.getId()).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoAltaPendiente.ENLAZADA);
        assertThat(recargada.getGestoriaId()).isNotNull();
    }

    @Test
    void sinStripeConfiguradoNoHaceNada() {
        nuevaAlta("cs_nostripe", "sub_nostripe", "nostripe@landing.test", "Cliente Sin Stripe");
        // EnlaceStripeService con clave vacia -> stripeConfigurado() = false.
        EnlaceStripeService sinStripe = new EnlaceStripeService(altaPendienteRepository, "", "");

        List<AltaAutomaticaService.CuentaCreada> creadas = servicio(sinStripe).procesarPendientes();

        assertThat(creadas).isEmpty();
        assertThat(gestoriaRepository.count()).isZero();
        assertThat(usuarioRepository.count()).isZero();
        assertThat(suscripcionRepository.count()).isZero();
    }
}
