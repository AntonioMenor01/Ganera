package com.ganera.core.alta;

import com.ganera.core.facturacion.AltaPendiente;
import com.ganera.core.facturacion.AltaPendienteRepository;
import com.ganera.core.facturacion.EnlaceStripeFake;
import com.ganera.core.facturacion.EnlaceStripeService;
import com.ganera.core.facturacion.EstadoAltaPendiente;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.tiempo.RelojConfig;
import org.junit.jupiter.api.Test;
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
 * Emails del alta automatica: que se construyen con los datos correctos, que el aviso al equipo no va si la
 * direccion esta vacia, la idempotencia (no reenvia) y el reintento (equipo cuando se configura despues,
 * bienvenida regenerando la contrasena si fallo antes). Sin la guardia DML: la auditoria de la creacion ya se
 * comprueba en AltaAutomaticaServiceTest, y el reintento de bienvenida escribe en usuario sin auditar (es un
 * mecanismo interno).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import({AuditoriaService.class, RelojConfig.class})
class AltaAutomaticaEmailsTest {

    private static final Instant AHORA = Instant.parse("2026-11-23T10:15:30Z");
    private static final String URL_APP = "https://app.ganera.es";
    private static final String EQUIPO = "equipo@ganera.es";

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
    private PlatformTransactionManager transactionManager;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AltaAutomaticaService servicio(EmailServiceFalso email, String equipoEmail) {
        EnlaceStripeService enlace = new EnlaceStripeFake(altaPendienteRepository, "trialing", "gestion", "mensual");
        return new AltaAutomaticaService(altaPendienteRepository, gestoriaRepository, usuarioRepository,
                suscripcionRepository, enlace, passwordEncoder, auditoriaService, email,
                Clock.fixed(AHORA, ZoneOffset.UTC), transactionManager, URL_APP, equipoEmail);
    }

    private AltaPendiente nuevaAlta(String sessionId, String subscriptionId, String email, String nombre, String nif) {
        AltaPendiente alta = new AltaPendiente();
        alta.setStripeSessionId(sessionId);
        alta.setSubscriptionId(subscriptionId);
        alta.setEmail(email);
        alta.setNombre(nombre);
        alta.setNif(nif);
        alta.setPlan("gestion");
        alta.setEstado(EstadoAltaPendiente.PENDIENTE);
        alta.setCreadoEn(AHORA);
        return altaPendienteRepository.save(alta);
    }

    @Test
    void enviaLaBienvenidaAlClienteConLaContrasenaYLosDatosDeAcceso() {
        AltaPendiente alta = nuevaAlta("cs_b", "sub_b", "cliente@landing.test", "Cliente Uno", "12345678Z");
        EmailServiceFalso email = new EmailServiceFalso(true);

        List<AltaAutomaticaService.CuentaCreada> creadas = servicio(email, "").procesarPendientes();

        String password = creadas.get(0).passwordTemporal();
        List<EmailServiceFalso.Enviado> bienvenida = email.aDestinatario("cliente@landing.test");
        assertThat(bienvenida).hasSize(1);
        assertThat(bienvenida.get(0).asunto()).contains("Bienvenido");
        assertThat(bienvenida.get(0).cuerpo())
                .contains("cliente@landing.test")
                .contains(password)
                .contains(URL_APP)
                .contains("15 días");
        AltaPendiente recargada = altaPendienteRepository.findById(alta.getId()).orElseThrow();
        assertThat(recargada.getEmailBienvenidaEnviadoEn()).isEqualTo(AHORA);
        assertThat(recargada.getEmailEquipoEnviadoEn()).isNull();
    }

    @Test
    void noEnviaElAvisoAlEquipoSiLaDireccionEstaEnBlanco() {
        AltaPendiente alta = nuevaAlta("cs_v", "sub_v", "cliente@landing.test", "Cliente Uno", "12345678Z");
        EmailServiceFalso email = new EmailServiceFalso(true);

        servicio(email, "").procesarPendientes();

        // Solo se envio la bienvenida al cliente; nada al equipo.
        assertThat(email.enviados).extracting(EmailServiceFalso.Enviado::destinatario)
                .containsExactly("cliente@landing.test");
        assertThat(altaPendienteRepository.findById(alta.getId()).orElseThrow().getEmailEquipoEnviadoEn()).isNull();
    }

