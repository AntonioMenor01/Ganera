package com.ganera.core.shared.web;

import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Lista blanca de campos de ordenacion para endpoints paginados. Sin ella, ?sort= acepta cualquier
 * propiedad de la entidad (incluidas las credenciales OVZ del Ganadero, que los DTOs ocultan) y un
 * campo inexistente acaba en 500. El controlador responde 400 con MOTIVO si no es valida.
 */
public final class OrdenacionPermitida {

    public static final String MOTIVO = "Campo de ordenación no permitido.";

    private OrdenacionPermitida() {
    }

    public static boolean esValida(Sort sort, Set<String> camposPermitidos) {
        return sort.stream().allMatch(orden -> camposPermitidos.contains(orden.getProperty()));
    }
}
