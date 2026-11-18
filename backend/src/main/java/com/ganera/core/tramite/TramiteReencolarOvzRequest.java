package com.ganera.core.tramite;

/**
 * Cuerpo de {@code POST /tramites/{id}/reencolar-ovz} (escalón 2, T2, D11). {@code version} es obligatoria (sin ella,
 * 400). {@code confirmadoNoPresentado} solo importa cuando el error es INCIERTO: debe ser {@code true} (el gestor ha
 * comprobado en OVZ que la baja no se presentó) para poder reencolar; en un error SEGURO se ignora.
 */
public record TramiteReencolarOvzRequest(Long version, Boolean confirmadoNoPresentado) {
}
