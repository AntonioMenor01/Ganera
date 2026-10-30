package com.ganera.core.explotacion;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Búsqueda de explotaciones para el buscador global. Reutiliza la Specification de GET /explotaciones
 * (REGA + nombre + nombre del ganadero, por palabras y sin tildes), con el gestoriaId explícito. El
 * Ganadero lo trae el @EntityGraph de findAll(Specification, Pageable), así que mapear a DTO no hace
 * N+1.
 */
@Service
public class ExplotacionBusquedaService {

    private final ExplotacionRepository explotacionRepository;

    public ExplotacionBusquedaService(ExplotacionRepository explotacionRepository) {
        this.explotacionRepository = explotacionRepository;
    }

    @Transactional(readOnly = true)
    public List<ExplotacionResultado> buscar(Long gestoriaId, List<String> palabras, int limite) {
        if (palabras.isEmpty()) {
            return List.of();
        }
        return explotacionRepository
                .findAll(ExplotacionBusquedaSpecification.porPalabras(gestoriaId, palabras),
                        PageRequest.of(0, limite, Sort.by("codigoRega", "id")))
                .map(e -> new ExplotacionResultado(
                        e.getId(), e.getCodigoRega(), e.getNombre(),
                        e.getGanadero().getId(), e.getGanadero().getNombre()))
                .getContent();
    }
}
