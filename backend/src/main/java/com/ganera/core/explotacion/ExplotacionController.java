package com.ganera.core.explotacion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExplotacionController {

    private final ExplotacionRepository explotacionRepository;

    public ExplotacionController(ExplotacionRepository explotacionRepository) {
        this.explotacionRepository = explotacionRepository;
    }

    /** El filtro gestoriaFilter ya esta activo para esta request -- findAll solo ve las
     * Explotaciones de la Gestoria autenticada, sin filtrado manual (mismo patron que AuthController.me()). */
    @GetMapping("/explotaciones")
    public Page<ExplotacionResponse> listar(Pageable pageable) {
        return explotacionRepository.findAll(pageable).map(ExplotacionResponse::from);
    }
}
