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
import com.stripe.exception.StripeException;
import lombok.extern.slf4j.Slf4j;
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
 * <p><b>Contrasena.</b> Se genera una temporal aleatoria y se guarda SOLO hasheada (PasswordEncoder). Se
 * devuelve en claro UNICAMENTE en memoria dentro de {@link CuentaCreada}, para el futuro email de activacion
 * (Prompt 3). Nunca se escribe en logs ni en auditoria. De momento no hay email: al crear una cuenta solo se
 * registra un log con ids.
 */
@Slf4j
@Service
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
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    public AltaAutomaticaService(
            AltaPendienteRepository altaPendienteRepository,
            GestoriaRepository gestoriaRepository,
            UsuarioRepository usuarioRepository,
            SuscripcionRepository suscripcionRepository,
            EnlaceStripeService enlaceStripeService,
            PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService,
            Clock reloj,
            PlatformTransactionManager transactionManager) {
        this.altaPendienteRepository = altaPendienteRepository;
        this.gestoriaRepository = gestoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.enlaceStripeService = enlaceStripeService;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transactionManager);
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
        for (AltaPendiente alta : altaPendienteRepository.findByEstadoOrderByIdAsc(EstadoAltaPendiente.PENDIENTE)) {
            Long altaId = alta.getId();
            try {
                CuentaCreada creada = transaccion.execute(status -> crearCuentaEnTransaccion(altaId));
                if (creada != null) {
                    creadas.add(creada);
                    // Solo ids: nunca email, nombre, nif ni la contrasena.
                    log.info("Cuenta creada automaticamente desde pago de la landing: altaId={}, gestoriaId={}, "
                            + "usuarioId={}", creada.altaId(), creada.gestoriaId(), creada.usuarioId());
                }
            } catch (RuntimeException e) {
                // Un fallo de una alta no aborta el lote. Solo la clase de la excepcion (sin datos personales).
                log.error("Fallo procesando el alta automatica altaId={}: excepcion={}", altaId, claseRaiz(e));
            }
        }
        return creadas;
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
     * Cuenta recien creada por el alta automatica. {@code passwordTemporal} va en CLARO y SOLO en memoria (para
     * el email de activacion del Prompt 3); no se persiste en claro ni se registra en logs/auditoria.
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
