package com.ganera.core.search;

import com.ganera.core.explotacion.AnimalRepository;
import com.ganera.core.explotacion.AnimalResultado;
import com.ganera.core.explotacion.ExplotacionBusquedaService;
import com.ganera.core.explotacion.ExplotacionResultado;
import com.ganera.core.ganadero.GanaderoBusquedaService;
import com.ganera.core.ganadero.GanaderoResultado;
import com.ganera.core.shared.texto.NormalizadorBusqueda;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Buscador global: cruza ganaderos (nombre o NIF), explotaciones (REGA, nombre o nombre del ganadero)
 * y crotales, cada grupo limitado, SIEMPRE dentro de la gestoría del JWT. Orquesta las búsquedas de
 * cada dominio (cada una pone su gestoriaId como predicado real).
 */
@Service
public class BuscadorService {

    /** Resultados por grupo: suficiente para «saltar a» sin volcar listados enteros. */
    static final int LIMITE_POR_GRUPO = 6;
    /** Por debajo de 2 caracteres (tras recortar) no se busca: demasiado ruido y consultas caras. */
    static final int LONGITUD_MINIMA = 2;
    private static final char ESCAPE_LIKE = '!';

    private final GanaderoBusquedaService ganaderoBusquedaService;
    private final ExplotacionBusquedaService explotacionBusquedaService;
    private final AnimalRepository animalRepository;

    public BuscadorService(GanaderoBusquedaService ganaderoBusquedaService,
                           ExplotacionBusquedaService explotacionBusquedaService,
                           AnimalRepository animalRepository) {
        this.ganaderoBusquedaService = ganaderoBusquedaService;
        this.explotacionBusquedaService = explotacionBusquedaService;
        this.animalRepository = animalRepository;
    }

    public BusquedaResponse buscar(Long gestoriaId, String texto) {
        String q = texto == null ? "" : texto.strip();
        if (q.length() < LONGITUD_MINIMA) {
            return BusquedaResponse.vacia();
        }
        List<String> palabras = NormalizadorBusqueda.palabras(q);
        // Token «tal cual» (sin espacios/puntos/guiones, en mayúsculas) para NIF y crotal.
        String token = q.replaceAll("[\\s.-]", "").toUpperCase(Locale.ROOT);

        List<GanaderoResultado> ganaderos =
                ganaderoBusquedaService.buscar(gestoriaId, palabras, token, LIMITE_POR_GRUPO);
        List<ExplotacionResultado> explotaciones =
                explotacionBusquedaService.buscar(gestoriaId, palabras, LIMITE_POR_GRUPO);
        List<AnimalResultado> animales = token.isBlank()
                ? List.of()
                : animalRepository.buscarPorCrotal(gestoriaId, patronContiene(token),
                        PageRequest.of(0, LIMITE_POR_GRUPO));

        return new BusquedaResponse(ganaderos, explotaciones, animales);
    }

    /** Patrón LIKE «contiene», escapando el carácter de escape, % y _ con {@link #ESCAPE_LIKE}. */
    private static String patronContiene(String texto) {
        StringBuilder patron = new StringBuilder(texto.length() + 2).append('%');
        for (char c : texto.toCharArray()) {
            if (c == ESCAPE_LIKE || c == '%' || c == '_') {
                patron.append(ESCAPE_LIKE);
            }
            patron.append(c);
        }
        return patron.append('%').toString();
    }
}
