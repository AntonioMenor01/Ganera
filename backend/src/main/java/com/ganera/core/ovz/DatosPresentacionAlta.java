package com.ganera.core.ovz;

/**
 * Todo lo que el worker necesita para presentar un alta por nacimiento, cargado en una transacción corta
 * (plan 2026-11-23, T2). Los códigos ({@code sexoCodigo}, {@code razaCodigo}, {@code tipoIdentificadorCodigo},
 * {@code emisionDiCodigo}, {@code oficinaDiCodigo}) son valores del catálogo de OVZ ({@link ListasOvz}), no textos.
 *
 * <p>Nunca se registra ni se audita (lleva crotales, REGA y usuario OVZ): {@code toString()} redactado.
 */
public record DatosPresentacionAlta(Long tramiteId, Long ganaderoId, String crotalTernero, String crotalMadre,
                                    String rega, String fechaNacimientoDdMmAaaa, String sexoCodigo,
                                    String razaCodigo, String tipoIdentificadorCodigo,
                                    String fechaIdentificacionDdMmAaaa, String emisionDiCodigo,
                                    String oficinaDiCodigo, String usuarioOvz, boolean usaCertificado) {

    @Override
    public String toString() {
        return "DatosPresentacionAlta[REDACTADO]";
    }
}
