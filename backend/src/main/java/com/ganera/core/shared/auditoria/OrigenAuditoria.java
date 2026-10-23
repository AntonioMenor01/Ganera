package com.ganera.core.shared.auditoria;

/**
 * Codigo del origen de una fila de auditoria cuando la misma accion puede llegar por varias vias (clave
 * {@code ORIGEN}, plan 2026-10-21). {@code IMPORTACION}: una entidad creada o enlazada por el importador de
 * Excel, para distinguirla de la misma accion hecha desde un endpoint. {@code REGISTRO}: un alta por el
 * registro publico ({@code POST /gestorias/registro}), para distinguirla del onboarding interno.
 */
public enum OrigenAuditoria {
    IMPORTACION,
    REGISTRO
}
