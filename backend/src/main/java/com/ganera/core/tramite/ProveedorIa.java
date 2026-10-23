package com.ganera.core.tramite;

/**
 * Proveedor de IA elegible para la extraccion de trámites. Se elige por la variable de entorno
 * {@code AI_PROVIDER} ({@code anthropic} | {@code mistral}); {@link #ANTHROPIC} es el valor por
 * defecto. El objetivo no es cambiar de proveedor en produccion, sino poder comparar Mistral con
 * Haiku: Anthropic sigue siendo el proveedor por defecto.
 */
public enum ProveedorIa {
    ANTHROPIC,
    MISTRAL;

    /** Si {@code AI_PROVIDER} no existe o no se reconoce, se usa este (y se registra en el log). */
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
