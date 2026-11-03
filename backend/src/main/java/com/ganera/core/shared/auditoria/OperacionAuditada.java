package com.ganera.core.shared.auditoria;

/**
 * Codigo de la operacion que intentaba hacer un usuario cuando se le negó el acceso a un recurso de OTRA gestoria
 * (fila {@code ACCESO_OTRA_GESTORIA}, clave {@code OPERACION}, plan 2026-10-21, T5/D3). Es un enum (no texto libre)
 * y se guarda como su {@code name()} a traves de {@link DatosAuditoria}, igual que {@link OrigenAuditoria}: un
 * vocabulario cerrado, sin ninguna sobrecarga de {@code String} en {@code DatosAuditoria}.
 *
 * <p>Cubre todos los endpoints con un id de ruta (o un id de otra gestoria en el cuerpo: {@code explotacionId} del
 * PATCH de tramites y del vinculo de contactos). La respuesta al cliente NO cambia (sigue {@code 404} sin cuerpo);
 * esta operacion solo describe lo que se intentaba, para la fila de auditoria.
 */
public enum OperacionAuditada {
    // Tramite (ruta)
    VER_TRAMITE,
    EDITAR_TRAMITE,
    APROBAR,
    RECHAZAR,
    MARCAR_HECHO_OVZ,
    DESMARCAR_HECHO_OVZ,
    DESCARTAR,
    DESHACER_DESCARTE,
    RESPONDER_WHATSAPP,

    // Ganadero
    VER_GANADERO,

    // Explotacion
    VER_EXPLOTACION,
    VER_ANIMALES,

    // Contacto
    EDITAR_CONTACTO,
    DESACTIVAR_CONTACTO,
    REACTIVAR_CONTACTO,
    VINCULAR_EXPLOTACION,
    DESVINCULAR_EXPLOTACION
}
