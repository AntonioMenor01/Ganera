package com.ganera.core.contacto;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normaliza un telefono escrito a mano (Excel, formularios) o recibido de
 * Twilio (prefijo "whatsapp:") a formato E.164. Sin dependencias de Spring a
 * proposito: lo reutilizara el webhook de Twilio en 3b.
 *
 * Reglas, en orden:
 * 1. null/blanco -> vacio. Se quita un prefijo "whatsapp:" (sin distinguir mayusculas).
 * 2. Se quitan espacios (incluidos los no separables U+00A0/U+2007/U+202F que
 *    llegan al pegar desde Excel), puntos y guiones; cualquier otro caracter no digito
 *    (salvo un '+' inicial) -> vacio.
 * 3. '+' + 8-15 digitos, el primero 1-9 (E.164: ningun prefijo de pais empieza
 *    por 0) -> se acepta tal cual; si empieza por +34, ademas debe
 *    ser +34 + 9 digitos que empiecen por 6, 7, 8 o 9. Se rechaza '+' seguido
 *    de exactamente 9 digitos que empiecen por 6, 7, 8 o 9: es un movil/fijo
 *    espanol al que le falta el 34, y guardarlo tal cual haria que nunca
 *    coincidiera con el From de Twilio (decision 14).
 * 4. 0034 + 9 digitos, o 34 + 9 digitos -> +34... (misma regla 6/7/8/9).
 * 5. 9 digitos que empiecen por 6, 7, 8 o 9 -> +34...
 * 6. Cualquier otra cosa -> vacio.
 */
public final class TelefonoNormalizador {

    private static final String PREFIJO_WHATSAPP = "whatsapp:";
    private static final Pattern SEPARADORES = Pattern.compile("[\\s\\u00A0\\u2007\\u202F.\\-]");
    private static final Pattern INTERNACIONAL = Pattern.compile("\\+[1-9]\\d{7,14}");
    private static final Pattern ESPANOL_SIN_34 = Pattern.compile("\\+[6789]\\d{8}");
    private static final Pattern NACIONAL_ES = Pattern.compile("[6789]\\d{8}");
    private static final Pattern SOLO_DIGITOS = Pattern.compile("\\d+");

    private TelefonoNormalizador() {
    }

    public static Optional<String> normalizar(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return Optional.empty();
        }
        String valor = telefono.strip();
        if (valor.toLowerCase(Locale.ROOT).startsWith(PREFIJO_WHATSAPP)) {
            valor = valor.substring(PREFIJO_WHATSAPP.length());
        }
        valor = SEPARADORES.matcher(valor).replaceAll("");

        if (valor.startsWith("+")) {
            if (!INTERNACIONAL.matcher(valor).matches()
                    || ESPANOL_SIN_34.matcher(valor).matches()) {
                return Optional.empty();
            }
            if (valor.startsWith("+34")) {
                return nacionalEspanol(valor.substring(3));
            }
            return Optional.of(valor);
        }

        if (!SOLO_DIGITOS.matcher(valor).matches()) {
            return Optional.empty();
        }
        if (valor.length() == 13 && valor.startsWith("0034")) {
            return nacionalEspanol(valor.substring(4));
        }
        if (valor.length() == 11 && valor.startsWith("34")) {
            return nacionalEspanol(valor.substring(2));
        }
        return nacionalEspanol(valor);
    }

    private static Optional<String> nacionalEspanol(String nueveDigitos) {
        if (!NACIONAL_ES.matcher(nueveDigitos).matches()) {
            return Optional.empty();
        }
        return Optional.of("+34" + nueveDigitos);
    }
}
