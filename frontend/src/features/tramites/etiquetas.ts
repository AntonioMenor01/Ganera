/** Única fuente de etiquetas y variantes de badge por dominio (plan A2, decisión 6).
 *
 * Los valores llegan del backend como texto: si llega uno que el frontend aún no conoce (p. ej.
 * un tipo nuevo del prompt B antes de actualizar esto), se muestra tal cual con un badge neutro.
 * Nunca rompe ni deja el texto en blanco. */

/** Variantes de `Badge` que se usan para comunicar estado. Pares exactos de DESIGN.md (success,
 * warning, danger) más `outline`, el neutro. */
export type VarianteBadge = "success" | "warning" | "danger" | "outline";

export interface Presentacion {
  etiqueta: string;
  variante: VarianteBadge;
}

const VARIANTE_DESCONOCIDA: VarianteBadge = "outline";
const TEXTO_VACIO = "—";

/** Busca `clave` solo entre las propias de `tabla` (exportada para fichaOvz.ts). */
export function buscar<T>(tabla: Readonly<Record<string, T>>, clave: string): T | undefined {
  // hasOwnProperty evita que "toString" o "constructor" se tomen por valores conocidos.
  return Object.prototype.hasOwnProperty.call(tabla, clave) ? tabla[clave] : undefined;
}

function textoCrudo(valor: string): string {
  // Los tipos no admiten null, pero un JSON inesperado podria traerlo: nunca romper la pantalla.
  return (valor ?? "").trim() === "" ? TEXTO_VACIO : valor;
}

function presentacionDe(tabla: Readonly<Record<string, Presentacion>>, valor: string): Presentacion {
  return buscar(tabla, valor) ?? { etiqueta: textoCrudo(valor), variante: VARIANTE_DESCONOCIDA };
}

// ---------------------------------------------------------------------------------------------
// Tipo de trámite. ÚNICA constante con los tipos. Cada uno es un formulario de OVZ, en el orden
// del enum TipoTramite del backend (ficha OVZ, T1; docs/referencias/ovz-tramites-bovino.md).

export const TIPOS_TRAMITE = {
  ALTA_NACIMIENTO: "Alta por nacimiento",
  BAJA_MUERTE: "Baja por muerte",
  SOLICITUD_MOVIMIENTO: "Solicitud de movimiento",
  CONFIRMACION_MOVIMIENTO: "Confirmación de entrada",
  DECLARACION_CENSO: "Declaración de censo",
  DEMORA_CROTALIZACION: "Demora de crotalización",
} as const satisfies Record<string, string>;

export type TipoTramite = keyof typeof TIPOS_TRAMITE;

export function etiquetaTipoTramite(tipo: string): string {
  return buscar<string>(TIPOS_TRAMITE, tipo) ?? textoCrudo(tipo);
}

/** Icono de cada tipo (diseño de Álvaro, fase 1): nombres kebab de lucide. El componente que los
 * pinta vive en `IconoTramite.tsx`; aquí no se importa React. */
export type IconoTipo = "log-in" | "log-out" | "truck" | "arrow-down-to-line" | "list-ordered" | "tag";

const ICONOS_TIPO_TRAMITE = {
  ALTA_NACIMIENTO: "log-in",
  BAJA_MUERTE: "log-out",
  SOLICITUD_MOVIMIENTO: "truck",
  CONFIRMACION_MOVIMIENTO: "arrow-down-to-line",
  DECLARACION_CENSO: "list-ordered",
  DEMORA_CROTALIZACION: "tag",
} as const satisfies Record<TipoTramite, IconoTipo>;

/** Sin tipo, vacío o desconocido → `null` (sin icono). */
export function iconoTipoTramite(tipo: string | null | undefined): IconoTipo | null {
  return tipo ? (buscar<IconoTipo>(ICONOS_TIPO_TRAMITE, tipo) ?? null) : null;
}

// ---------------------------------------------------------------------------------------------
// Estado del trámite. Aún sin resolver → aviso; resuelto a favor → éxito; en contra → peligro.
// Hecho en OVZ (2026-10-07): APROBADO solo es «datos revisados» y aún falta que el gestor lo meta
// en OVZ, así que es un aviso; HECHO_EN_OVZ (el gestor dice que ya lo hizo él) es el éxito.
// EN_PROCESO/EJECUTADO_OVZ/ERROR_OVZ son de la automatización (3c), hoy no se dan: siguen aquí para
// etiquetarlos si llegan, pero no salen en el filtro de la cola.

