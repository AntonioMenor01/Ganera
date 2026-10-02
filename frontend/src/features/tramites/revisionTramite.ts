import type { ActualizacionTramite } from "./api";
import type { EstadoTramite, TramiteDetalle } from "./types";

/**
 * Lógica pura del formulario de revisión (Task 9a): qué se puede editar, cuándo hay cambios sin
 * guardar y qué PATCH se envía. Sin React, sin red. Las reglas de negocio (qué crotal es válido,
 * si el trámite es aprobable…) NO están aquí: las decide el backend y responde con un `motivo`.
 */

/** Texto exacto de la decisión 24 (H6): con cambios sin guardar, Aprobar y Rechazar se desactivan. */
export const AVISO_CAMBIOS_SIN_GUARDAR = "Guarda antes de aprobar";

/** Lo que el usuario edita. `crotales` es la lista tal cual la escribe (con vacíos y espacios). */
export interface FormularioRevision {
  explotacionId: number | null;
  /** Normalmente un `TipoTramite`, pero puede ser un tipo que el frontend aún no conoce (prompt B)
   * si viene así del backend: se conserva sin tocar. */
  tipoTramite: string | null;
  crotales: string[];
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

/**
 * PATCH para pasar de `detalle` a `formulario`: la `version` del detalle y solo lo que cambió
 * (`crotales` completo si la lista cambió). `null` si no hay nada que enviar.
 *
 * Un null en explotación o tipo nunca se envía: para el backend null es "no cambiar" (H5), así que
 * no hay forma de vaciarlos. El hook tampoco deja ponerlos a null una vez tienen valor.
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
  return hayCambios ? patch : null;
}

/** Hay cambios sin guardar: exactamente cuando habría algo que enviar en el PATCH. */
export function estaSucio(detalle: TramiteDetalle, formulario: FormularioRevision): boolean {
  return construirPatch(detalle, formulario) !== null;
}
