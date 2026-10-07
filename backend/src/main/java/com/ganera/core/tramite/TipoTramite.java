package com.ganera.core.tramite;

/**
 * Tipo de tramite: cada valor es un formulario de OVZNET (ficha OVZ, T1; antes de la V20 eran
 * ALTA, BAJA, CENSO, MOVIMIENTO y DEMORA). El catalogo, los campos de cada formulario y el porque
 * estan en {@code docs/referencias/ovz-tramites-bovino.md}.
 *
 * <p>Si se anade un valor, hay que anadirlo tambien a
 * {@link AnthropicTramiteExtractionService.TipoExtraido}, describirlo en su prompt y en
 * {@code TIPOS_TRAMITE} del frontend; la columna {@code tramite.tipo_tramite} admite 30 caracteres.
 */
public enum TipoTramite {
    /** Alta individual: nacimiento de un ternero en la explotacion. */
    ALTA_NACIMIENTO,
    /** Baja de bovino: muerte en la explotacion (incluido el sacrificio alli mismo). */
    BAJA_MUERTE,
    /** Solicitud de movimiento (guia de salida): venta, matadero, cebadero, feria o pastos. */
    SOLICITUD_MOVIMIENTO,
    /** Confirmacion de movimientos: entrada de animales que vienen de otra explotacion. */
    CONFIRMACION_MOVIMIENTO,
    /** Declaracion de censo de la explotacion. */
    DECLARACION_CENSO,
    /** Animales con demora: identificacion de animales dados de alta con demora de crotalizacion. */
    DEMORA_CROTALIZACION
}