export const ESTADOS_TRAMITE = {
  PENDIENTE_EXTRACCION: { etiqueta: "Pendiente de extracción", variante: "warning" },
  PENDIENTE_REVISION: { etiqueta: "Pendiente de revisión", variante: "warning" },
  APROBADO: { etiqueta: "Aprobado · falta OVZ", variante: "warning" },
  HECHO_EN_OVZ: { etiqueta: "Hecho en OVZ", variante: "success" },
  EN_PROCESO: { etiqueta: "En proceso", variante: "warning" },
  EJECUTADO_OVZ: { etiqueta: "Ejecutado en OVZ.net", variante: "success" },
  ERROR_OVZ: { etiqueta: "Error en OVZ.net", variante: "danger" },
  RECHAZADO: { etiqueta: "Rechazado", variante: "danger" },
} as const satisfies Record<string, Presentacion>;

export type EstadoTramite = keyof typeof ESTADOS_TRAMITE;

export function presentacionEstado(estado: string): Presentacion {
  return presentacionDe(ESTADOS_TRAMITE, estado);
}

/** Motivos de rechazo (SIN_TRAMITE, 2026-10-12): el backend guarda un CÓDIGO, nunca texto; el texto
 * sale de aquí. Un código desconocido se presenta como el `RECHAZADO` de siempre. */
export const MOTIVOS_RECHAZO = {
  /** «Descartar» del modal: un mensaje sin trámite. Neutro: no es un error ni un rechazo del gestor. */
  SIN_TRAMITE: { etiqueta: "Descartado", variante: "outline" },
} as const satisfies Record<string, Presentacion>;

/** Lo que hace falta para presentar el estado de un trámite (lista, detalle o ficha). */
export interface EstadoConMotivo {
  estado: string;
  motivoRechazo?: string | null;
}

/** Descartado como mensaje sin trámite: `RECHAZADO` + `motivoRechazo = SIN_TRAMITE`. */
export function esDescartado(tramite: EstadoConMotivo): boolean {
  return tramite.estado === "RECHAZADO" && tramite.motivoRechazo === "SIN_TRAMITE";
}

/** Presentación del estado teniendo en cuenta el motivo de rechazo. ÚNICA fuente para el badge de la
 * cola, el modal y la ficha: un descartado se ve «Descartado» en los tres. */
export function presentacionEstadoTramite(tramite: EstadoConMotivo): Presentacion {
  if (tramite.estado === "RECHAZADO" && tramite.motivoRechazo) {
    const motivo = buscar<Presentacion>(MOTIVOS_RECHAZO, tramite.motivoRechazo);
    if (motivo) return motivo;
  }
  return presentacionEstado(tramite.estado);
}

/** Icono del badge de estado (diseño de Álvaro, fase 1): nombres kebab de lucide. */
export type IconoEstado = "sparkles" | "eye" | "check" | "circle-check" | "x" | "archive";

/** Los reservados de la automatización (EN_PROCESO, EJECUTADO_OVZ, ERROR_OVZ) no tienen icono. */
const ICONOS_ESTADO_TRAMITE = {
  PENDIENTE_EXTRACCION: "sparkles",
  PENDIENTE_REVISION: "eye",
  APROBADO: "check",
  HECHO_EN_OVZ: "circle-check",
  RECHAZADO: "x",
} as const satisfies Partial<Record<EstadoTramite, IconoEstado>>;

const ICONO_DESCARTADO: IconoEstado = "archive";

/** Icono del estado, con el descartado aparte (misma regla que `presentacionEstadoTramite`). Reservado
 * o desconocido → `null` (sin icono). */
export function iconoEstadoTramite(tramite: EstadoConMotivo): IconoEstado | null {
  if (esDescartado(tramite)) return ICONO_DESCARTADO;
  return buscar<IconoEstado>(ICONOS_ESTADO_TRAMITE, tramite.estado) ?? null;
}

/** Variante de `Badge` para un estado (DESIGN.md la nombra). Desconocido → `outline`. */
export function badgeVarianteDeEstado(estado: string): VarianteBadge {
  return presentacionEstado(estado).variante;
}

// ---------------------------------------------------------------------------------------------
// Resolución de un crotal contra el inventario. La decide el backend; aquí solo se etiqueta.

