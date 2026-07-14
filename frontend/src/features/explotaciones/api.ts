import { httpClient } from "@/shared/api/httpClient";
import type { Pagina } from "@/shared/api/types";
import type { Explotacion, ImportResumen } from "./types";

export async function listarExplotaciones(page: number, size: number): Promise<Pagina<Explotacion>> {
  const { data } = await httpClient.get<Pagina<Explotacion>>("/explotaciones", {
    params: { page, size },
  });
  return data;
}

export async function importarExcel(archivo: File): Promise<ImportResumen> {
  const formData = new FormData();
  formData.append("archivo", archivo);
  const { data } = await httpClient.post<ImportResumen>("/explotaciones/importar", formData);
  return data;
}
