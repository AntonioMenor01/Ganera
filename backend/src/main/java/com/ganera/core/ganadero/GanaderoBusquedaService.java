package com.ganera.core.ganadero;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Búsqueda de ganaderos para el buscador global. gestoriaId siempre explícito (la Specification lo
 * pone como predicado real, no depende del gestoriaFilter ambiente). Devuelve DTOs, nunca la entidad.
 */
@Service
public class GanaderoBusquedaService {

    private final GanaderoRepository ganaderoRepository;

    public GanaderoBusquedaService(GanaderoRepository ganaderoRepository) {
        this.ganaderoRepository = ganaderoRepository;
    }

    @Transactional(readOnly = true)
    public List<GanaderoResultado> buscar(Long gestoriaId, List<String> palabras, String nifToken, int limite) {
        if (palabras.isEmpty() && (nifToken == null || nifToken.isBlank())) {
            return List.of();
        }
        return ganaderoRepository
                .findAll(GanaderoBusquedaSpecification.porNombreONif(gestoriaId, palabras, nifToken),
                        PageRequest.of(0, limite, Sort.by("nombre", "id")))
                .map(g -> new GanaderoResultado(g.getId(), g.getNombre(), g.getNif()))
                .getContent();
    }
}
