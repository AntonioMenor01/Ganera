package com.ganera.core.ovz;

/** Resultado de {@link VerificacionBaja}: {@code motivo} es un texto para el gestor, sin ningún dato del trámite. */
public record ResultadoVerificacion(boolean ok, String motivo) {
}
