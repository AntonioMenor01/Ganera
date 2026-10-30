package com.ganera.core.ganadero;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Specification del buscador global para ganaderos: dentro de la gestoría (predicado explícito, no
 * el gestoriaFilter ambiente), coincide si CADA palabra normalizada aparece en nombre_busqueda, o si
 * el NIF contiene el token tal cual (en mayúsculas). Mismo criterio «sin tildes, por palabras» que la
 * búsqueda de explotaciones.
 */
final class GanaderoBusquedaSpecification {

    static final char ESCAPE_LIKE = '!';

    private GanaderoBusquedaSpecification() {
    }

    static Specification<Ganadero> porNombreONif(Long gestoriaId, List<String> palabras, String nifToken) {
        return (root, query, cb) -> {
            Predicate tenant = cb.equal(root.get("gestoria").get("id"), gestoriaId);

            Predicate porNombre;
            if (palabras.isEmpty()) {
                porNombre = cb.disjunction(); // siempre falso: sin palabras no hay match por nombre
            } else {
                List<Predicate> deCadaPalabra = new ArrayList<>(palabras.size());
                for (String palabra : palabras) {
                    deCadaPalabra.add(cb.like(root.get("nombreBusqueda"), patronContiene(palabra), ESCAPE_LIKE));
                }
                porNombre = cb.and(deCadaPalabra.toArray(Predicate[]::new));
            }

            Predicate porNif = (nifToken == null || nifToken.isBlank())
                    ? cb.disjunction()
                    : cb.like(cb.upper(root.get("nif")), patronContiene(nifToken), ESCAPE_LIKE);

            return cb.and(tenant, cb.or(porNombre, porNif));
        };
    }

    /** Patrón LIKE «contiene», escapando el carácter de escape, % y _ con ESCAPE_LIKE. */
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
