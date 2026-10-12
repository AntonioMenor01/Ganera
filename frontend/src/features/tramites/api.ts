import { httpClient } from "@/shared/api/httpClient";
import type { Pagina } from "@/shared/api/types";
import { ErrorApi } from "@/shared/api/errores";
import type { EstadoTramite, RespuestaWhatsapp, Tramite, TramiteDetalle } from "./types";

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

/** Hecho en OVZ: deja constancia de que el gestor ya metió el trámite en OVZ, a mano. Ganera no
 * envía nada a OVZ: solo guarda quién (sale del JWT, nunca del cuerpo) y cuándo. `version` es la
 * que mostraba la pantalla: si no es la actual, 409. Sin bloqueo de suscripción. */
export async function marcarHechoEnOvz(id: number, version: number): Promise<Tramite> {
  const { data } = await httpClient.post<Tramite>(`/tramites/${id}/marcar-hecho-ovz`, { version });
  return data;
}

/** Quita la marca de hecho en OVZ (vuelve a «Aprobado · falta OVZ»). En OVZ no cambia nada. */
export async function desmarcarHechoEnOvz(id: number, version: number): Promise<Tramite> {
  const { data } = await httpClient.post<Tramite>(`/tramites/${id}/desmarcar-hecho-ovz`, { version });
  return data;
}

/** SIN_TRAMITE: descarta un mensaje sin trámite (pasa a `RECHAZADO` con motivo `SIN_TRAMITE`). Solo
 * cambia el trámite en Ganera: no envía nada por WhatsApp ni a OVZ. `version` la de la pantalla (si
 * no es la actual, 409). Devuelve el DETALLE (con el historial), como el PATCH. Sin bloqueo de
 * suscripción. */
export async function descartarTramite(id: number, version: number): Promise<TramiteDetalle> {
  const { data } = await httpClient.post<TramiteDetalle>(`/tramites/${id}/descartar`, { version });
  return data;
}

/** SIN_TRAMITE: deshace un descarte (vuelve a `PENDIENTE_REVISION`). Solo sobre un descartado; un
 * rechazo normal da 409. Devuelve el DETALLE. */
export async function deshacerDescarte(id: number, version: number): Promise<TramiteDetalle> {
  const { data } = await httpClient.post<TramiteDetalle>(`/tramites/${id}/deshacer-descarte`, { version });
  return data;
}

/**
 * B2 reducido: responde al contacto que escribió el trámite, por WhatsApp. El destinatario lo decide
 * el servidor (el contacto del trámite); aquí solo viajan el texto y la clave de idempotencia, que se
 * genera al abrir el panel y se repite en los reintentos: con la misma clave el servidor devuelve la
 * respuesta guardada (`200`) sin volver a mandarla. `201` (nueva) y `200` son éxito, también cuando
 * la respuesta vuelve en `FALLIDO` o `SIN_CONFIRMAR`. Responder no cambia el trámite ni su `version`.
 *
 * El `503` de «no configurado» trae `{motivo}` y no guarda nada: es el único 5xx cuyo motivo se lee,
 * y solo en este endpoint (el modelo general nunca lee el cuerpo de un 5xx, M1). Un `503` sin
 * `{motivo}` (un gateway) sigue siendo un fallo incierto, como cualquier otro 5xx.
 */
export async function responderPorWhatsapp(
  tramiteId: number,
  texto: string,
  claveIdempotencia: string,
): Promise<RespuestaWhatsapp> {
  const respuesta = await httpClient.post<unknown>(
    `/tramites/${tramiteId}/respuestas-whatsapp`,
    { texto, claveIdempotencia },
    { validateStatus: (status) => (status >= 200 && status < 300) || status === 503 },
  );
  if (respuesta.status === 503) {
    throw new ErrorApi({ tipo: "servidor", status: 503, motivo: motivoDeObjeto(respuesta.data) });
  }
  return respuesta.data as RespuestaWhatsapp;
}

/** `{motivo}` de un cuerpo JSON; nunca el de un texto plano (un gateway o un proxy). */
function motivoDeObjeto(data: unknown): string | undefined {
  if (data === null || typeof data !== "object") return undefined;
  const motivo = (data as Record<string, unknown>).motivo;
  return typeof motivo === "string" ? motivo : undefined;
}
