import type { ActualizacionTramite } from "./api";
import { esDescartado } from "./etiquetas";
import type { EstadoTramite, TramiteDetalle } from "./types";

/**
 * Lógica pura del formulario de revisión (Task 9a): qué se puede editar, cuándo hay cambios sin
 * guardar y qué PATCH se envía. Sin React, sin red. Las reglas de negocio (qué crotal es válido,
 * si el trámite es aprobable…) NO están aquí: las decide el backend y responde con un `motivo`.
 */

/** Texto exacto de la decisión 24 (H6): con cambios sin guardar, Aprobar y Rechazar se desactivan. */
export const AVISO_CAMBIOS_SIN_GUARDAR = "Guarda antes de aprobar";

/** Campos OVZ editables en el formulario (fechas ISO `yyyy-MM-dd` o texto). */
export type CampoOvz =
  | "fechaHecho"
  | "fechaNacimiento"
  | "fechaIdentificacion"
  | "sexo"
  | "crotalMadreIndicado"
  | "numeroMer"
  | "destinoRega"
  | "destinoTexto"
  | "consignatario"
  | "aptitudMovimiento";

const CAMPOS_OVZ: readonly CampoOvz[] = [
  "fechaHecho",
  "fechaNacimiento",
  "fechaIdentificacion",
  "sexo",
  "crotalMadreIndicado",
  "numeroMer",
  "destinoRega",
  "destinoTexto",
  "consignatario",
  "aptitudMovimiento",
] as const;

const CAMPOS_FECHA: ReadonlySet<CampoOvz> = new Set([
  "fechaHecho",
  "fechaNacimiento",
  "fechaIdentificacion",
]);

/** Lo que el usuario edita. `crotales` es la lista tal cual la escribe (con vacíos y espacios). */
export interface FormularioRevision {
  explotacionId: number | null;
  /** Normalmente un `TipoTramite`, pero puede ser un tipo que el frontend aún no conoce (prompt B)
   * si viene así del backend: se conserva sin tocar. */
  tipoTramite: string | null;
  crotales: string[];
  fechaHecho: string | null;
  fechaNacimiento: string | null;
  fechaIdentificacion: string | null;
  sexo: string | null;
  crotalMadreIndicado: string | null;
  numeroMer: string | null;
  destinoRega: string | null;
  destinoTexto: string | null;
  consignatario: string | null;
  aptitudMovimiento: string | null;
}

/** Decisión 7: solo un trámite en PENDIENTE_REVISION se edita, aprueba o rechaza. */
export function esEditable(estado: EstadoTramite): boolean {
  return estado === "PENDIENTE_REVISION";
}

/** El formulario parte de lo guardado. De cada crotal se toma lo INDICADO (`crotalIndicado`), no
 * el completo resuelto: el backend vuelve a resolver desde lo escrito. */
export function formularioDesdeDetalle(detalle: TramiteDetalle): FormularioRevision {
  return {
    explotacionId: detalle.explotacionId,
    tipoTramite: detalle.tipoTramite,
    crotales: detalle.crotales.map((crotal) => crotal.crotalIndicado),
    fechaHecho: detalle.fechaHecho,
    fechaNacimiento: detalle.fechaNacimiento,
    fechaIdentificacion: detalle.fechaIdentificacion,
    sexo: detalle.sexo,
    crotalMadreIndicado: detalle.crotalMadreIndicado,
    numeroMer: detalle.numeroMer,
    destinoRega: detalle.destinoRega,
    destinoTexto: detalle.destinoTexto,
    consignatario: detalle.consignatario,
    aptitudMovimiento: detalle.aptitudMovimiento,
  };
}

/** Recorta espacios y quita las entradas vacías, en orden. No normaliza, no clasifica, no valida y
 * no quita duplicados: todo eso es del backend (400 con motivo si algo no vale). */
export function crotalesParaEnviar(crotales: readonly string[]): string[] {
  return crotales.map((crotal) => crotal.trim()).filter((crotal) => crotal !== "");
}

function mismasListas(a: readonly string[], b: readonly string[]): boolean {
  return a.length === b.length && a.every((valor, indice) => valor === b[indice]);
}

