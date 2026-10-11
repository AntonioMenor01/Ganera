package com.ganera.core.whatsapp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tope de tamano del callback de estado de Twilio (B2 reducido, T4). Solo para
 * {@code POST /webhooks/twilio/whatsapp/estado} (lo registra {@link WhatsappFiltrosConfig} con esa ruta exacta y
 * antes de Spring Security), y ANTES de que nadie lea el cuerpo: {@code getParameterMap} de Tomcat lo consume
 * entero (hasta 2 MB por defecto) y la firma solo se puede validar despues.
 *
 * <ul>
 *   <li>{@code Content-Length} declarado de mas de {@value CallbackEstadoWhatsapp#TAMANO_MAXIMO_CUERPO} bytes →
 *       413 sin leer nada.</li>
 *   <li>Si no, el cuerpo se lee con tope (tambien sin {@code Content-Length}, p. ej. chunked): al pasarse, 413.</li>
 *   <li>Dentro del tope, la peticion sigue envuelta: los parametros salen de la query string y del cuerpo ya
 *       leido (formulario UTF-8, como lo manda Twilio), y {@code getInputStream} devuelve esos mismos bytes.
 *       Un formulario mal codificado (un {@code %} roto) → 400.</li>
 * </ul>
 * Los 413/400 van sin cuerpo y sin pasar por {@code /error}. Logs: nada del cuerpo.
 */
@Slf4j
public class LimiteCuerpoCallbackEstadoFilter extends OncePerRequestFilter {

    static final String RUTA = "/webhooks/twilio/whatsapp/estado";

    private final int tamanoMaximo;

    public LimiteCuerpoCallbackEstadoFilter() {
        this(CallbackEstadoWhatsapp.TAMANO_MAXIMO_CUERPO);
    }

    LimiteCuerpoCallbackEstadoFilter(int tamanoMaximo) {
        this.tamanoMaximo = tamanoMaximo;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > tamanoMaximo) {
            rechazar(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        byte[] cuerpo = leerConTope(request.getInputStream(), tamanoMaximo);
        if (cuerpo == null) {
            rechazar(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        Map<String, String[]> parametros;
        try {
            parametros = parametros(request.getQueryString(), esFormulario(request.getContentType()) ? cuerpo : null);
        } catch (IllegalArgumentException e) {
            rechazar(response, HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        cadena.doFilter(new PeticionLeida(request, cuerpo, parametros), response);
    }

    private static void rechazar(HttpServletResponse response, int status) {
        log.warn("Callback de estado de Twilio rechazado con {} antes de leer sus datos", status);
        response.setStatus(status);
        response.setContentLength(0);
    }

    /** Lee como mucho {@code tope} bytes; null si el cuerpo tiene mas. */
    static byte[] leerConTope(InputStream entrada, int tope) throws IOException {
        ByteArrayOutputStream leido = new ByteArrayOutputStream();
        byte[] bloque = new byte[1024];
        int n;
        while ((n = entrada.read(bloque)) != -1) {
            if (leido.size() + n > tope) {
                return null;
            }
            leido.write(bloque, 0, n);
        }
        return leido.toByteArray();
    }

    private static boolean esFormulario(String contentType) {
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded");
    }

    /** Query string primero y cuerpo despues (como Tomcat), cada nombre con todos sus valores en orden. */
    static Map<String, String[]> parametros(String queryString, byte[] formulario) {
        Map<String, List<String>> valores = new LinkedHashMap<>();
        anadir(valores, queryString);
        if (formulario != null) {
            anadir(valores, new String(formulario, StandardCharsets.UTF_8));
        }
        Map<String, String[]> resultado = new LinkedHashMap<>();
        valores.forEach((nombre, lista) -> resultado.put(nombre, lista.toArray(String[]::new)));
        return Collections.unmodifiableMap(resultado);
    }

    private static void anadir(Map<String, List<String>> valores, String codificado) {
        if (codificado == null || codificado.isEmpty()) {
            return;
        }
        for (String par : codificado.split("&")) {
            if (par.isEmpty()) {
                continue;
            }
            int igual = par.indexOf('=');
            String nombre = URLDecoder.decode(igual < 0 ? par : par.substring(0, igual), StandardCharsets.UTF_8);
            String valor = igual < 0 ? "" : URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8);
            valores.computeIfAbsent(nombre, n -> new ArrayList<>()).add(valor);
        }
    }

    /** La peticion con el cuerpo ya leido: parametros y cuerpo salen de memoria. */
    private static final class PeticionLeida extends HttpServletRequestWrapper {

        private final byte[] cuerpo;
        private final Map<String, String[]> parametros;

        PeticionLeida(HttpServletRequest request, byte[] cuerpo, Map<String, String[]> parametros) {
            super(request);
            this.cuerpo = cuerpo;
            this.parametros = parametros;
        }

        @Override
        public String getParameter(String nombre) {
            String[] lista = parametros.get(nombre);
            return lista == null || lista.length == 0 ? null : lista[0];
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return parametros;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(parametros.keySet());
        }

        @Override
        public String[] getParameterValues(String nombre) {
            String[] lista = parametros.get(nombre);
            return lista == null ? null : lista.clone();
        }

        @Override
        public int getContentLength() {
            return cuerpo.length;
        }

        @Override
        public long getContentLengthLong() {
            return cuerpo.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bytes = new ByteArrayInputStream(cuerpo);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bytes.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return bytes.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
