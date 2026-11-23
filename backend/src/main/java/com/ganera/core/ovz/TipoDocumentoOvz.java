package com.ganera.core.ovz;

/**
 * Tipo de justificante OVZ guardado (escalon 2). El de «Baja Animal» (plan 2026-11-17) y el de «Alta Bovino»
 * (plan 2026-11-23, el DI del alta por nacimiento). Se guarda como {@code name()} en {@code documento_ovz.tipo}.
 */
public enum TipoDocumentoOvz {
    BAJA_ANIMAL,
    ALTA_BOVINO
}
