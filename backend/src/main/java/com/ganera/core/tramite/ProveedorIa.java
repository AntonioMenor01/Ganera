package com.ganera.core.tramite;

/**
 * Proveedor de IA elegible para la extraccion de trámites. Se elige por la variable de entorno
 * {@code AI_PROVIDER} ({@code mistral} | {@code anthropic}). El por defecto cuando {@code AI_PROVIDER}
 * no esta definida es {@code mistral} (lo pone {@code application.yml} / {@link ProveedorIaConfig});
 * {@link #ANTHROPIC} queda como {@link #POR_DEFECTO}: el fallback para un valor vacio o no reconocido,
 * y sigue disponible a mano con {@code AI_PROVIDER=anthropic}.
 */
public enum ProveedorIa {
    ANTHROPIC,
    MISTRAL;

    /**
     * Fallback cuando {@code AI_PROVIDER} esta vacia o no se reconoce (se registra en el log). No es el
     * por defecto del sistema cuando la variable no esta definida: eso lo pone {@code application.yml}
     * con {@code ${AI_PROVIDER:mistral}}.
     */
    public static final ProveedorIa POR_DEFECTO = ANTHROPIC;

    /**
     * Resultado de interpretar el valor de {@code AI_PROVIDER}.
     *
     * @param proveedor      el proveedor elegido (siempre uno valido; {@link #POR_DEFECTO} si no se
     *                       reconocio el valor o no habia).
     * @param reconocido     {@code false} solo cuando habia un valor no vacio que no corresponde a
     *                       ningun proveedor: en ese caso conviene avisar en el log. Un valor ausente
     *                       o en blanco es el caso normal (se usa el por defecto sin aviso de error).
     * @param valorOriginal  el texto recibido, ya recortado (puede ser {@code null} o vacio).
     */
    public record Seleccion(ProveedorIa proveedor, boolean reconocido, String valorOriginal) {
    }

    /**
     * Interpreta el valor de {@code AI_PROVIDER} sin distinguir mayusculas y recortando espacios.
     * Nunca lanza: un valor ausente, en blanco o no reconocido devuelve {@link #POR_DEFECTO}.
     */
    public static Seleccion resolver(String valor) {
        if (valor == null || valor.isBlank()) {
            return new Seleccion(POR_DEFECTO, true, valor);
        }
        String limpio = valor.trim();
        for (ProveedorIa proveedor : values()) {
            if (proveedor.name().equalsIgnoreCase(limpio)) {
                return new Seleccion(proveedor, true, limpio);
            }
        }
        return new Seleccion(POR_DEFECTO, false, limpio);
    }
}
