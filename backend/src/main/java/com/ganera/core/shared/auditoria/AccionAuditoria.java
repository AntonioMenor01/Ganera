package com.ganera.core.shared.auditoria;

/**
 * Vocabulario cerrado de acciones auditadas: la tabla «Cobertura» del plan 2026-10-21 (columna
 * {@code auditoria.accion}, sin CHECK: la cierra este enum y el inventario de T6). Una accion nueva va aqui y en el
 * inventario, nunca como texto libre.
 */
public enum AccionAuditoria {
    // Revision de tramites (usuario)
    TRAMITE_EDITADO,
    TRAMITE_APROBADO,
    TRAMITE_CROTALES_RERESUELTOS,
    TRAMITE_RECHAZADO,
    TRAMITE_MARCADO_HECHO_OVZ,
    TRAMITE_DESMARCADO_HECHO_OVZ,
    TRAMITE_DESCARTADO,
    TRAMITE_DESCARTE_DESHECHO,

    // Respuestas por WhatsApp (usuario y callback de estado de Twilio)
    RESPUESTA_WHATSAPP_CREADA,
    RESPUESTA_WHATSAPP_RESULTADO,
    RESPUESTA_WHATSAPP_ESTADO_ACTUALIZADO,

    // Contactos (endpoints /contactos e importacion)
    CONTACTO_CREADO,
    CONTACTO_MODIFICADO,
    CONTACTO_DESACTIVADO,
    CONTACTO_REACTIVADO,
    CONTACTO_VINCULADO,
    CONTACTO_ROL_CAMBIADO,
    CONTACTO_DESVINCULADO,

    // Importacion de Excel (una por entidad creada o cambiada, y el resumen)
    GANADERO_CREADO,
    GANADERO_MODIFICADO,
    EXPLOTACION_CREADA,
    EXPLOTACION_MODIFICADA,
    ANIMAL_CREADO,
    ANIMAL_MODIFICADO,
    IMPORTACION_EXCEL,

    // Alta de gestorias (onboarding interno y registro publico) y suscripciones
    GESTORIA_CREADA,
    USUARIO_CREADO,
    SUSCRIPCION_CREADA,
    SUSCRIPCION_CANTIDAD_FIJADA,
    SUSCRIPCION_ESTADO_CAMBIADO,
    SUSCRIPCION_CANTIDAD_SINCRONIZADA,

    // Recepcion de WhatsApp (webhook de Twilio)
    MENSAJE_RECIBIDO,
    TRAMITE_CREADO,

    // Planificador de extraccion
    EXTRACCION_APLICADA,
    EXTRACCION_FALLIDA,
    EXTRACCION_INTENTO_FALLIDO,
    EXTRACCION_REINTENTOS_DETENIDOS,

    // Planificador de retencion (en bloque, por gestoria)
    RETENCION_MENSAJES_BORRADOS,
    RETENCION_TEXTO_VACIADO,
    RETENCION_RESPUESTAS_VACIADAS,

    // Login (A2)
    LOGIN_CORRECTO,
    LOGIN_FALLIDO,

    // Lecturas sensibles (D4) y accesos a recursos de otra gestoria (D3)
    LECTURA_TRAMITE,
    LECTURA_GANADERO,
    LECTURA_CONTACTOS,
    ACCESO_OTRA_GESTORIA
}
