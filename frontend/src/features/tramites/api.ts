import { httpClient } from "@/shared/api/httpClient";
import type { Pagina } from "@/shared/api/types";
import type { EstadoTramite, Tramite, TramiteDetalle } from "./types";

export async function listarTramites(
  page: number,
  size: number,
  estado?: EstadoTramite,
): Promise<Pagina<Tramite>> {
  const { data } = await httpClient.get<Pagina<Tramite>>("/tramites", {
    params: { page, size, estado },
  });
  return data;
}

export async function obtenerDetalleTramite(
  id: number,
  signal?: AbortSignal,
): Promise<TramiteDetalle> {
  const { data } = await httpClient.get<TramiteDetalle>(`/tramites/${id}`, { signal });
  return data;
}

/**
 * Cuerpo de `PATCH /tramites/{id}`. `version` es obligatoria (la que mostraba la pantalla). Un
 * campo ausente = "no cambiar": el backend no permite vaciar explotación ni tipo (H5), por eso
 * aquí no admiten null. `crotales` es la lista COMPLETA y sustituye a la anterior (`[]` los quita
 * todos); el backend los normaliza, clasifica y valida.
 */
export interface ActualizacionTramite {
  version: number;
  explotacionId?: number;
  tipoTramite?: string;
  crotales?: string[];
}

/** Guarda los cambios de la revisión. Devuelve el detalle actualizado (`version + 1`). */
export async function actualizarTramite(
  id: number,
  cambios: ActualizacionTramite,
): Promise<TramiteDetalle> {
  // Solo los campos presentes: un undefined no debe viajar ni como clave.
  const cuerpo: ActualizacionTramite = { version: cambios.version };
  if (cambios.explotacionId !== undefined) cuerpo.explotacionId = cambios.explotacionId;
  if (cambios.tipoTramite !== undefined) cuerpo.tipoTramite = cambios.tipoTramite;
  if (cambios.crotales !== undefined) cuerpo.crotales = cambios.crotales;
  const { data } = await httpClient.patch<TramiteDetalle>(`/tramites/${id}`, cuerpo);
  return data;
}

/** Solo cambia el estado en BD -- OvzAutomationService.ejecutarTramite() no esta implementado
 * todavia (Prompt 3c), aprobar no dispara ninguna ejecucion real contra OVZ.net.
 * `version` es la que mostraba la pantalla: si no es la actual, el backend responde 409. */
export async function aprobarTramite(id: number, version: number): Promise<Tramite> {
  const { data } = await httpClient.post<Tramite>(`/tramites/${id}/aprobar`, { version });
  return data;
}

/** Como aprobar, solo cambia el estado en BD. `version` es la que mostraba la pantalla y es
 * obligatoria desde el mini-prompt de backend tras A2 (H4): sin ella el backend responde 400, y
 * si no es la actual, 409. */
export async function rechazarTramite(id: number, version: number): Promise<Tramite> {
  const { data } = await httpClient.post<Tramite>(`/tramites/${id}/rechazar`, { version });
  return data;
}
