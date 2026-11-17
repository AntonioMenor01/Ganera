package com.ganera.core.whatsapp;

import com.ganera.core.contacto.TelefonoNormalizador;
import com.ganera.core.gestoria.Gestoria;
import com.ganera.core.gestoria.GestoriaRepository;
import com.ganera.core.shared.auditoria.AccionAuditoria;
import com.ganera.core.shared.auditoria.Actor;
import com.ganera.core.shared.auditoria.AuditoriaService;
import com.ganera.core.shared.auditoria.EntidadAuditada;
import com.ganera.core.shared.web.MotivoErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Optional;

/**
 * Alta interna de números de WhatsApp DEDICADO (plan 2026-11-16, T4). Como el onboarding: NO hay panel de
 * administración todavía, así que se protege con el mismo secreto compartido ({@code X-Internal-Secret}) en vez
 * de un JWT. Registra el número propio de una gestoría, al que escriben sus clientes; el webhook de recepción
 * resuelve la gestoría por el número de DESTINO contra la tabla que esto rellena.
 *
 * <p>Solo DEDICADO: el número COMPARTIDO no se registra aquí (se identifica por configuración, {@code To ==
 * whatsapp-from}); por eso se rechaza con 400 un número igual al whatsapp-from. {@code gestoria_id} siempre
 * presente, así que la fila de auditoría ({@code NUMERO_WHATSAPP_REGISTRADO}, actor SISTEMA) siempre la tiene.
 */
@RestController
public class NumeroWhatsappController {

    static final String MOTIVO_NUMERO_INVALIDO = "El número no es válido.";
    static final String MOTIVO_ES_COMPARTIDO =
            "Ese número es el número compartido; no se puede registrar como número propio.";
    static final String MOTIVO_FALTA_GESTORIA = "Falta la gestoría.";
    static final String MOTIVO_YA_REGISTRADO = "Ese número ya está registrado.";

    private final NumeroWhatsappRepository numeroWhatsappRepository;
    private final GestoriaRepository gestoriaRepository;
    private final AuditoriaService auditoriaService;
    private final Clock reloj;
    private final String onboardingSecret;
    /** El whatsapp-from configurado (normalizado a E.164): el número COMPARTIDO, que no se registra como propio. */
    private final Optional<String> numeroCompartido;

    public NumeroWhatsappController(
            NumeroWhatsappRepository numeroWhatsappRepository,
            GestoriaRepository gestoriaRepository,
            AuditoriaService auditoriaService,
            Clock reloj,
            @Value("${ganera.onboarding.secret:}") String onboardingSecret,
            @Value("${ganera.twilio.whatsapp-from:}") String whatsappFrom) {
        this.numeroWhatsappRepository = numeroWhatsappRepository;
        this.gestoriaRepository = gestoriaRepository;
        this.auditoriaService = auditoriaService;
        this.reloj = reloj;
        this.onboardingSecret = onboardingSecret;
        this.numeroCompartido = TelefonoNormalizador.normalizar(whatsappFrom);
    }

    @PostMapping("/internal/whatsapp/numeros")
    @Transactional
    public ResponseEntity<?> registrar(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secretoRecibido,
            @RequestBody NumeroWhatsappRequest request) {

        if (!secretoValido(secretoRecibido)) {
            return ResponseEntity.status(401).build();
        }
        Optional<String> numero = TelefonoNormalizador.normalizar(request.numero());
        if (numero.isEmpty()) {
            return error400(MOTIVO_NUMERO_INVALIDO);
        }
        if (numeroCompartido.isPresent() && numeroCompartido.get().equals(numero.get())) {
            return error400(MOTIVO_ES_COMPARTIDO);
        }
        if (request.gestoriaId() == null) {
            return error400(MOTIVO_FALTA_GESTORIA);
        }
        Gestoria gestoria = gestoriaRepository.findById(request.gestoriaId()).orElse(null);
        if (gestoria == null) {
            return ResponseEntity.notFound().build(); // 404 sin cuerpo
        }
        if (numeroWhatsappRepository.existsByNumero(numero.get())) {
            return ResponseEntity.status(409).body(new MotivoErrorResponse(MOTIVO_YA_REGISTRADO));
        }

        NumeroWhatsapp registrado = new NumeroWhatsapp();
        registrado.setGestoria(gestoria);
        registrado.setNumero(numero.get());
        registrado.setActivo(true);
        registrado.setCreatedAt(reloj.instant());
        numeroWhatsappRepository.saveAndFlush(registrado);

        // Fila de auditoria: el id del numero, NUNCA el telefono en texto (DatosAuditoria no admite String libre).
        auditoriaService.registrar(AccionAuditoria.NUMERO_WHATSAPP_REGISTRADO, gestoria.getId(), Actor.sistema(),
                EntidadAuditada.NUMERO_WHATSAPP, registrado.getId(), null, null);

        return ResponseEntity.status(201).body(new NumeroWhatsappResponse(registrado.getId(), gestoria.getId()));
    }

    private static ResponseEntity<MotivoErrorResponse> error400(String motivo) {
        return ResponseEntity.badRequest().body(new MotivoErrorResponse(motivo));
    }

    /** Comparacion en tiempo constante; falla cerrado si ONBOARDING_SECRET no esta configurado. */
    boolean secretoValido(String secretoRecibido) {
        if (onboardingSecret == null || onboardingSecret.isBlank() || secretoRecibido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                onboardingSecret.getBytes(StandardCharsets.UTF_8),
                secretoRecibido.getBytes(StandardCharsets.UTF_8));
    }
}
