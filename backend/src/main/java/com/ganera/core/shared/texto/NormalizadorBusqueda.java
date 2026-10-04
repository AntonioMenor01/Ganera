package com.ganera.core.shared.texto;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normaliza texto para la busqueda de explotaciones sin tildes y por palabras.
 * Sin dependencias de Spring a proposito: la usan las entidades (columnas
 * explotacion.busqueda y ganadero.nombre_busqueda), la migracion V18 (que
 * rellena las filas existentes por JDBC) y la consulta de GET /explotaciones?q=.
 * Las tres deben aplicar exactamente la misma regla. Si la regla cambia, hace
 * falta una migracion nueva que recalcule las columnas: la V18 no se vuelve a
 * ejecutar (las migraciones Java no tienen checksum de contenido).
 *
 * Regla, en orden:
 * 0. Se eliminan, sin dejar hueco, los simbolos modificadores (\p{Sk}: acentos
 *    sueltos de espaciado como U+00B4, U+00A8, U+00B8, U+02D8-U+02DD, '`', '^').
 *    Va antes del NFKD porque NFKD los convierte en espacio + marca y partiria
 *    la palabra ("Mart" + U+00B4 + "in" -> "mart in" en vez de "martin").
 * 1. NFKD y se quitan las marcas combinantes: a con tilde -> a, u con dieresis -> u,
 *    enie -> n, c cedilla -> c, ordinales (º, ª) -> o, a, espacio duro -> espacio.
 * 2. Minusculas (Locale.ROOT).
 * 3. Se elimina, sin dejar hueco, todo lo que no sea letra, numero o espacio:
 *    guiones, puntos, apostrofos, barras, y los comodines de LIKE (%, _, !).
 * 4. Todo el espacio en blanco Unicode se colapsa a un espacio y se recorta.
 * null -> "".
 *
 * A sabiendas, no se pliegan letras que NFKD no descompone, ajenas al espanol:
 * U+00DF (eszett), U+0142 (l con barra), U+00F8 (o con barra), U+00E6 (ae),
 * U+0153 (oe), U+0111 (d con barra) y similares se quedan como estan.
 */
public final class NormalizadorBusqueda {

    private static final Pattern SIMBOLOS_MODIFICADORES = Pattern.compile("\\p{Sk}+");
    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern NO_LETRA_NUMERO_NI_ESPACIO =
            Pattern.compile("[^\\p{L}\\p{N}\\s]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ESPACIOS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private NormalizadorBusqueda() {
    }

    public static String normalizar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        String valor = SIMBOLOS_MODIFICADORES.matcher(texto).replaceAll("");
        valor = Normalizer.normalize(valor, Normalizer.Form.NFKD);
        valor = MARCAS.matcher(valor).replaceAll("");
        valor = valor.toLowerCase(Locale.ROOT);
        valor = NO_LETRA_NUMERO_NI_ESPACIO.matcher(valor).replaceAll("");
        return ESPACIOS.matcher(valor).replaceAll(" ").strip();
    }

    /**
     * Valor de la columna explotacion.busqueda: normalizar(codigoRega) + " " +
     * normalizar(nombre), sin espacio sobrante si uno de los dos queda vacio
     * (null -> ""). La usan la entidad Explotacion y la migracion V18, para que
     * el valor sea identico en los dos sitios. Los campos se unen con espacio,
     * asi que una palabra de la consulta nunca encaja "a caballo" entre ambos.
     */
    public static String textoExplotacion(String codigoRega, String nombre) {
        String rega = normalizar(codigoRega);
        String nombreNormalizado = normalizar(nombre);
        if (rega.isEmpty()) {
            return nombreNormalizado;
        }
        if (nombreNormalizado.isEmpty()) {
            return rega;
        }
        return rega + " " + nombreNormalizado;
    }

    /**
     * Palabras de la consulta (ajuste A2): se parte q por espacio en blanco
     * Unicode y cada trozo se normaliza por separado; los que quedan vacios
     * ("-" en "martin - perez") se descartan, igual que las repetidas, en el
     * orden de primera aparicion. null/blanco -> lista vacia. Lista inmutable.
     * Se vuelve a partir cada trozo normalizado como defensa: aunque los \p{Sk}
     * ya no lleguen al NFKD, otros caracteres (U+203E, U+2017, formas arabes)
     * siguen descomponiendose en espacio + marca, y una palabra con espacio
     * dentro seria un LIKE '%a b%' que no coincide con normalizar().
     */
    public static List<String> palabras(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        Set<String> palabras = new LinkedHashSet<>();
        for (String trozo : ESPACIOS.split(q)) {
            String normalizado = normalizar(trozo);
            if (normalizado.isEmpty()) {
                continue;
            }
            for (String palabra : normalizado.split(" ")) {
                palabras.add(palabra);
            }
        }
        return List.copyOf(palabras);
    }
}