/** Texto: null y "" se tratan iguales (vacío). */
function mismoTexto(a: string | null, b: string | null): boolean {
  return (a ?? "") === (b ?? "");
}

/**
 * Fechas: vaciar en pantalla no cuenta como cambio (el backend no permite limpiar fechas con PATCH).
 * Solo una fecha no vacía distinta de la guardada es cambio.
 */
function mismaFecha(form: string | null, guardada: string | null): boolean {
  const f = form && form.trim() !== "" ? form.trim() : null;
  if (f === null) return true;
  return f === guardada;
}

/**
 * PATCH para pasar de `detalle` a `formulario`: la `version` del detalle y solo lo que cambió
 * (`crotales` completo si la lista cambió). `null` si no hay nada que enviar.
 *
 * Un null en explotación o tipo nunca se envía: para el backend null es "no cambiar" (H5), así que
 * no hay forma de vaciarlos. El hook tampoco deja ponerlos a null una vez tienen valor.
 * Textos OVZ: cadena vacía limpia. Fechas OVZ: solo si hay valor nuevo (no se pueden vaciar).
 */
export function construirPatch(
  detalle: TramiteDetalle,
  formulario: FormularioRevision,
): ActualizacionTramite | null {
  const patch: ActualizacionTramite = { version: detalle.version };
  let hayCambios = false;

  if (formulario.explotacionId !== null && formulario.explotacionId !== detalle.explotacionId) {
    patch.explotacionId = formulario.explotacionId;
    hayCambios = true;
  }
  if (formulario.tipoTramite !== null && formulario.tipoTramite !== detalle.tipoTramite) {
    patch.tipoTramite = formulario.tipoTramite;
    hayCambios = true;
  }
  const crotales = crotalesParaEnviar(formulario.crotales);
  const guardados = detalle.crotales.map((crotal) => crotal.crotalIndicado);
  if (!mismasListas(crotales, guardados)) {
    patch.crotales = crotales;
    hayCambios = true;
  }

  for (const campo of CAMPOS_OVZ) {
    if (CAMPOS_FECHA.has(campo)) {
      const form = formulario[campo];
      const f = form && form.trim() !== "" ? form.trim() : null;
      if (f !== null && f !== detalle[campo]) {
        patch[campo] = f;
        hayCambios = true;
      }
      continue;
    }
    if (!mismoTexto(formulario[campo], detalle[campo])) {
      // Blanco limpia en el backend; mandamos "" si el formulario está vacío.
      patch[campo] = formulario[campo] ?? "";
      hayCambios = true;
    }
  }

  return hayCambios ? patch : null;
}

/** Hay cambios sin guardar: exactamente cuando habría algo que enviar en el PATCH. */
export function estaSucio(detalle: TramiteDetalle, formulario: FormularioRevision): boolean {
  return construirPatch(detalle, formulario) !== null;
}

/** Compara un campo OVZ del formulario con lo guardado (para badges «Sin guardar»). */
export function campoOvzSucio(
  detalle: TramiteDetalle,
  formulario: FormularioRevision,
  campo: CampoOvz,
): boolean {
  if (CAMPOS_FECHA.has(campo)) {
    return !mismaFecha(formulario[campo], detalle[campo]);
  }
  return !mismoTexto(formulario[campo], detalle[campo]);
}

/** SIN_TRAMITE: «Descartar» se ofrece si el BACKEND dice `descartable` (D3: pendiente, `SIN_TRAMITE`,
 * sin tipo ni crotales) y no hay cambios sin guardar (descartaría lo guardado, no lo de pantalla).
 * El frontend nunca recalcula la regla por su cuenta (p. ej. mirando `estadoExtraccion`). */
export function puedeDescartar(detalle: TramiteDetalle, formulario: FormularioRevision): boolean {
  return esEditable(detalle.estado) && detalle.descartable === true && !estaSucio(detalle, formulario);
}

/** SIN_TRAMITE: «Deshacer» solo en un descartado (`RECHAZADO` + `SIN_TRAMITE`), nunca en un rechazo
 * normal (que no tiene vuelta atrás). */
export function puedeDeshacerDescarte(detalle: TramiteDetalle): boolean {
  return esDescartado(detalle);
}
