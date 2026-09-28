package com.ganera.core.tramite;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normaliza y clasifica un crotal escrito a mano (revision del Tramite) o extraido por la IA (3b).
 * Sin dependencias de Spring a proposito.
 *
 * Reglas (decision 20, ver plan Prompt A1 Task 5):
 * 1. Se quitan TODOS los espacios, incluidos los no separables (U+00A0, U+2007, U+202F) que llegan
 *    al pegar desde Excel, y los guiones, puntos y barras (decision 24: "ES-0100-0000-1234").
 *    Lo que queda debe ser [A-Za-z0-9]{1,30} (solo ASCII) -- si no (incluido vacio), invalido.
 *    Se pasa a mayusculas.
 * 2. Solo digitos y 3 o menos -> invalido ("demasiado corto"): con tan pocos digitos casi siempre
 *    seria ambiguo.
 * 3. Solo digitos, entre 4 y 12 -> INCOMPLETO (sufijo del crotal; un crotal espanol sin "ES" son
 *    exactamente 12 digitos).
 * 4. Empieza por dos letras (codigo de pais) o tiene 13 o mas digitos -> COMPLETO.
 * 5. Cualquier otra forma valida -> COMPLETO tambien (igualdad exacta; si no coincide sera
 *    NO_ENCONTRADO, sin rechazarla: la gestoria decide).
 */
public final class CrotalNormalizador {

    static final String MOTIVO_FORMATO = "Crotal no válido: solo letras y números, hasta 30 caracteres";
    static final String MOTIVO_DEMASIADO_CORTO = "Crotal demasiado corto: indica al menos los últimos 4 dígitos";

    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9]{1,30}");
    private static final Pattern SOLO_DIGITOS = Pattern.compile("[0-9]+");

    public enum TipoCrotal {
        /** Se resuelve por igualdad exacta con Animal.crotal. */
        COMPLETO,
        /** Ultimos digitos: se resuelve por sufijo dentro de la Explotacion del Tramite. */
        INCOMPLETO
    }

    public record CrotalNormalizado(String valor, TipoCrotal tipo) {
    }

    private CrotalNormalizador() {
    }

    /** Decision 24: guion, punto y barra se tratan como separadores visuales, igual que un espacio. */
    private static boolean esSeparador(int c) {
        return c == '-' || c == '.' || c == '/';
    }

    /** @throws CrotalInvalidoException con el motivo, si el crotal no es valido. */
    public static CrotalNormalizado normalizar(String crotal) {
        if (crotal == null) {
            throw new CrotalInvalidoException(MOTIVO_FORMATO);
        }
        StringBuilder sinEspacios = new StringBuilder(crotal.length());
        crotal.codePoints()
                .filter(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c) && !esSeparador(c))
                .forEach(sinEspacios::appendCodePoint);
        String valor = sinEspacios.toString();
        // Se valida ANTES de pasar a mayusculas: toUpperCase convierte p. ej. "ß" en "SS".
        if (!VALIDO.matcher(valor).matches()) {
            throw new CrotalInvalidoException(MOTIVO_FORMATO);
        }
        valor = valor.toUpperCase(Locale.ROOT);

        if (SOLO_DIGITOS.matcher(valor).matches()) {
            if (valor.length() <= 3) {
                throw new CrotalInvalidoException(MOTIVO_DEMASIADO_CORTO);
            }
            return new CrotalNormalizado(valor, valor.length() <= 12 ? TipoCrotal.INCOMPLETO : TipoCrotal.COMPLETO);
        }
        return new CrotalNormalizado(valor, TipoCrotal.COMPLETO);
    }
}
