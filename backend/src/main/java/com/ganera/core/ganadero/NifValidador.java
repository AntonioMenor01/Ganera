package com.ganera.core.ganadero;

import java.util.Locale;

/**
 * Validación y normalización de NIF/NIE/CIF españoles (la clave de negocio del Ganadero). Lógica
 * pura, sin estado: el importador acepta lo que venga en el Excel tal cual, pero el alta MANUAL por
 * la UI valida el identificador para no meter typos en la base (D: alta manual, Antonio la abre).
 *
 * <p>Comprueba la letra/dígito de control:
 * <ul>
 *   <li><b>DNI</b>: 8 dígitos + letra de control (tabla T-R-W-A-G-M-…).</li>
 *   <li><b>NIE</b>: X/Y/Z + 7 dígitos + letra; la inicial cuenta como 0/1/2 para la letra.</li>
 *   <li><b>CIF</b>: letra de organización + 7 dígitos + control (dígito o letra, según el tipo; se
 *       acepta cualquiera de los dos para no rechazar CIF válidos por el matiz del tipo).</li>
 * </ul>
 */
public final class NifValidador {

    private NifValidador() {
    }

    private static final String LETRAS_DNI = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final String CONTROL_CIF = "JABCDEFGHI";
    /** Tipos de organización válidos como primera letra de un CIF. */
    private static final String TIPOS_CIF = "ABCDEFGHJNPQRSUVW";

    /**
     * Normaliza para guardar y comparar: sin espacios, puntos ni guiones, y en mayúsculas. No valida;
     * un valor nulo da cadena vacía. La unicidad por NIF se decide sobre esta forma normalizada.
     */
    public static String normalizar(String nif) {
        if (nif == null) return "";
        return nif.replaceAll("[\\s.\\-]", "").toUpperCase(Locale.ROOT);
    }

    /** true si, una vez normalizado, es un DNI, NIE o CIF con control correcto. */
    public static boolean esValido(String nif) {
        String valor = normalizar(nif);
        if (valor.length() != 9) return false;
        char inicial = valor.charAt(0);
        if (Character.isDigit(inicial)) return esDniValido(valor);
        if (inicial == 'X' || inicial == 'Y' || inicial == 'Z') return esNieValido(valor);
        if (TIPOS_CIF.indexOf(inicial) >= 0) return esCifValido(valor);
        return false;
    }

    private static boolean esDniValido(String valor) {
        String numero = valor.substring(0, 8);
        char letra = valor.charAt(8);
        if (!esNumerico(numero)) return false;
        return letra == letraDni(Integer.parseInt(numero));
    }

    private static boolean esNieValido(String valor) {
        char prefijo = valor.charAt(0);
        int equivalente = prefijo == 'X' ? 0 : prefijo == 'Y' ? 1 : 2;
        String cuerpo = valor.substring(1, 8);
        char letra = valor.charAt(8);
        if (!esNumerico(cuerpo)) return false;
        return letra == letraDni(Integer.parseInt(equivalente + cuerpo));
    }

    private static boolean esCifValido(String valor) {
        String digitos = valor.substring(1, 8);
        char control = valor.charAt(8);
        if (!esNumerico(digitos)) return false;
        int suma = 0;
        for (int i = 0; i < digitos.length(); i++) {
            int d = digitos.charAt(i) - '0';
            // Posiciones impares (1.ª, 3.ª…) se duplican y se suman sus cifras; las pares van tal cual.
            if (i % 2 == 0) {
                int doble = d * 2;
                suma += doble > 9 ? doble - 9 : doble;
            } else {
                suma += d;
            }
        }
        int digitoControl = (10 - (suma % 10)) % 10;
        char letraControl = CONTROL_CIF.charAt(digitoControl);
        // Según el tipo, el control es dígito o letra; se acepta cualquiera de los dos.
        return control == (char) ('0' + digitoControl) || control == letraControl;
    }

    private static char letraDni(int numero) {
        return LETRAS_DNI.charAt(numero % 23);
    }

    private static boolean esNumerico(String texto) {
        for (int i = 0; i < texto.length(); i++) {
            if (!Character.isDigit(texto.charAt(i))) return false;
        }
        return !texto.isEmpty();
    }
}
