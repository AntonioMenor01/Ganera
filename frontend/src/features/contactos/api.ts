import { httpClient } from "@/shared/api/httpClient";
import type { Pagina } from "@/shared/api/types";

export type RolContacto = "TITULAR" | "EMPLEADO";

export interface ContactoExplotacion {
  explotacionId: number;
  codigoRega: string;
  nombre: string;
  rol: RolContacto;
}

export interface Contacto {
  id: number;
  telefono: string;
  nombre: string;
  activo: boolean;
  explotaciones: ContactoExplotacion[];
}

export async function listarContactos(
  page: number,
  size: number,
  incluirInactivos = false,
): Promise<Pagina<Contacto>> {
  const { data } = await httpClient.get<Pagina<Contacto>>("/contactos", {
    params: { page, size, incluirInactivos },
  });
  return data;
}

export async function crearContacto(telefono: string, nombre: string): Promise<Contacto> {
  const { data } = await httpClient.post<Contacto>("/contactos", { telefono, nombre });
  return data;
}

export async function actualizarContacto(
  id: number,
  telefono: string,
  nombre: string,
): Promise<Contacto> {
  const { data } = await httpClient.put<Contacto>(`/contactos/${id}`, { telefono, nombre });
  return data;
}

export async function desactivarContacto(id: number): Promise<void> {
  await httpClient.delete(`/contactos/${id}`);
}

export async function reactivarContacto(id: number): Promise<Contacto> {
  const { data } = await httpClient.post<Contacto>(`/contactos/${id}/reactivar`);
  return data;
}
