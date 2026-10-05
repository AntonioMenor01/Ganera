package com.ganera.core.tramite;

import com.ganera.core.tramite.TramiteExtractionService.TramiteExtraido;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validacion en el servidor de lo que devuelve la IA (R3 del Prompt B1). Puro, sin Spring.
 *
 * <ul>
 *   <li>Cada crotal pasa por {@link CrotalNormalizador}; los invalidos (3 digitos o menos,
 *       caracteres raros, mas de 30, vacios o null) se descartan y se cuentan, sin lanzar.</li>
 *   <li>Los repetidos del mismo valor normalizado se colapsan (se queda el primero) y no cuentan
 *       como descartados.</li>
 *   <li>Como mucho {@value #MAX_CROTALES} crotales validos distintos, en el orden en que llegaron;
 *       de ahi en adelante cada valor distinto se descarta y se cuenta una vez.</li>
 * </ul>
 * El tipo se conserva tal cual ({@code null} = la IA no lo identifico).
 */
public final class ExtraccionValidador {

    public static final int MAX_CROTALES = 50;

    private ExtraccionValidador() {
    }

    public static ExtraccionValidada validar(TramiteExtraido extraido) {
        List<String> aceptados = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        int descartados = 0;
        List<String> crotales = extraido.crotales() != null ? extraido.crotales() : List.of();
        for (String crotal : crotales) {
            String valor;
            try {
                valor = CrotalNormalizador.normalizar(crotal).valor();
            } catch (CrotalInvalidoException e) {
                descartados++;
                continue;
            }
            if (!vistos.add(valor)) {
                continue;
            }
            if (aceptados.size() < MAX_CROTALES) {
                aceptados.add(valor);
            } else {
                descartados++;
            }
        }
        return new ExtraccionValidada(extraido.tipoTramite(), List.copyOf(aceptados), descartados);
    }

    /**
     * @param tipo        {@code null} si la IA devolvio NO_IDENTIFICADO.
     * @param crotales    normalizados, sin repetidos, como mucho {@value #MAX_CROTALES}.
     * @param descartados invalidos mas los que pasaron del maximo (D5).
     */
    public record ExtraccionValidada(TipoTramite tipo, List<String> crotales, int descartados) {
    }
}
