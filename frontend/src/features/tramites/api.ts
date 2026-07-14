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

export async function obtenerDetalleTramite(id: number): Promise<TramiteDetalle> {
  const { data } = await httpClient.get<TramiteDetalle>(`/tramites/${id}`);
  return data;
}

/** Solo cambia el estado en BD -- OvzAutomationService.ejecutarTramite() no esta implementado
 * todavia (Prompt 3c), aprobar no dispara ninguna ejecucion real contra OVZ.net. */
export async function aprobarTramite(id: number): Promise<Tramite> {
  const { data } = await httpClient.post<Tramite>(`/tramites/${id}/aprobar`);
  return data;
}

export async function rechazarTramite(id: number): Promise<Tramite> {
  const { data } = await httpClient.post<Tramite>(`/tramites/${id}/rechazar`);
  return data;
}
