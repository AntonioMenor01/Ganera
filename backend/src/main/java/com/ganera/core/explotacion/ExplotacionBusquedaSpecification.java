package com.ganera.core.explotacion;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Specification de GET /explotaciones?q= (plan de la busqueda sin tildes, D5 y ajuste A3).
 * Las palabras ya llegan normalizadas (NormalizadorBusqueda.palabras): minusculas, sin tildes ni
 * puntuacion, asi que se comparan tal cual con las columnas normalizadas, sin lower().
 */
final class ExplotacionBusquedaSpecification {

    /** Caracter de escape del LIKE (debe coincidir con el {@code escape} de los predicados). */
    static final char ESCAPE_LIKE = '!';

    private ExplotacionBusquedaSpecification() {
    }

    /**
     * Explotaciones de {@code gestoriaId} (predicado explicito: no depende del gestoriaFilter
     * ambiente; lo protege ExplotacionControllerTest
     * .qDeVariasPalabrasSoloDevuelveYCuentaLasDeLaGestoriaSinDependerDelFiltroAmbiente, un
     * @DataJpaTest sin filtro: los E2E por HTTP no lo detectarian porque el filtro lo enmascara) en las que CADA palabra aparece ("contiene") en explotacion.busqueda (REGA + nombre)
     * o en ganadero.nombre_busqueda. Sin fetch: el Ganadero entra por path (join implicito) y lo
     * trae el @EntityGraph de ExplotacionRepository.findAll(Specification, Pageable), que Spring
     * Data aplica solo a la consulta de datos; un fetch aqui romperia la consulta de count.
     */
    static Specification<Explotacion> porPalabras(Long gestoriaId, List<String> palabras) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>(palabras.size() + 1);
            predicados.add(cb.equal(root.get("gestoria").get("id"), gestoriaId));
            for (String palabra : palabras) {
                String patron = patronContiene(palabra);
                predicados.add(cb.or(
                        cb.like(root.get("busqueda"), patron, ESCAPE_LIKE),
                        cb.like(root.get("ganadero").get("nombreBusqueda"), patron, ESCAPE_LIKE)));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    /** Patron LIKE "contiene": escapa el caracter de escape, {@code %} y {@code _} con
     * {@link #ESCAPE_LIKE} (para que coincidan literalmente) y lo envuelve en %...%. Con la regla
     * actual de NormalizadorBusqueda una palabra nunca lleva esos caracteres; el escape se mantiene
     * como defensa por si la regla cambia (D3). */
    static String patronContiene(String texto) {
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
