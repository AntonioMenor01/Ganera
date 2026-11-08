import { httpClient } from "@/shared/api/httpClient";
import type { ResultadosBusqueda } from "./types";

/**
 * Buscador global (GET /buscar?q=). El backend filtra por el gestoriaId del JWT y agrupa/limita;
 * con q de menos de 2 caracteres devuelve grupos vacíos (no es un error). La consulta va tal cual
 * (recortada): el backend normaliza (palabras, sin tildes, NIF/crotal en mayúsculas).
 */
export async function buscar(q: string, signal?: AbortSignal): Promise<ResultadosBusqueda> {
  const { data } = await httpClient.get<ResultadosBusqueda>("/buscar", { params: { q }, signal });
  return data;
}
