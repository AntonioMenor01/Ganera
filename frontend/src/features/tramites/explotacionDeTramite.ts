import type { CargaTodasLasExplotaciones } from "@/features/explotaciones/useTodasLasExplotaciones";

/** Qué enseñar en la columna Explotación de la cola (H7 / decisión 22). Nunca el id crudo. */
export type PresentacionExplotacion =
  | { tipo: "sin-asignar" }
  | { tipo: "cargando" }
  | { tipo: "no-disponible" }
  | { tipo: "no-encontrada" }
  | { tipo: "encontrada"; codigoRega: string; nombre: string };

/**
 * Traduce el `explotacionId` de un trámite a su código REGA con la lista completa de
 * explotaciones. Sin explotación → "sin-asignar" (no necesita la lista). Un id que no está en la
 * lista completa → "no-encontrada".
 */
export function presentarExplotacion(
  explotacionId: number | null,
  carga: CargaTodasLasExplotaciones,
): PresentacionExplotacion {
  if (explotacionId === null || explotacionId === undefined) return { tipo: "sin-asignar" };
  if (carga.estado === "cargando") return { tipo: "cargando" };
  if (carga.estado === "error") return { tipo: "no-disponible" };
  const explotacion = carga.porId.get(explotacionId);
  if (!explotacion) return { tipo: "no-encontrada" };
  return { tipo: "encontrada", codigoRega: explotacion.codigoRega, nombre: explotacion.nombre };
}