export const RESOLUCIONES_CROTAL = {
  EN_INVENTARIO: { etiqueta: "En inventario", variante: "success" },
  AMBIGUO: { etiqueta: "Varios animales coinciden", variante: "warning" },
  /** Neutro: un crotal completo que no está en el inventario puede aprobarse (un animal que
   * entra). Si lo escrito era incompleto, `presentacionCrotal` lo pasa a ámbar (decisión 21 de A2,
   * cerrada con `completo` en el mini-prompt tras A2). */
  NO_ENCONTRADO: { etiqueta: "No está en el inventario", variante: "outline" },
  SIN_EXPLOTACION: { etiqueta: "Falta la explotación", variante: "warning" },
} as const satisfies Record<string, Presentacion>;

export type ResolucionCrotal = keyof typeof RESOLUCIONES_CROTAL;

export function presentacionResolucion(resolucion: string): Presentacion {
  return presentacionDe(RESOLUCIONES_CROTAL, resolucion);
}

/** `NO_ENCONTRADO` de un crotal escrito incompleto: el backend no dejará aprobarlo (OVZ.net
 * necesita el crotal completo), así que es un aviso, no información neutra. */
const NO_ENCONTRADO_INCOMPLETO: Presentacion = {
  etiqueta: "No está en el inventario · incompleto",
  variante: "warning",
};

/** Presentación de un crotal de trámite: su resolución, matizada por `completo` (que describe lo
 * ESCRITO, `crotalIndicado`, y lo calcula el backend). Solo `NO_ENCONTRADO` + `completo === false`
 * cambia; `completo` ausente (D5b) o cualquier otra resolución se pintan como siempre. El frontend
 * no clasifica crotales por su cuenta: nunca mira la longitud ni el formato del texto. */
export function presentacionCrotal(crotal: { resolucion: string; completo?: boolean }): Presentacion {
  if (crotal.resolucion === "NO_ENCONTRADO" && crotal.completo === false) return NO_ENCONTRADO_INCOMPLETO;
  return presentacionResolucion(crotal.resolucion);
}

// ---------------------------------------------------------------------------------------------
// Rol de un contacto en una explotación.

export const ROLES_CONTACTO = {
  TITULAR: "Titular",
  EMPLEADO: "Empleado",
} as const satisfies Record<string, string>;

export type RolContacto = keyof typeof ROLES_CONTACTO;

export function etiquetaRolContacto(rol: string): string {
  return buscar<string>(ROLES_CONTACTO, rol) ?? textoCrudo(rol);
}

/** Variante del badge de rol (DESIGN.md, par exacto): Titular = éxito, Empleado = neutro. La
 * etiqueta sale de ROLES_CONTACTO; aquí solo se decide el color. */
const VARIANTES_ROL_CONTACTO = {
  TITULAR: "success",
  EMPLEADO: "outline",
} as const satisfies Record<RolContacto, VarianteBadge>;

export function presentacionRolContacto(rol: string): Presentacion {
  return {
    etiqueta: etiquetaRolContacto(rol),
    variante: buscar<VarianteBadge>(VARIANTES_ROL_CONTACTO, rol) ?? VARIANTE_DESCONOCIDA,
  };
}

// ---------------------------------------------------------------------------------------------
// Origen del trámite (B1, D7). Solo WhatsApp por ahora; null (anterior a B1) o desconocido = sin
// indicador. No es un badge: los badges son estados.

export const ORIGENES_TRAMITE = {
  WHATSAPP: "Recibido por WhatsApp",
} as const satisfies Record<string, string>;

/** Texto del indicador de origen (oculto y `title`), o null si no hay indicador que pintar. */
export function etiquetaOrigen(origen: string | null | undefined): string | null {
  return origen ? (buscar<string>(ORIGENES_TRAMITE, origen) ?? null) : null;
}

// ---------------------------------------------------------------------------------------------
// Extracción por IA de un trámite de WhatsApp (B1: D7, D8, A3 y brief de T4). Son hechos de los
// datos, no estados del trámite: van como línea de texto bajo el badge (cola) y como aviso fijo en
// el modal, nunca como badge. `COMPLETADA` no da aviso.

/** `aviso` = el par ámbar de aviso (trabajo pendiente, nunca rojo); `neutro` = Gris Texto. */
export type TonoAvisoExtraccion = "aviso" | "neutro";

export interface TextoExtraccion {
  /** Línea corta de la cola, bajo el badge de Estado. */
  cola: string;
  /** Aviso del modal de revisión. */
  modal: string;
  tono: TonoAvisoExtraccion;
}

