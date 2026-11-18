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
    LECTURA_EXPLOTACION,
    LECTURA_DOCUMENTO_OVZ,
    ACCESO_OTRA_GESTORIA,

    // Descifrado bajo demanda de la contrasena OVZ (M2), en la via de envio a OVZ. Nunca guarda la contrasena.
    CREDENCIAL_OVZ_DESCIFRADA,

    // Credenciales OVZ por ganadero (escalon 2, T1). Nunca guardan el usuario ni la contrasena: solo los
    // codigos de los campos cambiados.
    CREDENCIAL_OVZ_GUARDADA,
    CREDENCIAL_OVZ_MODIFICADA,
    CREDENCIAL_OVZ_BORRADA,

    // Preferencias OVZ de la gestoria (plan 2026-11-10)
    PREFERENCIA_OVZ_GUARDADA,

    // Alta de un numero de WhatsApp DEDICADO por el endpoint interno (plan 2026-11-16, fase 1). Nunca guarda el telefono.
    NUMERO_WHATSAPP_REGISTRADO,

    // Cola de presentacion automatica de la baja en OVZ (escalon 2, T2). Nunca guardan datos personales (ni crotal
    // ni credenciales): solo estados y codigos. OVZ_INCIERTO_CONFIRMADO es el reencolado de un ERROR INCIERTO tras
    // la confirmacion explicita del gestor de que la baja NO se presento (D11).
    OVZ_TRABAJO_ENCOLADO,
    OVZ_TRABAJO_REENCOLADO,
    OVZ_INCIERTO_CONFIRMADO,

    // Justificante de la baja guardado en la ficha del ganadero (escalon 2, T3). Nunca guarda el contenido ni datos
    // personales: solo el tamaño en bytes (Clave.TAMANO). El entidadId es el id del documento_ovz.
    OVZ_DOCUMENTO_GUARDADO,

    // Worker de OVZ (escalon 2, T4). Solo estados y codigos en antes/despues: el motivo del error va al trabajo,
    // NUNCA a la auditoria (ni crotal, ni REGA, ni credenciales). OVZ_BAJA_ENSAYO: el ensayo llego hasta antes de
    // «Aceptar» sin presentar nada.
    OVZ_BAJA_PRESENTADA,
    OVZ_BAJA_ERROR,
    OVZ_BAJA_ENSAYO
}
