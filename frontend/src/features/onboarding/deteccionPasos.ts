import { listarGanaderos } from "@/features/ganaderos/api";
import { listarExplotaciones } from "@/features/explotaciones/api";
import { obtenerPreferenciasOvz } from "@/features/configuracion/preferenciasOvzApi";
import { ovzConfigurado, type DeteccionPaso } from "./primerosPasos";

/** Qué pasos auto están hechos, según datos reales (endpoints ya existentes, filtrados por gestoría
 * en el backend). Cada comprobación es independiente: si una falla, ese paso queda "no detectado"
 * (no se marca hecho a ciegas) y las demás siguen. Nunca lanza. */
export type PasosDetectados = Record<DeteccionPaso, boolean>;

export const SIN_DETECTAR: PasosDetectados = { ovz: false, ganadero: false, inventario: false };

export async function detectarPasosAuto(signal?: AbortSignal): Promise<PasosDetectados> {
  const [prefs, ganaderos, explotaciones] = await Promise.allSettled([
    obtenerPreferenciasOvz(),
    listarGanaderos(0, 1, { signal }),
    listarExplotaciones(0, 1, { signal }),
  ]);
  return {
    ovz: prefs.status === "fulfilled" && ovzConfigurado(prefs.value),
    ganadero: ganaderos.status === "fulfilled" && ganaderos.value.totalElements > 0,
    inventario: explotaciones.status === "fulfilled" && explotaciones.value.totalElements > 0,
  };
}
