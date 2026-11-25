package com.ganera.core.alta;

import com.ganera.core.facturacion.AltaPendiente;
import com.ganera.core.facturacion.AltaPendienteRepository;
import com.ganera.core.facturacion.EnlaceStripeService;
import com.ganera.core.facturacion.EstadoAltaPendiente;
import com.ganera.core.facturacion.Suscripcion;
import com.ganera.core.facturacion.SuscripcionRepository;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.gestoria.Usuario;
import com.ganera.core.gestoria.UsuarioRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.Clave;
import com.ganera.core.shared.auditoria.DatosAuditoria;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.email.EmailService;
import com.stripe.exception.StripeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Alta automatica de cuentas a partir de los pagos de la landing (fase automatica que anunciaba la migracion
 * V24; rama feat/alta-automatica). Para cada {@link AltaPendiente} en {@link EstadoAltaPendiente#PENDIENTE}
 * crea Gestoria + Usuario + Suscripcion REUTILIZANDO las mismas piezas que el onboarding manual
 * ({@link GestoriaRepository}, {@link UsuarioRepository}, {@link SuscripcionRepository} y
 * {@link EnlaceStripeService#enlazar} para enlazar la Subscription de Stripe por su {@code subscription_id}),
 * sin tocar {@code OnboardingController} ni {@code StripeWebhookService}: la secuencia minima se duplica aqui.
 *
 * <p><b>Todo esto solo se dispara con el flag encendido</b> ({@code ganera.alta.automatica.activa=true}); el
 * flag vive en {@link AltaAutomaticaScheduler}, el unico que invoca este servicio en produccion. Apagado (el
 * valor por defecto) el alta sigue siendo manual (POST /internal/onboarding/gestoria) y esto nunca corre.
 *
 * <p><b>Idempotencia</b> (las re-entregas de Stripe no deben duplicar cuentas): no crea nada si la alta ya
 * esta ENLAZADA, si ya existe una {@link Suscripcion} con ese {@code subscription_id}, o si ya existe un
 * {@link Usuario} con ese email. Cada alta se procesa en su propia transaccion ({@link TransactionTemplate}):
 * un fallo de una fila no aborta el resto del lote.
 *
 * <p><b>Contrasena.</b> Se genera una temporal aleatoria y se guarda SOLO hasheada (PasswordEncoder). Se usa
 * en el momento para el email de bienvenida y se devuelve en claro UNICAMENTE en memoria dentro de
 * {@link CuentaCreada}. Nunca se escribe en logs ni en auditoria ni se persiste en claro.
 *
 * <p><b>Emails</b> (via {@link EmailService}, que esta fail-closed: si el email esta apagado no se envia nada
 * y se reintenta cuando se active). Tras crear la cuenta se envian dos correos, cada uno idempotente por su
 * columna {@code email_*_enviado_en} de {@code alta_pendiente}: no se reenvia si ya consta.
 * <ul>
 *   <li><b>Bienvenida</b> al cliente (al email del alta): enlace a la app ({@code ganera.frontend.origen}),
 *       email de acceso, contrasena temporal, aviso de cambiarla y los 15 dias de prueba.</li>
 *   <li><b>Aviso al equipo</b> a {@code ganera.notificaciones.equipo-email} (nombre, email, NIF, plan; NUNCA
 *       datos de tarjeta). Si esa direccion esta en blanco, no se envia.</li>
 * </ul>
 * Un fallo de email NO revierte la cuenta ya creada ni bloquea el planificador: se registra (sin datos
 * personales) y se reintenta en la siguiente pasada. En el reintento de la bienvenida, como la contrasena en
 * claro ya no existe (solo se guardo hasheada), se regenera una nueva y se actualiza el hash del Usuario.
 */
// Solo en el backend web (como SecurityConfig): el alta automatica usa ganera.frontend.origen (que en el perfil
// clever no tiene default, es FRONTEND_ORIGEN) y Stripe/email, variables del backend. El worker de OVZ arranca sin
// esas variables, asi que este bean no debe crearse alli (PerfilCleverWorkerArranqueTest).
@Slf4j
@Service
@ConditionalOnWebApplication(type = Type.SERVLET)
public class AltaAutomaticaService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int BYTES_PASSWORD = 24;

    private final AltaPendienteRepository altaPendienteRepository;
    private final GestoriaRepository gestoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final EnlaceStripeService enlaceStripeService;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final EmailService emailService;
    private final Clock reloj;
    private final TransactionTemplate transaccion;
    private final String urlApp;
    private final String equipoEmail;

    public AltaAutomaticaService(
            AltaPendienteRepository altaPendienteRepository,
            GestoriaRepository gestoriaRepository,
            UsuarioRepository usuarioRepository,
            SuscripcionRepository suscripcionRepository,
            EnlaceStripeService enlaceStripeService,
            PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService,
            EmailService emailService,
            Clock reloj,
            PlatformTransactionManager transactionManager,
            @Value("${ganera.frontend.origen:}") String urlApp,
            @Value("${ganera.notificaciones.equipo-email:}") String equipoEmail) {
        this.altaPendienteRepository = altaPendienteRepository;
        this.gestoriaRepository = gestoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.enlaceStripeService = enlaceStripeService;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.emailService = emailService;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transactionManager);
        this.urlApp = urlApp;
        this.equipoEmail = equipoEmail;
    }

    /**
     * Procesa todas las altas PENDIENTE. Devuelve las cuentas creadas en esta pasada (cada una con su
     * contrasena temporal en claro, solo en memoria). Sin Stripe configurado no hace nada: las altas de la
     * landing llevan una Subscription de Stripe que hay que enlazar.
     */
    public List<CuentaCreada> procesarPendientes() {
        if (!enlaceStripeService.stripeConfigurado()) {
            log.info("Stripe no configurado (stripe.api-key en blanco); se omite el alta automatica");
            return List.of();
        }
        List<CuentaCreada> creadas = new ArrayList<>();
        // 1. Crear cuentas de las altas PENDIENTE y mandarles los emails con la contrasena recien generada.
        for (AltaPendiente alta : altaPendienteRepository.findByEstadoOrderByIdAsc(EstadoAltaPendiente.PENDIENTE)) {
            Long altaId = alta.getId();
            try {
                CuentaCreada creada = transaccion.execute(status -> crearCuentaEnTransaccion(altaId));
                if (creada != null) {
                    creadas.add(creada);
                    // Solo ids: nunca email, nombre, nif ni la contrasena.
                    log.info("Cuenta creada automaticamente desde pago de la landing: altaId={}, gestoriaId={}, "
                            + "usuarioId={}", creada.altaId(), creada.gestoriaId(), creada.usuarioId());
                    // Emails FUERA de la transaccion de creacion: un fallo de email no revierte la cuenta.
                    enviarEmailsSiFaltan(altaId, creada.passwordTemporal());
                }
            } catch (RuntimeException e) {
                // Un fallo de una alta no aborta el lote. Solo la clase de la excepcion (sin datos personales).
                log.error("Fallo procesando el alta automatica altaId={}: excepcion={}", altaId, claseRaiz(e));
            }
        }
        // 2. Reintentar los emails pendientes de altas ya creadas por el alta automatica (SMTP caido antes, o
        //    equipo-email configurado despues). La bienvenida se regenera (ya no existe la contrasena en claro).
        for (AltaPendiente alta : altaPendienteRepository.findByEstadoAndProcesadaEnIsNotNullOrderByIdAsc(
                EstadoAltaPendiente.ENLAZADA)) {
            if (!tieneEmailPendiente(alta)) {
                continue;
            }
            Long altaId = alta.getId();
            try {
                enviarEmailsSiFaltan(altaId, null);
            } catch (RuntimeException e) {
                log.error("Fallo reintentando los emails del alta automatica altaId={}: excepcion={}",
                        altaId, claseRaiz(e));
            }
        }
        return creadas;
    }

    /** true si a esta alta le falta al menos un email que proceda enviar (bienvenida, o equipo si esta configurado). */
    private boolean tieneEmailPendiente(AltaPendiente alta) {
        boolean bienvenidaPendiente = alta.getEmailBienvenidaEnviadoEn() == null;
        boolean equipoPendiente = equipoConfigurado() && alta.getEmailEquipoEnviadoEn() == null;
        return bienvenidaPendiente || equipoPendiente;
    }

    /**
     * Envia los emails que todavia falten para esta alta (idempotente por las columnas email_*_enviado_en). Si
     * el email esta apagado/sin configurar no hace nada (se reintentara al activarlo). Un fallo de un envio se
     * registra y deja la columna en null para reintentar; nunca lanza al llamante.
     *
     * @param passwordRecienCreada contrasena en claro si la cuenta se acaba de crear en esta pasada; null en un
     *                             reintento, en cuyo caso la bienvenida regenera una contrasena nueva.
     */
    private void enviarEmailsSiFaltan(Long altaId, String passwordRecienCreada) {
        if (!emailService.configurado()) {
            // Ni spam por alta ni regeneracion inutil: se reintentara cuando el email este activo.
            log.info("Email no activo/configurado; no se envian los emails del alta automatica (se reintentara)");
            return;
        }
        AltaPendiente alta = altaPendienteRepository.findById(altaId).orElse(null);
        if (alta == null) {
            return;
        }
        if (alta.getEmailBienvenidaEnviadoEn() == null) {
            String password = passwordRecienCreada != null ? passwordRecienCreada : regenerarPassword(alta.getEmail());
            if (password != null) {
                PlantillasEmailAlta.Email email = PlantillasEmailAlta.bienvenida(
                        alta.getNombre(), alta.getEmail(), password, urlApp);
                if (emailService.enviarHtml(alta.getEmail(), email.asunto(), email.cuerpoHtml())) {
                    marcarEnviado(altaId, true);
                } else {
                    log.warn("No se pudo enviar el email de bienvenida: altaId={}", altaId);
                }
            }
        }
        if (equipoConfigurado() && alta.getEmailEquipoEnviadoEn() == null) {
            PlantillasEmailAlta.Email email = PlantillasEmailAlta.equipo(
                    alta.getNombre(), alta.getEmail(), alta.getNif(), alta.getPlan());
            if (emailService.enviarHtml(equipoEmail, email.asunto(), email.cuerpoHtml())) {
                marcarEnviado(altaId, false);
            } else {
                log.warn("No se pudo enviar el email de aviso al equipo: altaId={}", altaId);
            }
        }
    }

    private boolean equipoConfigurado() {
        return equipoEmail != null && !equipoEmail.isBlank();
    }

    /**
     * Reintento de la bienvenida: la contrasena en claro no se guarda, asi que se genera una nueva y se
     * actualiza el hash del Usuario (buscado por su email, que es el del alta). Devuelve la nueva en claro, o
     * null si no se encuentra el Usuario.
     */
    private String regenerarPassword(String emailUsuario) {
        String email = limpio(emailUsuario);
        if (email == null) {
            return null;
        }
        return transaccion.execute(status -> {
            Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
            if (usuario == null) {
                log.warn("Reintento de bienvenida sin Usuario para ese email; no se regenera contrasena");
                return null;
            }
            String nueva = generarPasswordTemporal();
            usuario.setPasswordHash(passwordEncoder.encode(nueva));
            // Nueva contrasena temporal (va por email): hay que cambiarla igual que la primera.
            usuario.setDebeCambiarPassword(true);
            usuarioRepository.save(usuario);
            return nueva;
        });
    }

    /** Marca en su propia transaccion la columna de email enviado (bienvenida o equipo). */
    private void marcarEnviado(Long altaId, boolean bienvenida) {
        transaccion.executeWithoutResult(status -> {
            AltaPendiente alta = altaPendienteRepository.findById(altaId).orElse(null);
            if (alta == null) {
                return;
            }
            if (bienvenida) {
                alta.setEmailBienvenidaEnviadoEn(reloj.instant());
            } else {
                alta.setEmailEquipoEnviadoEn(reloj.instant());
            }
            altaPendienteRepository.save(alta);
        });
    }

    /** Crea (o no, por idempotencia) la cuenta de una alta, dentro de la transaccion abierta por el llamante. */
    private CuentaCreada crearCuentaEnTransaccion(Long altaId) {
        AltaPendiente alta = altaPendienteRepository.findById(altaId).orElse(null);
        if (alta == null || alta.getEstado() != EstadoAltaPendiente.PENDIENTE) {
            return null; // ya procesada (re-entrega / carrera)
        }
        String subscriptionId = limpio(alta.getSubscriptionId());
        if (subscriptionId == null) {
            log.warn("Alta de la landing sin subscriptionId; no se puede enlazar con Stripe: altaId={}", altaId);
            return null;
        }
        String email = limpio(alta.getEmail());
        if (email == null) {
            log.warn("Alta de la landing sin email; no se puede crear el Usuario: altaId={}", altaId);
            return null;
        }

        // Idempotencia: ya existe una Suscripcion con ese subscriptionId -> no crear nada (solo marcar ENLAZADA).
        Optional<Suscripcion> suscripcionExistente = suscripcionRepository.findByStripeSubscriptionId(subscriptionId);
        if (suscripcionExistente.isPresent()) {
            Long gestoriaId = suscripcionExistente.get().getGestoria().getId();
            marcarEnlazada(alta, gestoriaId);
            log.info("Alta ya tenia suscripcion para ese subscriptionId; marcada ENLAZADA sin crear cuenta: "
                    + "altaId={}, gestoriaId={}", altaId, gestoriaId);
            return null;
        }
        // Idempotencia: ya existe un Usuario con ese email -> no crear nada (evita violar UNIQUE(email)).
        Optional<Usuario> usuarioExistente = usuarioRepository.findByEmail(email);
        if (usuarioExistente.isPresent()) {
            Long gestoriaId = usuarioExistente.get().getGestoria().getId();
            marcarEnlazada(alta, gestoriaId);
            log.info("Alta con un email que ya tiene usuario; marcada ENLAZADA sin crear cuenta: altaId={}, "
                    + "gestoriaId={}", altaId, gestoriaId);
            return null;
        }

        // --- Crear la cuenta (misma secuencia que el onboarding, duplicada a proposito para no tocar su codigo) ---
        Gestoria gestoria = gestoriaRepository.save(new Gestoria(nombreGestoria(alta)));

        String passwordTemporal = generarPasswordTemporal();
        Usuario usuario = new Usuario();
        usuario.setGestoria(gestoria);
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode(passwordTemporal));
        usuario.setNombre(limpio(alta.getNombre()) != null ? alta.getNombre().trim() : email);
        usuario.setActivo(true);
        // Contrasena temporal: el usuario DEBE cambiarla en el primer login (el correo de bienvenida lo pide).
        usuario.setDebeCambiarPassword(true);
        usuarioRepository.save(usuario);

        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setGestoria(gestoria);
        // Enlaza con Stripe y recalcula el estado desde la Subscription (no depende del orden de los webhooks).
        // Un fallo de Stripe revierte toda la transaccion de esta alta (y el lote continua con la siguiente).
        try {
            enlaceStripeService.enlazar(suscripcion, subscriptionId);
        } catch (StripeException e) {
            throw new EnlaceStripeFallidoException(e);
        }
        suscripcionRepository.save(suscripcion);

        // Auditoria (despues de los save, con los ids definitivos): actor PLANIFICADOR, la Gestoria recien
        // creada. NUNCA la contrasena. Misma terna que el onboarding manual (GESTORIA/USUARIO/SUSCRIPCION).
        auditoriaService.registrar(AccionAuditoria.GESTORIA_CREADA, gestoria.getId(), Actor.planificador(),
                EntidadAuditada.GESTORIA, gestoria.getId(), null, null);
        auditoriaService.registrar(AccionAuditoria.USUARIO_CREADO, gestoria.getId(), Actor.planificador(),
                EntidadAuditada.USUARIO, usuario.getId(), null, DatosAuditoria.con(Clave.ACTIVO, Boolean.TRUE));
        auditoriaService.registrar(AccionAuditoria.SUSCRIPCION_CREADA, gestoria.getId(), Actor.planificador(),
                EntidadAuditada.SUSCRIPCION, suscripcion.getId(), null,
                DatosAuditoria.con(Clave.ESTADO, suscripcion.getEstado()));

        marcarEnlazada(alta, gestoria.getId());
        return new CuentaCreada(altaId, gestoria.getId(), usuario.getId(), passwordTemporal);
    }

    /** Marca la alta ENLAZADA y fija gestoria_id / procesada_en (idempotente: se puede llamar sobre una ya marcada). */
    private void marcarEnlazada(AltaPendiente alta, Long gestoriaId) {
        alta.setEstado(EstadoAltaPendiente.ENLAZADA);
        alta.setGestoriaId(gestoriaId);
        alta.setProcesadaEn(reloj.instant());
        altaPendienteRepository.save(alta);
    }

    /** Nombre de la Gestoria a partir de lo que trae la alta; nunca en blanco (Gestoria.nombre es NOT NULL). */
    static String nombreGestoria(AltaPendiente alta) {
        if (limpio(alta.getNombre()) != null) {
            return alta.getNombre().trim();
        }
        if (limpio(alta.getEmail()) != null) {
            return alta.getEmail().trim();
        }
        if (limpio(alta.getNif()) != null) {
            return "Gestoria " + alta.getNif().trim();
        }
        return "Gestoria " + alta.getStripeSessionId();
    }

    /** Contrasena temporal aleatoria segura (24 bytes de SecureRandom en base64url, ~32 caracteres). */
    static String generarPasswordTemporal() {
        byte[] bytes = new byte[BYTES_PASSWORD];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String limpio(String valor) {
        if (valor == null) {
            return null;
        }
        String s = valor.trim();
        return s.isEmpty() ? null : s;
    }

    private static String claseRaiz(Throwable t) {
        Throwable causa = t;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        return causa.getClass().getName();
    }

    /**
     * Cuenta recien creada por el alta automatica. {@code passwordTemporal} va en CLARO y SOLO en memoria (se
     * usa en el acto para el email de bienvenida); no se persiste en claro ni se registra en logs/auditoria.
     */
    public record CuentaCreada(Long altaId, Long gestoriaId, Long usuarioId, String passwordTemporal) {
    }

    /** Fallo hablando con Stripe al enlazar; fuerza el rollback de la transaccion de esa alta. */
    static class EnlaceStripeFallidoException extends RuntimeException {
        EnlaceStripeFallidoException(StripeException causa) {
            super(causa);
        }
    }
}
