package com.ganera.core.shared.auditoria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Contenido de {@code antes} / {@code despues} de una fila de auditoria (plan 2026-10-21, D8 y A3). Inmutable: cada
 * {@code y(...)} devuelve un objeto nuevo.
 *
 * <p>Solo admite, por TIPO, ids ({@code Long}), recuentos y versiones ({@code Integer}/{@code Long}), booleanos,
 * codigos ({@code Enum}, se guarda su {@code name()}), instantes ({@code Instant}, ISO-8601 UTC con {@code Z}), listas
 * de ids y listas de codigos ({@code conCodigos}/{@code yCodigos}). No hay ninguna sobrecarga {@code String},
 * {@code Object}, {@code CharSequence} ni {@code Map}: un texto, un telefono, un nombre, un NIF, un crotal o un id de
 * Stripe/Twilio no se pueden guardar aqui ni por descuido (lo vigila {@code DatosAuditoriaTest} por reflexion). Un
 * codigo que en el modelo es texto (p. ej. el codigo de error de Twilio) se pasa como {@code Integer} o como enum.
 *
 * <p>Garantia de longitud (la columna es VARCHAR(4000) y una fila demasiado larga NO puede tumbar la escritura de
 * negocio): como mucho {@value #MAXIMO_CLAVES} claves (de hasta 30 caracteres, vigilado en el test), codigos de hasta
 * {@value #MAXIMO_LONGITUD_CODIGO} caracteres y {@value #MAXIMO_ELEMENTOS} elementos (ids o codigos) en total entre
 * todas las listas (los demas se descartan y se anade {@code "truncado": true}). Pasarse de claves, repetir una clave,
 * un codigo demasiado largo o un id o codigo nulo en una lista son errores de programacion: excepcion inmediata, que
 * los tests de cada llamada detectan.
 *
 * <p>JSON canonico: claves en minusculas ordenadas alfabeticamente, sin espacios.
 */
public final class DatosAuditoria {

    static final int MAXIMO_CLAVES = 20;
    static final int MAXIMO_ELEMENTOS = 50;
    static final int MAXIMO_LONGITUD_CODIGO = 40;
    static final int LONGITUD_MAXIMA_JSON = 4000;

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Claves en su forma JSON -> Long, Integer, Boolean, String (nombre de enum o Instant ya formateado) o List. */
    private final TreeMap<String, Object> valores;
    /** Elementos (ids o codigos) ya guardados en listas, para el tope total. */
    private final int ids;
    private final int claves;

    private DatosAuditoria(TreeMap<String, Object> valores, int ids, int claves) {
        this.valores = valores;
        this.ids = ids;
        this.claves = claves;
    }

    public static DatosAuditoria con(Clave clave, Long valor) {
        return vacio().anadir(clave, valor);
    }

    public static DatosAuditoria con(Clave clave, Integer valor) {
        return vacio().anadir(clave, valor);
    }

    public static DatosAuditoria con(Clave clave, Boolean valor) {
        return vacio().anadir(clave, valor);
    }

    public static DatosAuditoria con(Clave clave, Enum<?> valor) {
        return vacio().anadir(clave, codigo(valor));
    }

    public static DatosAuditoria con(Clave clave, Instant valor) {
        return vacio().anadir(clave, instante(valor));
    }

    public static DatosAuditoria con(Clave clave, List<Long> ids) {
        return vacio().anadirIds(clave, ids);
    }

    public DatosAuditoria y(Clave clave, Long valor) {
        return anadir(clave, valor);
    }

    public DatosAuditoria y(Clave clave, Integer valor) {
        return anadir(clave, valor);
    }

    public DatosAuditoria y(Clave clave, Boolean valor) {
        return anadir(clave, valor);
    }

    public DatosAuditoria y(Clave clave, Enum<?> valor) {
        return anadir(clave, codigo(valor));
    }

    public DatosAuditoria y(Clave clave, Instant valor) {
        return anadir(clave, instante(valor));
    }

    public DatosAuditoria y(Clave clave, List<Long> ids) {
        return anadirIds(clave, ids);
    }

    /**
     * Lista de CODIGOS (p. ej. los campos cambiados de un contacto): solo enums, se guarda cada {@code name()} en el
     * orden dado. Metodo aparte y no una sobrecarga de {@code con}: {@code List<Long>} y {@code List<Enum>} tienen el
     * mismo borrado de tipos. Comparte el tope de {@value #MAXIMO_ELEMENTOS} elementos con las listas de ids.
     */
    public static DatosAuditoria conCodigos(Clave clave, List<? extends Enum<?>> codigos) {
        return vacio().anadirCodigos(clave, codigos);
    }

    /** Como {@link #conCodigos}. */
    public DatosAuditoria yCodigos(Clave clave, List<? extends Enum<?>> codigos) {
        return anadirCodigos(clave, codigos);
    }

    /** El JSON canonico que va a la columna. */
    String json() {
        String json;
        try {
            json = JSON.writeValueAsString(valores);
        } catch (JsonProcessingException e) {
            // Solo hay Long, Integer, Boolean, String y listas de Long o de String (codigos): no puede pasar.
            throw new IllegalStateException("No se ha podido serializar un dato de auditoria", e);
        }
        if (json.length() > LONGITUD_MAXIMA_JSON) {
            // Inalcanzable por construccion (ver la garantia de longitud del Javadoc de la clase).
            throw new IllegalStateException("Dato de auditoria de mas de " + LONGITUD_MAXIMA_JSON + " caracteres");
        }
        return json;
    }

    // --- internos ---

    private static DatosAuditoria vacio() {
        return new DatosAuditoria(new TreeMap<>(), 0, 0);
    }

    private static String codigo(Enum<?> valor) {
        if (valor == null) {
            return null;
        }
        String nombre = valor.name();
        if (nombre.length() > MAXIMO_LONGITUD_CODIGO) {
            throw new IllegalArgumentException("Codigo de auditoria de mas de " + MAXIMO_LONGITUD_CODIGO
                    + " caracteres: " + valor.getDeclaringClass().getSimpleName());
        }
        return nombre;
    }

    private static String instante(Instant valor) {
        return valor == null ? null : valor.toString();
    }

    private DatosAuditoria anadir(Clave clave, Object valor) {
        TreeMap<String, Object> nuevos = comprobarYCopiar(clave);
        nuevos.put(clave.json(), valor);
        return new DatosAuditoria(nuevos, ids, claves + 1);
    }

    private DatosAuditoria anadirIds(Clave clave, List<Long> lista) {
        if (lista == null) {
            return anadirLista(clave, null);
        }
        List<Object> elementos = new ArrayList<>(lista.size());
        for (Long id : lista) { // un List crudo con otra cosa dentro revienta aqui (ClassCastException)
            if (id == null) {
                throw new IllegalArgumentException("Id nulo en la lista de auditoria " + clave);
            }
            elementos.add(id);
        }
        return anadirLista(clave, elementos);
    }

    private DatosAuditoria anadirCodigos(Clave clave, List<? extends Enum<?>> lista) {
        if (lista == null) {
            return anadirLista(clave, null);
        }
        List<Object> elementos = new ArrayList<>(lista.size());
        for (Enum<?> valor : lista) {
            if (valor == null) {
                throw new IllegalArgumentException("Codigo nulo en la lista de auditoria " + clave);
            }
            elementos.add(codigo(valor));
        }
        return anadirLista(clave, elementos);
    }

    /** Ids o nombres de codigo ya validados; el tope de elementos es para el total del objeto. */
    private DatosAuditoria anadirLista(Clave clave, List<Object> elementos) {
        TreeMap<String, Object> nuevos = comprobarYCopiar(clave);
        if (elementos == null) {
            nuevos.put(clave.json(), null);
            return new DatosAuditoria(nuevos, ids, claves + 1);
        }
        int caben = MAXIMO_ELEMENTOS - ids;
        List<Object> guardados = elementos.size() <= caben ? elementos : elementos.subList(0, caben);
        if (guardados.size() < elementos.size()) {
            nuevos.put(Clave.TRUNCADO.json(), Boolean.TRUE);
        }
        nuevos.put(clave.json(), Collections.unmodifiableList(new ArrayList<>(guardados)));
        return new DatosAuditoria(nuevos, ids + guardados.size(), claves + 1);
    }

    private TreeMap<String, Object> comprobarYCopiar(Clave clave) {
        Objects.requireNonNull(clave, "clave");
        if (clave == Clave.TRUNCADO) {
            throw new IllegalArgumentException("TRUNCADO la pone DatosAuditoria sola al recortar una lista");
        }
        if (valores.containsKey(clave.json())) {
            throw new IllegalArgumentException("Clave de auditoria repetida: " + clave);
        }
        if (claves >= MAXIMO_CLAVES) {
            throw new IllegalArgumentException("Mas de " + MAXIMO_CLAVES + " claves en un dato de auditoria");
        }
        return new TreeMap<>(valores);
    }
}
