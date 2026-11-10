package com.ganera.core.ovz;

import java.util.List;

/**
 * Listas de valores de OVZ. Las que el manual publica (transporte, responsable) son definitivas;
 * el resto quedan vacías y {@code provisional=true} hasta la sesión de mapeo
 * ({@code docs/referencias/guion-sesion-mapeo-ovz.md}).
 */
public final class ListasOvz {

    private ListasOvz() {
    }

    public static final List<String> MEDIOS_TRANSPORTE = List.of(
            "Camión", "Barco", "Tren", "Avión", "Conducción a pie", "Otros");

    public static final List<String> TIPOS_RESPONSABLE = List.of(
            "Desconocido",
            "Titular de la Explotación",
            "Tratante-Comerciante-Operador",
            "Transportista",
            "Otros");

    /** Emisión del DI: textos del manual §2.1. */
    public static final List<String> EMISIONES_DI = List.of(
            "Prefiero que mis DI's sean emitidos en la OVZ",
            "Imprimiré los DI's desde la opción de animales sin DI");

    public record ListaOvz(String id, String etiqueta, boolean provisional, List<String> valores) {
    }

    public record ListasOvzResponse(List<ListaOvz> listas) {
    }

    public static ListasOvzResponse todas() {
        return new ListasOvzResponse(List.of(
                new ListaOvz("medio_transporte", "Medio de Transporte", false, MEDIOS_TRANSPORTE),
                new ListaOvz("tipo_responsable", "Tipo Responsable del Movimiento", false, TIPOS_RESPONSABLE),
                new ListaOvz("emision_di", "Emisión del DI", false, EMISIONES_DI),
                new ListaOvz("sexo", "Sexo", true, List.of()),
                new ListaOvz("raza", "Raza", true, List.of()),
                new ListaOvz("tipo_identificador", "Tipo Identificador", true, List.of()),
                new ListaOvz("aptitud_movimiento", "Aptitud del Movimiento", true, List.of("SACRIFICIO")),
                new ListaOvz("categoria_censo", "Categoría del censo", true, List.of())));
    }
}
