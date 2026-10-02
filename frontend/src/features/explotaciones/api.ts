import { httpClient } from "@/shared/api/httpClient";
import type { Pagina } from "@/shared/api/types";
import type { Animal, Explotacion, ImportResumen } from "./types";

export interface OpcionesListarExplotaciones {
  /** `campo,asc|desc`; campos permitidos por el backend: codigoRega, nombre, id. */
  sort?: string;
  signal?: AbortSignal;
}

export async function listarExplotaciones(
  page: number,
  size: number,
  { sort, signal }: OpcionesListarExplotaciones = {},
): Promise<Pagina<Explotacion>> {
  const { data } = await httpClient.get<Pagina<Explotacion>>("/explotaciones", {
    params: { page, size, sort },
    signal,
  });
  return data;
}

/**
 * Orden explícito `crotal,asc` + `id,asc` (campos permitidos: crotal, id). El crotal ya es UNIQUE y
 * es también el orden por defecto del backend, pero pedirlo aquí deja el orden de la pantalla
 * escrito en el frontend: no cambia si algún día cambia el valor por defecto del endpoint, e `id`
 * es un desempate que no cuesta nada.
 */
const ORDEN_ANIMALES = ["crotal,asc", "id,asc"] as const;

export async function listarAnimalesDeExplotacion(
  explotacionId: number,
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<Pagina<Animal>> {
  // URLSearchParams y no un array en `params`: axios serializaría `sort[]=`, que Spring no entiende.
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  for (const valor of ORDEN_ANIMALES) params.append("sort", valor);
  const { data } = await httpClient.get<Pagina<Animal>>(`/explotaciones/${explotacionId}/animales`, {
    params,
    signal,
  });
  return data;
}

export async function importarExcel(archivo: File): Promise<ImportResumen> {
  const formData = new FormData();
  formData.append("archivo", archivo);
  const { data } = await httpClient.post<ImportResumen>("/explotaciones/importar", formData);
  return data;
}
