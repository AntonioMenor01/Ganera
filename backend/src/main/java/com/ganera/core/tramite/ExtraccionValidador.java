package com.ganera.core.tramite;

import com.ganera.core.tramite.TramiteExtractionService.TramiteExtraido;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validacion en el servidor de lo que devuelve la IA (R3 del Prompt B1). Puro, sin Spring.
 *
 * <p>Red de seguridad contra falsos SIN_TRAMITE (D2 de SIN_TRAMITE): si la IA dice que el mensaje no
 * pide ningun tramite pero ha devuelto ALGUN crotal (la lista cruda, antes de normalizar: aunque
 * todos se descarten por invalidos), se trata como NO_IDENTIFICADO ({@code sinTramite = false}) y los
 * crotales se validan como siempre. Un mensaje que nombra un animal casi siempre pide algo.
 *
 * <ul>
 *   <li>Cada crotal pasa por {@link CrotalNormalizador}; los invalidos (3 digitos o menos,
 *       caracteres raros, mas de 30, vacios o null) se descartan y se cuentan, sin lanzar.</li>
 *   <li>Los repetidos del mismo valor normalizado se colapsan (se queda el primero) y no cuentan
 *       como descartados.</li>
 *   <li>Como mucho {@value #MAX_CROTALES} crotales validos distintos, en el orden en que llegaron;
 *       de ahi en adelante cada valor distinto se descarta y se cuenta una vez.</li>
 * </ul>
 * El tipo se conserva tal cual ({@code null} = NO_IDENTIFICADO o SIN_TRAMITE; los distingue {@code sinTramite}).
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
        // D2: con algun crotal (aunque sea invalido) no es SIN_TRAMITE; con un tipo tampoco (se contradicen y
        // manda el tipo: nunca se pierde un tramite identificado).
        boolean sinTramite = extraido.sinTramite() && crotales.isEmpty() && extraido.tipoTramite() == null;
        return new ExtraccionValidada(extraido.tipoTramite(), List.copyOf(aceptados), descartados, sinTramite);
    }

    /**
     * @param tipo        {@code null} si la IA devolvio NO_IDENTIFICADO o SIN_TRAMITE.
     * @param crotales    normalizados, sin repetidos, como mucho {@value #MAX_CROTALES}.
     * @param descartados invalidos mas los que pasaron del maximo (D5).
     * @param sinTramite  SIN_TRAMITE tras la red de seguridad D2: solo si la IA lo dijo, sin tipo y sin
     *                    ningun crotal.
     */
    public record ExtraccionValidada(TipoTramite tipo, List<String> crotales, int descartados, boolean sinTramite) {
    }
}
