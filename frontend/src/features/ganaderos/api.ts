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

/**
 * Alta manual de un ganadero (POST /ganaderos). El backend valida el NIF y la unicidad y responde
 * 400/409 con `{ motivo }`; httpClient lo convierte en ErrorApi. Devuelve la fila creada (con
 * numeroExplotaciones 0).
 */
export async function crearGanadero(
  nombre: string,
  nif: string,
  telefono?: string,
): Promise<GanaderoResumen> {
  // El teléfono es opcional: solo se manda la clave si se escribió algo (el backend lo normaliza).
  const cuerpo = telefono ? { nombre, nif, telefono } : { nombre, nif };
  const { data } = await httpClient.post<GanaderoResumen>("/ganaderos", cuerpo);
  return data;
}

/**
 * Cambia (o quita) el teléfono de un ganadero (PUT /ganaderos/{id}/telefono). `null` lo borra. El
 * backend valida/normaliza y responde 400 `{motivo}` si no es válido, o 404 si no es de tu gestoría.
 * Devuelve el detalle actualizado, con el que se repinta la ficha sin volver a pedirlo.
 */
export async function cambiarTelefonoGanadero(
  id: number,
  telefono: string | null,
): Promise<GanaderoDetalle> {
  const { data } = await httpClient.put<GanaderoDetalle>(`/ganaderos/${id}/telefono`, { telefono });
  return data;
}
