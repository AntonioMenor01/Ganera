package com.ganera.core.ovz;

/**
 * Valores que el worker teclea en la pantalla 2 del alta por nacimiento en OVZ (plan 2026-11-23, T4). Son códigos
 * de los catálogos de OVZ ({@link ListasOvz}) y fechas ya en {@code dd/MM/yyyy}. {@code oficinaDiCodigo} puede ser
 * {@code null} (solo cuenta si la emisión es en la OVZ). Nunca se registra ni se audita: {@code toString()} redactado.
 */
public record DatosFormularioAlta(String crotalMadre, String fechaNacimientoDdMmAaaa, String sexoCodigo,
                                  String tipoIdentificadorCodigo, String fechaIdentificacionDdMmAaaa,
                                  String razaCodigo, String emisionDiCodigo, String oficinaDiCodigo) {

    @Override
    public String toString() {
        return "DatosFormularioAlta[REDACTADO]";
    }
}
