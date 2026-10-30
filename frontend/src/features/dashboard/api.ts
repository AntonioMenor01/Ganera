import { listarGanaderos } from "@/features/ganaderos/api";
import { listarTramites } from "@/features/tramites/api";
import type { EstadoTramite } from "@/features/tramites/types";
import type { Pagina } from "@/shared/api/types";
import type { Tramite } from "@/features/tramites/types";

/** La cola del inicio son los trámites que esperan revisión humana (los accionables), igual que la
 * cifra «por revisar» del Figma. PENDIENTE_EXTRACCION (la IA aún trabajando) no cuenta aquí. */
export const ESTADO_COLA: EstadoTramite = "PENDIENTE_REVISION";

/** Cuántos trámites pendientes se previsualizan en «Cola de trabajo». El contador del KPI sale del
 * `totalElements` de la misma respuesta, no de contar estas filas. */
export const TAMANIO_PREVIA_COLA = 5;

/**
 * Trámites pendientes de revisión: la primera página (hasta {@link TAMANIO_PREVIA_COLA}) para la
 * previa, y su `totalElements` para el KPI. Reutiliza GET /tramites, ya filtrado por el gestoriaId
 * del JWT en el backend (TramiteController), así que no expone datos de otra gestoría.
 */
export function cargarColaPendiente(): Promise<Pagina<Tramite>> {
  return listarTramites(0, TAMANIO_PREVIA_COLA, ESTADO_COLA);
}

/** Número de ganaderos de la gestoría (el `totalElements` de GET /ganaderos; `size=1`: no trae el
 * listado entero solo para contar). */
export function contarGanaderos(signal?: AbortSignal): Promise<number> {
  return listarGanaderos(0, 1, { signal }).then((pagina) => pagina.totalElements);
}
