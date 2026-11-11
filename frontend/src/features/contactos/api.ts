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

/** Tamaño de página al recorrer todos los contactos (la lista completa cabe de sobra en una o dos). */
const TAMANIO_PAGINA_TODOS = 100;

/**
 * Todos los contactos (activos por defecto) recorriendo las páginas una tras otra. Lo usa «Añadir
 * persona» para decidir en el cliente si un teléfono ya es contacto de la gestoría (→ solo enlazar)
 * o es nuevo (→ crear y enlazar). Secuencial a propósito: una petición a la vez.
 */
export async function listarTodosLosContactos(incluirInactivos = false): Promise<Contacto[]> {
  const primera = await listarContactos(0, TAMANIO_PAGINA_TODOS, incluirInactivos);
  const todos = [...primera.content];
  for (let page = 1; page < primera.totalPages; page += 1) {
    const siguiente = await listarContactos(page, TAMANIO_PAGINA_TODOS, incluirInactivos);
    todos.push(...siguiente.content);
  }
  return todos;
}

/**
 * Enlaza un contacto a una explotación, o cambia su rol si ya estaba enlazado (POST, 200). El
 * backend valida que contacto y explotación sean de la gestoría (404 si no) y que el contacto esté
 * activo (409 si está inactivo).
 */
export async function vincularExplotacion(
  contactoId: number,
  explotacionId: number,
  rol: RolContacto,
): Promise<void> {
  await httpClient.post(`/contactos/${contactoId}/explotaciones`, { explotacionId, rol });
}

/** Quita un contacto de una explotación (DELETE, 204). 404 si el enlace no es de la gestoría. */
export async function desvincularExplotacion(
  contactoId: number,
  explotacionId: number,
): Promise<void> {
  await httpClient.delete(`/contactos/${contactoId}/explotaciones/${explotacionId}`);
}
