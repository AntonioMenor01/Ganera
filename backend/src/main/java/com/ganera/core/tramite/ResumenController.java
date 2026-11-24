package com.ganera.core.tramite;

import com.ganera.core.shared.security.GaneraUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /resumen: los números del dashboard de una gestoría. gestoriaId del JWT (nunca del path/body). Endpoint
 * aditivo: no cambia /tramites ni TramiteResponse. Requiere JWT como cualquier ruta no pública.
 */
@RestController
public class ResumenController {

    private final ResumenService resumenService;

    public ResumenController(ResumenService resumenService) {
        this.resumenService = resumenService;
    }

    @GetMapping("/resumen")
    public ResumenResponse resumen(@AuthenticationPrincipal GaneraUserPrincipal principal) {
        return resumenService.generar(principal.gestoriaId());
    }
}
