package com.ganera.core.shared.auditoria;

import java.util.List;
import java.util.Optional;

/**
 * IP del cliente para la auditoria (plan 2026-10-21, D5). Pura, sin Spring. La IP solo se guarda en la fila de
 * auditoria: NUNCA decide nada de seguridad (ni login, ni limites).
 *
 * <ul>
 *   <li>{@code confiarEnXff = false} (por defecto: local y tests): la de la conexion ({@code getRemoteAddr()}).</li>
 *   <li>{@code true} (solo el perfil clever): todas las cabeceras {@code X-Forwarded-For} en orden, unidas por comas,
 *       y el ULTIMO elemento no vacio, que es el que anade el proxy de Clever (lo que el cliente mete a la izquierda
 *       no cuenta). Solo vale si es una IP literal IPv4 o IPv6 (analizador propio: NUNCA {@code InetAddress}, que
 *       resolveria un nombre por DNS); se quitan corchetes y puerto. Si no hay cabecera o el ultimo elemento no es
 *       una IP, la de la conexion (la del proxy: un dato real, aunque no sea el del cliente). El WARN lo emite el
 *       filtro, sin el valor de la cabecera.</li>
 * </ul>
 *
 * <p>Nunca devuelve mas de 45 caracteres (la columna {@code auditoria.ip}).
 */
public final class IpCliente {

    /** Longitud de {@code auditoria.ip}; la IPv6 mas larga (con IPv4 embebida) mide justo esto. */
    static final int LONGITUD_MAXIMA = 45;

    private IpCliente() {
    }

    public static String de(String remoteAddr, List<String> cabecerasXff, boolean confiarEnXff) {
        if (confiarEnXff) {
            Optional<String> desdeXff = ultimaIpValidaDeXff(cabecerasXff);
            if (desdeXff.isPresent()) {
                return desdeXff.get();
            }
        }
        return recortar(remoteAddr);
    }

    /** El ultimo elemento no vacio de las cabeceras, si es una IP literal (sin corchetes ni puerto). */
    public static Optional<String> ultimaIpValidaDeXff(List<String> cabecerasXff) {
        if (cabecerasXff == null || cabecerasXff.isEmpty()) {
            return Optional.empty();
        }
        String ultimo = null;
        for (String cabecera : cabecerasXff) {
            if (cabecera == null) {
                continue;
            }
            for (String elemento : cabecera.split(",", -1)) {
                String limpio = elemento.trim();
                if (!limpio.isEmpty()) {
                    ultimo = limpio;
                }
            }
        }
        if (ultimo == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(literal(ultimo));
    }

    /** La IP de un elemento ({@code 1.2.3.4}, {@code 1.2.3.4:80}, {@code ::1}, {@code [::1]}, {@code [::1]:443}). */
    private static String literal(String elemento) {
        if (elemento.length() > LONGITUD_MAXIMA + 8) { // corchetes y ":65535"
            return null;
        }
        if (elemento.startsWith("[")) {
            int cierre = elemento.indexOf(']');
            if (cierre < 0) {
                return null;
            }
            String resto = elemento.substring(cierre + 1);
            if (!resto.isEmpty() && !esPuerto(resto)) {
                return null;
            }
            String dentro = elemento.substring(1, cierre);
            return esIpv6(dentro) ? dentro : null;
        }
        int dosPuntos = elemento.indexOf(':');
        if (dosPuntos >= 0 && dosPuntos == elemento.lastIndexOf(':')) {
            // Un solo ':' -> IPv4 con puerto (una IPv6 siempre tiene al menos dos).
            String ip = elemento.substring(0, dosPuntos);
            return esPuerto(elemento.substring(dosPuntos)) && esIpv4(ip) ? ip : null;
        }
        if (dosPuntos < 0) {
            return esIpv4(elemento) ? elemento : null;
        }
        return esIpv6(elemento) ? elemento : null;
    }

    /** {@code :} seguido de 1 a 5 cifras. */
    private static boolean esPuerto(String conDosPuntos) {
        if (conDosPuntos.length() < 2 || conDosPuntos.length() > 6 || conDosPuntos.charAt(0) != ':') {
            return false;
        }
        for (int i = 1; i < conDosPuntos.length(); i++) {
            if (!esCifra(conDosPuntos.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Cuatro octetos decimales 0-255, sin ceros a la izquierda (salvo el propio "0"). */
    static boolean esIpv4(String texto) {
        String[] octetos = texto.split("\\.", -1);
        if (octetos.length != 4) {
            return false;
        }
        for (String octeto : octetos) {
            if (octeto.isEmpty() || octeto.length() > 3 || (octeto.length() > 1 && octeto.charAt(0) == '0')) {
                return false;
            }
            for (int i = 0; i < octeto.length(); i++) {
                if (!esCifra(octeto.charAt(i))) {
                    return false;
                }
            }
            if (Integer.parseInt(octeto) > 255) {
                return false;
            }
        }
        return true;
    }

    /**
     * IPv6 textual (RFC 4291, 2.2): ocho grupos de 1-4 cifras hexadecimales, o menos con un unico {@code ::}, y los
     * dos ultimos grupos pueden ser una IPv4. Sin zona ({@code %eth0}).
     */
    static boolean esIpv6(String texto) {
        if (texto.isEmpty() || texto.length() > LONGITUD_MAXIMA) {
            return false;
        }
        int compresion = texto.indexOf("::");
        if (compresion >= 0 && texto.indexOf("::", compresion + 1) >= 0) {
            return false;
        }
        if (compresion < 0) {
            return gruposValidos(texto, true) == 8;
        }
        String izquierda = texto.substring(0, compresion);
        String derecha = texto.substring(compresion + 2);
        int gruposIzquierda = izquierda.isEmpty() ? 0 : gruposValidos(izquierda, false);
        int gruposDerecha = derecha.isEmpty() ? 0 : gruposValidos(derecha, true);
        if (gruposIzquierda < 0 || gruposDerecha < 0) {
            return false;
        }
        return gruposIzquierda + gruposDerecha <= 7;
    }

    /**
     * Numero de grupos de 16 bits de una secuencia {@code h:h:...} (una IPv4 final cuenta 2, solo si
     * {@code admiteIpv4Final}); -1 si alguno no es valido.
     */
    private static int gruposValidos(String secuencia, boolean admiteIpv4Final) {
        String[] grupos = secuencia.split(":", -1);
        int total = 0;
        for (int i = 0; i < grupos.length; i++) {
            String grupo = grupos[i];
            boolean ultimo = i == grupos.length - 1;
            if (ultimo && admiteIpv4Final && grupo.indexOf('.') >= 0) {
                if (!esIpv4(grupo)) {
                    return -1;
                }
                total += 2;
                continue;
            }
            if (grupo.isEmpty() || grupo.length() > 4) {
                return -1;
            }
            for (int j = 0; j < grupo.length(); j++) {
                if (!esHexadecimal(grupo.charAt(j))) {
                    return -1;
                }
            }
            total++;
        }
        return total;
    }

    private static boolean esHexadecimal(char c) {
        return esCifra(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean esCifra(char c) {
        return c >= '0' && c <= '9';
    }

    private static String recortar(String texto) {
        if (texto == null || texto.length() <= LONGITUD_MAXIMA) {
            return texto;
        }
        return texto.substring(0, LONGITUD_MAXIMA);
    }
}
