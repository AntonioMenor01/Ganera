package com.ganera.core.tramite;

/** Un Tramite con extraccion por hacer, con la Gestoria a la que pertenece (todas las operaciones
 * posteriores llevan ese gestoriaId explicito). */
public record TramitePendienteExtraccion(Long tramiteId, Long gestoriaId) {
}
