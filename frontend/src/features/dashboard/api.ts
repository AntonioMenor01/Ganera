import { listarGanaderos } from "@/features/ganaderos/api";

/** Número de ganaderos de la gestoría (el `totalElements` de GET /ganaderos; `size=1`: no trae el
 * listado entero solo para contar). Lo usa Informes (el Inicio ya no muestra esta métrica). */
export function contarGanaderos(signal?: AbortSignal): Promise<number> {
  return listarGanaderos(0, 1, { signal }).then((pagina) => pagina.totalElements);
}