export const TEXTOS_EXTRACCION = {
  PENDIENTE: { cola: "Extracción en curso", modal: "Extracción en curso", tono: "neutro" },
  FALLIDA: {
    cola: "No se ha podido extraer",
    modal: "No se ha podido extraer automáticamente. Revisa el mensaje original.",
    tono: "aviso",
  },
  SIN_TEXTO: {
    cola: "Mensaje sin texto",
    modal: "El mensaje no tiene texto; puede traer una foto o un audio, que todavía no se procesan.",
    tono: "aviso",
  },
  /** SIN_TRAMITE (2026-10-12): la IA cree que no pide ningún trámite. Neutro: no es un fallo. Solo se
   * enseña si el backend dice que es `descartable` (ver `avisosExtraccion`). */
  SIN_TRAMITE: {
    cola: "Parece un mensaje sin trámite",
    modal:
      "Parece un mensaje sin trámite: un saludo, un agradecimiento o una pregunta. Si hay que contestar, responde antes de descartar.",
    tono: "neutro",
  },
} as const satisfies Record<string, TextoExtraccion>;

export interface AvisoDescartados {
  cola: string;
  modal: string;
}

export interface AvisosExtraccion {
  extraccion: TextoExtraccion | null;
  descartados: AvisoDescartados | null;
}

/** Los avisos solo se ven mientras el trámite está pendiente: aprobado o rechazado, desaparecen
 * (aunque `FALLIDA` siga guardado). */
const ESTADOS_CON_AVISOS_EXTRACCION: ReadonlySet<string> = new Set<EstadoTramite>([
  "PENDIENTE_EXTRACCION",
  "PENDIENTE_REVISION",
]);

function avisoDescartados(n: number): AvisoDescartados {
  return n === 1
    ? { cola: "1 descartado", modal: "Hay 1 identificador que no parece un crotal; revisa el mensaje." }
    : {
        cola: `${n} descartados`,
        modal: `Hay ${n} identificadores que no parecen crotales; revisa el mensaje.`,
      };
}

/** Qué avisos de extracción tocan para un trámite. Un `estadoExtraccion` desconocido no da aviso;
 * un `crotalesDescartados` ausente, no entero o ≤ 0, tampoco. `SIN_TRAMITE` solo da aviso si el
 * backend lo marca `descartable` (D3: si el gestor ya le puso tipo o crotales, deja de serlo); el
 * frontend no recalcula esa regla. Nunca lanza. */
export function avisosExtraccion(tramite: {
  estado: string;
  estadoExtraccion?: string | null;
  crotalesDescartados?: number | null;
  descartable?: boolean | null;
}): AvisosExtraccion {
  if (!ESTADOS_CON_AVISOS_EXTRACCION.has(tramite.estado)) return { extraccion: null, descartados: null };
  const { estadoExtraccion, crotalesDescartados } = tramite;
  const sinTramiteNoDescartable = estadoExtraccion === "SIN_TRAMITE" && tramite.descartable !== true;
  const extraccion =
    estadoExtraccion && !sinTramiteNoDescartable
      ? (buscar<TextoExtraccion>(TEXTOS_EXTRACCION, estadoExtraccion) ?? null)
      : null;
  const descartados =
    typeof crotalesDescartados === "number" && Number.isInteger(crotalesDescartados) && crotalesDescartados > 0
      ? avisoDescartados(crotalesDescartados)
      : null;
  return { extraccion, descartados };
}

// ---------------------------------------------------------------------------------------------
// Estado de una respuesta de la gestoría por WhatsApp (B2 reducido). Vocabulario sin «enviado» ni
// derivados (la lista de palabras prohibidas del test de honestidad aplica a todo el modal):
// `ENVIADO` solo significa que WhatsApp (Twilio) lo aceptó. Desconocido → tal cual, neutro.

export const ESTADOS_RESPUESTA_WHATSAPP = {
  ENVIANDO: { etiqueta: "Enviando…", variante: "outline" },
  ENVIADO: { etiqueta: "Aceptado por WhatsApp", variante: "outline" },
  ENTREGADO: { etiqueta: "Entregado", variante: "success" },
  LEIDO: { etiqueta: "Leído", variante: "success" },
  FALLIDO: { etiqueta: "No ha llegado", variante: "danger" },
  SIN_CONFIRMAR: { etiqueta: "Sin confirmar", variante: "warning" },
} as const satisfies Record<string, Presentacion>;

export type EstadoRespuestaWhatsapp = keyof typeof ESTADOS_RESPUESTA_WHATSAPP;

export function presentacionRespuestaWhatsapp(estado: string): Presentacion {
  return presentacionDe(ESTADOS_RESPUESTA_WHATSAPP, estado);
}
