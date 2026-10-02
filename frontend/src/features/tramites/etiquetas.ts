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
  /** PROVISIONAL (plan A2, decisión 21): neutro mientras Antonio decide cómo distinguir un crotal
   * incompleto. La API nunca devuelve `crotal` vacío, así que el frontend no puede saberlo, y no
   * debe clasificar crotales por su cuenta. Para pasar a ámbar basta con cambiar esta entrada. */
  NO_ENCONTRADO: { etiqueta: "No está en el inventario", variante: "outline" },
  SIN_EXPLOTACION: { etiqueta: "Falta la explotación", variante: "warning" },
} as const satisfies Record<string, Presentacion>;

export type ResolucionCrotal = keyof typeof RESOLUCIONES_CROTAL;

export function presentacionResolucion(resolucion: string): Presentacion {
  return presentacionDe(RESOLUCIONES_CROTAL, resolucion);
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