    @Test
    void enviaElAvisoAlEquipoConLosDatosDelAltaCuandoHayDireccion() {
        AltaPendiente alta = nuevaAlta("cs_e", "sub_e", "cliente@landing.test", "Cliente Uno", "12345678Z");
        EmailServiceFalso email = new EmailServiceFalso(true);

        servicio(email, EQUIPO).procesarPendientes();

        List<EmailServiceFalso.Enviado> alEquipo = email.aDestinatario(EQUIPO);
        assertThat(alEquipo).hasSize(1);
        assertThat(alEquipo.get(0).asunto()).contains("Nueva alta");
        assertThat(alEquipo.get(0).cuerpo())
                .contains("Cliente Uno")
                .contains("cliente@landing.test")
                .contains("12345678Z")
                .contains("gestion");
        assertThat(altaPendienteRepository.findById(alta.getId()).orElseThrow().getEmailEquipoEnviadoEn())
                .isEqualTo(AHORA);
    }

    @Test
    void noReenviaLosEmailsSiYaConstanComoEnviados() {
        nuevaAlta("cs_i", "sub_i", "cliente@landing.test", "Cliente Uno", "12345678Z");
        EmailServiceFalso email = new EmailServiceFalso(true);
        AltaAutomaticaService servicio = servicio(email, EQUIPO);

        servicio.procesarPendientes();
        servicio.procesarPendientes(); // re-entrega / siguiente pasada

        // Un unico envio de cada tipo pese a las dos pasadas.
        assertThat(email.aDestinatario("cliente@landing.test")).hasSize(1);
        assertThat(email.aDestinatario(EQUIPO)).hasSize(1);
    }

    @Test
    void reintentaElAvisoAlEquipoCuandoSeConfiguraLaDireccionDespues() {
        nuevaAlta("cs_r", "sub_r", "cliente@landing.test", "Cliente Uno", "12345678Z");
        EmailServiceFalso email = new EmailServiceFalso(true);

        // 1a pasada sin equipo-email configurado: solo bienvenida.
        servicio(email, "").procesarPendientes();
        assertThat(email.aDestinatario(EQUIPO)).isEmpty();

        // Se configura el equipo-email: la siguiente pasada reintenta y envia el aviso (sin reenviar bienvenida).
        servicio(email, EQUIPO).procesarPendientes();
        assertThat(email.aDestinatario(EQUIPO)).hasSize(1);
        assertThat(email.aDestinatario("cliente@landing.test")).hasSize(1);
    }

    @Test
    void reintentaLaBienvenidaRegenerandoLaContrasenaSiElPrimerEnvioFallo() {
        AltaPendiente alta = nuevaAlta("cs_f", "sub_f", "cliente@landing.test", "Cliente Uno", "12345678Z");
        EmailServiceFalso email = new EmailServiceFalso(true);
        email.fallar(true); // el primer envio falla (SMTP caido)
        AltaAutomaticaService servicio = servicio(email, "");

        servicio.procesarPendientes();
        // La cuenta se creo pese al fallo del email; la bienvenida queda pendiente.
        Usuario tras1 = usuarioRepository.findByEmail("cliente@landing.test").orElseThrow();
        String hashTras1 = tras1.getPasswordHash();
        assertThat(altaPendienteRepository.findById(alta.getId()).orElseThrow().getEmailBienvenidaEnviadoEn()).isNull();
        assertThat(email.aDestinatario("cliente@landing.test")).isEmpty();

        // El SMTP se recupera: la siguiente pasada regenera la contrasena y envia la bienvenida.
        email.fallar(false);
        servicio.procesarPendientes();

        assertThat(email.aDestinatario("cliente@landing.test")).hasSize(1);
        assertThat(altaPendienteRepository.findById(alta.getId()).orElseThrow().getEmailBienvenidaEnviadoEn())
                .isEqualTo(AHORA);
        // Se regenero la contrasena: el hash del usuario cambio y la nueva clave del email casa con el.
        Usuario tras2 = usuarioRepository.findByEmail("cliente@landing.test").orElseThrow();
        assertThat(tras2.getPasswordHash()).isNotEqualTo(hashTras1);
        String passwordEnEmail = entrePasswordStrong(email.aDestinatario("cliente@landing.test").get(0).cuerpo());
        assertThat(passwordEncoder.matches(passwordEnEmail, tras2.getPasswordHash())).isTrue();
    }

    /** Extrae la contrasena del cuerpo de bienvenida ("Contraseña temporal: &lt;strong&gt;PWD&lt;/strong&gt;"). */
    private static String entrePasswordStrong(String cuerpo) {
        String marca = "Contraseña temporal: <strong>";
        int inicio = cuerpo.indexOf(marca) + marca.length();
        return cuerpo.substring(inicio, cuerpo.indexOf("</strong>", inicio));
    }
}
