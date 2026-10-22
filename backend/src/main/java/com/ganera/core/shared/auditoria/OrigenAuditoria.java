package com.ganera.core.shared.auditoria;

/**
 * Codigo del origen de una fila de auditoria cuando la misma accion puede llegar por varias vias (clave
 * {@code ORIGEN}, plan 2026-10-21). {@code IMPORTACION}: una entidad creada o enlazada por el importador de
 * Excel, para distinguirla de la misma accion hecha desde un endpoint. (T4 anade {@code REGISTRO} para el
 * alta publica.)
 */
public enum OrigenAuditoria {
    IMPORTACION
}
