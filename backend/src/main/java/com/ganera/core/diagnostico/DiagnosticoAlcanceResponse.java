package com.ganera.core.diagnostico;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Respuesta del diagnostico de alcance de OVZ (endpoint TEMPORAL). Solo una de las dos formas:
 * {@code {"codigoHttp": N}} si OVZ respondio, o {@code {"error": "timeout"|"conexion"}} si no. NUNCA
 * lleva el cuerpo de OVZ, ni cabeceras, ni credenciales.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiagnosticoAlcanceResponse(Integer codigoHttp, String error) {

    static DiagnosticoAlcanceResponse conCodigo(int codigo) {
        return new DiagnosticoAlcanceResponse(codigo, null);
    }

    static DiagnosticoAlcanceResponse conError(String error) {
        return new DiagnosticoAlcanceResponse(null, error);
    }
}
