package com.ganera.core.shared.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * El origen del frontend que admite CORS ({@code ganera.frontend.origen} / {@code FRONTEND_ORIGEN}; despliegue en
 * Clever, T3). Tiene que ser un origen exacto, {@code esquema://host[:puerto]}: sin comodines, ruta (ni la barra
 * final, que el navegador nunca manda en la cabecera Origin), query, fragmento ni usuario. En produccion
 * ({@code exigirHttps}) solo {@code https}. Si no cumple, la app no arranca: un valor mal escrito dejaria el
 * frontend sin poder llamar a la API o, peor, abriria la API a quien no debe.
 */
final class OrigenFrontend {

    private OrigenFrontend() {
    }

    static String validar(String origen, boolean exigirHttps) {
        if (origen == null || origen.isBlank() || !origen.equals(origen.strip())) {
            throw invalido("vacio o con espacios");
        }
        URI uri;
        try {
            uri = new URI(origen);
        } catch (URISyntaxException e) {
            throw invalido("no es una URL");
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!esquema.equals("https") && !(esquema.equals("http") && !exigirHttps)) {
            throw invalido(exigirHttps ? "tiene que empezar por https://" : "tiene que empezar por http:// o https://");
        }
        if (uri.getHost() == null || uri.getHost().contains("*") || uri.getRawUserInfo() != null
                || !uri.getRawPath().isEmpty() || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw invalido("tiene que ser solo esquema://host[:puerto], sin barra final ni ruta");
        }
        // El navegador nunca manda en Origin el puerto por defecto ni un ":" sin puerto: con ellos no casaria nunca.
        int puertoPorDefecto = esquema.equals("https") ? 443 : 80;
        if (uri.getPort() == puertoPorDefecto || (uri.getPort() == -1 && origen.endsWith(":"))) {
            throw invalido("sin el puerto por defecto (" + puertoPorDefecto + ") ni dos puntos al final");
        }
        return origen;
    }

    private static IllegalStateException invalido(String motivo) {
        return new IllegalStateException("FRONTEND_ORIGEN (ganera.frontend.origen) no es un origen valido: " + motivo
                + ". Ejemplo: https://app.ganera.es");
    }
}
