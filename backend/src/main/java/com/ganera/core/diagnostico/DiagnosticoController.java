package com.ganera.core.diagnostico;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Endpoint de diagnostico TEMPORAL: comprueba si OVZnet responde desde donde corre el backend (p. ej. la IP
 * de Clever), para decidir el escalon 2. <b>Se elimina en cuanto tengamos el resultado.</b>
 *
 * <p>Protegido con {@code X-Internal-Secret} (mismo patron y secreto que {@code OnboardingController}:
 * comparacion en tiempo constante, falla cerrado sin secreto; {@code 401} si no es valido). No escribe nada
 * ni toca datos: por eso esta exento del inventario de auditoria (ver comentario en {@code InventarioAuditoriaTest}).
 */
@RestController
public class DiagnosticoController {

    private final DiagnosticoAlcanceService alcanceService;
    private final String onboardingSecret;

    DiagnosticoController(
            DiagnosticoAlcanceService alcanceService,
            @Value("${ganera.onboarding.secret:}") String onboardingSecret) {
        this.alcanceService = alcanceService;
        this.onboardingSecret = onboardingSecret;
    }

    @GetMapping("/internal/diagnostico/ovz-alcance")
    public ResponseEntity<DiagnosticoAlcanceResponse> ovzAlcance(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secretoRecibido) {
        if (!secretoValido(secretoRecibido)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(alcanceService.comprobar());
    }

    /** Comparacion en tiempo constante; falla cerrado si ONBOARDING_SECRET no esta configurado. */
    boolean secretoValido(String secretoRecibido) {
        if (onboardingSecret.isBlank() || secretoRecibido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                onboardingSecret.getBytes(StandardCharsets.UTF_8),
                secretoRecibido.getBytes(StandardCharsets.UTF_8));
    }
}
