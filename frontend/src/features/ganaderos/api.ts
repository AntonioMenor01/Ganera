import { httpClient } from "@/shared/api/httpClient";
import type { Pagina } from "@/shared/api/types";
import type { GanaderoDetalle, GanaderoResumen } from "./types";

export interface OpcionesListarGanaderos {
  /** Valores de `sort` en orden (`campo,asc|desc`); campos permitidos: nombre, nif, id. Vacío =
   * el orden por defecto del backend (nombre, id). */
  sort?: readonly string[];
  signal?: AbortSignal;
}

export async function listarGanaderos(
  page: number,
  size: number,
  { sort = [], signal }: OpcionesListarGanaderos = {},
): Promise<Pagina<GanaderoResumen>> {
  // URLSearchParams y no un array en `params`: axios serializaría `sort[]=`, que Spring no entiende.
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  for (const valor of sort) params.append("sort", valor);
  const { data } = await httpClient.get<Pagina<GanaderoResumen>>("/ganaderos", { params, signal });
  return data;
}

export async function obtenerGanadero(id: number, signal?: AbortSignal): Promise<GanaderoDetalle> {
  const { data } = await httpClient.get<GanaderoDetalle>(`/ganaderos/${id}`, { signal });
  return data;
}
