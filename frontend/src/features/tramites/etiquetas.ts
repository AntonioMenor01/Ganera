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

function buscar<T>(tabla: Readonly<Record<string, T>>, clave: string): T | undefined {
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
// Tipo de trámite. ÚNICA constante con los tipos: cambiará en el prompt B.

export const TIPOS_TRAMITE = {
  ALTA: "Alta",
  BAJA: "Baja",
  CENSO: "Censo",
  MOVIMIENTO: "Movimiento",
  DEMORA: "Demora",
} as const satisfies Record<string, string>;

export type TipoTramite = keyof typeof TIPOS_TRAMITE;

export function etiquetaTipoTramite(tipo: string): string {
  return buscar<string>(TIPOS_TRAMITE, tipo) ?? textoCrudo(tipo);
}

// ---------------------------------------------------------------------------------------------
// Estado del trámite. Aún sin resolver → aviso; resuelto a favor → éxito; en contra → peligro.

export const ESTADOS_TRAMITE = {
  PENDIENTE_EXTRACCION: { etiqueta: "Pendiente de extracción", variante: "warning" },
  PENDIENTE_REVISION: { etiqueta: "Pendiente de revisión", variante: "warning" },
  APROBADO: { etiqueta: "Aprobado", variante: "success" },
  EN_PROCESO: { etiqueta: "En proceso", variante: "warning" },
  EJECUTADO_OVZ: { etiqueta: "Ejecutado en OVZ.net", variante: "success" },
  ERROR_OVZ: { etiqueta: "Error en OVZ.net", variante: "danger" },
  RECHAZADO: { etiqueta: "Rechazado", variante: "danger" },
} as const satisfies Record<string, Presentacion>;

export type EstadoTramite = keyof typeof ESTADOS_TRAMITE;

export function presentacionEstado(estado: string): Presentacion {
  return presentacionDe(ESTADOS_TRAMITE, estado);
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
 * un `crotalesDescartados` ausente, no entero o ≤ 0, tampoco. Nunca lanza. */
export function avisosExtraccion(tramite: {
  estado: string;
  estadoExtraccion?: string | null;
  crotalesDescartados?: number | null;
}): AvisosExtraccion {
  if (!ESTADOS_CON_AVISOS_EXTRACCION.has(tramite.estado)) return { extraccion: null, descartados: null };
  const { estadoExtraccion, crotalesDescartados } = tramite;
  const extraccion = estadoExtraccion
    ? (buscar<TextoExtraccion>(TEXTOS_EXTRACCION, estadoExtraccion) ?? null)
    : null;
  const descartados =
    typeof crotalesDescartados === "number" && Number.isInteger(crotalesDescartados) && crotalesDescartados > 0
      ? avisoDescartados(crotalesDescartados)
      : null;
  return { extraccion, descartados };
}
