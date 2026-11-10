package com.ganera.core.tramite;

import com.ganera.core.shared.security.GaneraUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AlertaController {

    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @GetMapping("/alertas")
    public List<AlertaResponse> listar(@AuthenticationPrincipal GaneraUserPrincipal principal) {
        return alertaService.listar(principal.gestoriaId());
    }
}
