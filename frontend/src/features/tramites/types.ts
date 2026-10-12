import type { EstadoTramite, ResolucionCrotal } from "./etiquetas";

// Estados, tipos, resoluciones y sus etiquetas viven en etiquetas.ts (una sola fuente por dominio).
// badgeVarianteDeEstado se reexporta aquí porque DESIGN.md la cita desde este fichero.
export { badgeVarianteDeEstado } from "./etiquetas";
export type { EstadoTramite, ResolucionCrotal, TipoTramite } from "./etiquetas";

/** Crotal de un trámite (`TramiteCrotalResponse`). `crotal` es el completo si `EN_INVENTARIO` y,
 * si no, igual a `crotalIndicado`; `animalId` es null salvo en `EN_INVENTARIO`. */
export interface TramiteCrotal {
  crotalIndicado: string;
  crotal: string;
  animalId: number | null;
  enInventario: boolean;
  resolucion: ResolucionCrotal;
  /** Si lo ESCRITO (`crotalIndicado`) es un crotal completo, no a qué se resolvió: `1234`
   * `EN_INVENTARIO` es `false`. Opcional (D5b): si falta, el badge se queda neutro. */
  completo?: boolean;
}

// tipoTramite es string (no TipoTramite): el backend puede traer tipos que el frontend aún no
// conoce (prompt B). Se etiqueta con etiquetaTipoTramite, que muestra tal cual lo desconocido.
export interface Tramite {
  id: number;
  explotacionId: number | null;
  /** `TramiteResponse` (mini-prompt tras A2): null si el trámite no tiene explotación. */
  explotacionCodigoRega: string | null;
  explotacionNombre: string | null;
  tipoTramite: string | null;
  estado: EstadoTramite;
  motivoError: string | null;
  crotales: TramiteCrotal[];
  version: number;
  /** B1: `"WHATSAPP"`, o null en trámites anteriores a B1. Se etiqueta con `etiquetaOrigen`. */
  origen: string | null;
  /** B1: `PENDIENTE`, `COMPLETADA`, `FALLIDA`, `SIN_TEXTO` (y `SIN_TRAMITE` desde 2026-10-12), o null
   * en trámites anteriores a B1.
   * Es string: un valor que el frontend no conozca no muestra aviso (`avisosExtraccion`). */
  estadoExtraccion: string | null;
  /** B1: identificadores que la IA devolvió y no parecían crotales; 0 si no hubo. */
  crotalesDescartados: number;
  /** Hecho en OVZ: cuándo marcó el gestor que ya lo había metido él en OVZ (ISO-8601 UTC con `Z`,
   * hasta microsegundos), o null si no está marcado. Se enseña con `fechaHechoEnOvz` (Madrid). */
  hechoEnOvzEn: string | null;
  /** Hecho en OVZ: nombre del usuario que lo marcó (nunca email ni id), o null. */
  hechoEnOvzPorNombre: string | null;
  /** SIN_TRAMITE: código del motivo de un `RECHAZADO` (`"SIN_TRAMITE"` = descartado), o null en un
   * rechazo normal o en otro estado. Es string: un código desconocido se presenta como «Rechazado». */
  motivoRechazo: string | null;
  /** SIN_TRAMITE (D3): lo decide el backend (pendiente, `SIN_TRAMITE`, sin tipo ni crotales). El
   * frontend nunca recalcula la regla: solo enseña el aviso y «Descartar» si es `true`. */
  descartable: boolean;
}

/** SIN_TRAMITE (A6): una acción de descartar o deshacer el descarte, con quién y cuándo. */
export interface EventoDescarte {
  /** `DESCARTADO` o `DESCARTE_DESHECHO`; string: uno desconocido no se enseña. */
  tipo: string;
  /** `Usuario.nombre` (nunca email ni id). */
  usuarioNombre: string | null;
  /** ISO-8601 UTC con `Z`. Se enseña en hora de Madrid. */
  en: string | null;
}

export interface TramiteDetalle {
  id: number;
  tipoTramite: string | null;
  estado: EstadoTramite;
  motivoError: string | null;
  explotacionId: number | null;
  explotacionCodigoRega: string | null;
  explotacionNombre: string | null;
  /** Ficha OVZ, T2: titular de la explotación, para saber con qué cuenta entrar en OVZ. null sin
   * explotación. El backend nunca manda las credenciales de OVZ. */
  ganaderoNombre: string | null;
  ganaderoNif: string | null;
  mensajeOriginal: string | null;
  crotales: TramiteCrotal[];
  version: number;
  /** B1: `"WHATSAPP"`, o null en trámites anteriores a B1. Se etiqueta con `etiquetaOrigen`. */
  origen: string | null;
  /** B1: `PENDIENTE`, `COMPLETADA`, `FALLIDA`, `SIN_TEXTO` (y `SIN_TRAMITE` desde 2026-10-12), o null
   * en trámites anteriores a B1.
   * Es string: un valor que el frontend no conozca no muestra aviso (`avisosExtraccion`). */
  estadoExtraccion: string | null;
  /** B1: identificadores que la IA devolvió y no parecían crotales; 0 si no hubo. */
  crotalesDescartados: number;
  /** Hecho en OVZ: cuándo marcó el gestor que ya lo había metido él en OVZ (ISO-8601 UTC con `Z`,
   * hasta microsegundos), o null si no está marcado. Se enseña con `fechaHechoEnOvz` (Madrid). */
  hechoEnOvzEn: string | null;
  /** Hecho en OVZ: nombre del usuario que lo marcó (nunca email ni id), o null. */
  hechoEnOvzPorNombre: string | null;
  /** B2 reducido: la conversación de WhatsApp del trámite (destinatario, ventana de 24 h y
   * respuestas). null si el trámite no llegó por WhatsApp. La traen el GET del detalle y el PATCH. */
  whatsapp: WhatsappTramite | null;
  /** SIN_TRAMITE: como en `Tramite`. */
  motivoRechazo: string | null;
  descartable: boolean;
  /** SIN_TRAMITE (A6): descartes y deshacer, en orden cronológico; `[]` si no hay. Solo en el detalle. */
  historialDescarte: EventoDescarte[];
}

/** B2 reducido: una respuesta de la gestoría al contacto por WhatsApp (`RespuestaWhatsappResponse`).
 * Sin teléfono ni SID. `estado` es el efectivo y es string: uno desconocido se etiqueta tal cual
 * (`presentacionRespuestaWhatsapp`). `motivoError` es el texto de Ganera, nunca el de Twilio. */
export interface RespuestaWhatsapp {
  id: number;
  texto: string;
  estado: string;
  enviadoPorNombre: string | null;
  /** ISO-8601 UTC con `Z`. Se enseña en hora de Madrid. */
  enviadoEn: string;
  codigoError: number | null;
  motivoError: string | null;
}

/** B2 reducido: bloque `whatsapp` del detalle. El servidor decide si se puede responder
 * (`motivoNoDisponible`, con el mismo texto que su 409, o null); el frontend solo lo refleja. */
export interface WhatsappTramite {
  destinatarioNombre: string;
  /** Tal como llega (`+34******001`): nunca el teléfono entero. */
  telefonoEnmascarado: string;
  contactoActivo: boolean;
  /** Último mensaje del contacto + 24 h (ISO UTC), o null si no tiene mensajes. */
  ventanaAbiertaHasta: string | null;
  /** Nombre de la gestoría: firma del texto propuesto (el número de WhatsApp es compartido). */
  firmaGestoria: string;
  motivoNoDisponible: string | null;
  /** En orden cronológico. */
  respuestas: RespuestaWhatsapp[];
}
