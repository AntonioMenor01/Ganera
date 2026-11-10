package com.ganera.core.tramite;

import java.time.LocalDate;
import java.util.List;

/**
 * Cuerpo de {@code POST /tramites}: alta MANUAL por la gestoría. {@code tipoTramite} y
 * {@code explotacionId} son obligatorios (el resto, opcional). {@code crotales} es la lista
 * completa (vacía o ausente = sin crotales). Los campos OVZ siguen el mismo criterio que el
 * PATCH: cadena en blanco se guarda como null.
 */
public record TramiteAltaRequest(
        String tipoTramite,
        Long explotacionId,
        Long contactoId,
        List<String> crotales,
        LocalDate fechaHecho,
        LocalDate fechaNacimiento,
        String sexo,
        String crotalMadreIndicado,
        String numeroMer,
        String destinoRega,
        String destinoTexto,
        String consignatario,
        String aptitudMovimiento,
        LocalDate fechaIdentificacion) {
}
