/** SIN_TRAMITE (2026-10-12, A1/A6): textos de «Descartar» y de su historial (quién y cuándo). Descartar
 * solo cambia el trámite en Ganera: no envía nada ni por WhatsApp ni a OVZ. La hora, siempre la de
 * Madrid (`fechaMadrid.ts`). Lógica pura: sin React ni red. */

import { buscar } from "./etiquetas";
import { fechaHoraMadrid } from "./fechaMadrid";
import type { EventoDescarte } from "./types";

export const TEXTO_BOTON_DESCARTAR = "Descartar";
export const TEXTO_BOTON_DESCARTANDO = "Descartando…";
export const TEXTO_BOTON_DESHACER = "Deshacer";
export const TEXTO_BOTON_DESHACIENDO = "Deshaciendo…";
/** Pista de «Descartar» desactivado por cambios sin guardar (la de aprobar no vale: aquí no se aprueba). */
export const PISTA_DESCARTAR_SIN_GUARDAR = "Para descartar el mensaje, quita antes los cambios con “Descartar cambios”.";
/** Pista junto a «Deshacer». */
export const PISTA_DESHACER = "Vuelve a la cola de pendientes.";

const INICIO_POR_TIPO: Readonly<Record<string, string>> = {
  DESCARTADO: "Descartado",
  DESCARTE_DESHECHO: "Descarte deshecho",
};

const INICIO_BANDA = "Descartado como mensaje sin trámite";

/** La ficha OVZ de un descartado (no tiene ficha): título y frase, distintos de un rechazo normal. */
export const TITULO_FICHA_DESCARTADO = INICIO_BANDA;
export const TEXTO_FICHA_DESCARTADO = "Un mensaje descartado no se pasa a OVZ.";

/** «por {nombre}» y «el dd/mm/aaaa, hh:mm»; sin nombre o sin fecha válida, esa parte no se pone. */
function quienYCuando(evento: Pick<EventoDescarte, "usuarioNombre" | "en"> | undefined): string[] {
  if (!evento) return [];
  const nombre = typeof evento.usuarioNombre === "string" ? evento.usuarioNombre.trim() : "";
  const fecha = fechaHoraMadrid(evento.en);
  return [nombre ? `por ${nombre}` : null, fecha ? `el ${fecha}` : null].filter((p): p is string => p !== null);
}

/** Una línea del historial: «Descartado por Ana el 07/10/2026, 12:30» o «Descarte deshecho por …».
 * Un tipo desconocido da null (no se enseña). Nunca lanza. */
export function textoEventoDescarte(evento: EventoDescarte): string | null {
  const inicio = buscar<string>(INICIO_POR_TIPO, evento?.tipo ?? "");
  if (!inicio) return null;
  return [inicio, ...quienYCuando(evento)].join(" ");
}

/** Las líneas del historial, en el orden en que llegan (cronológico), sin los tipos desconocidos. */
export function lineasHistorialDescarte(historial: readonly EventoDescarte[] | null | undefined): string[] {
  if (!Array.isArray(historial)) return [];
  return historial.map(textoEventoDescarte).filter((linea): linea is string => linea !== null);
}

/** La banda de un descartado: «Descartado como mensaje sin trámite por Ana el 07/10/2026, 12:30.», con
 * el ÚLTIMO descarte del historial. Sin nombre, sin fecha o sin evento, se omite esa parte. */
export function textoBandaDescartado(historial: readonly EventoDescarte[] | null | undefined): string {
  const descartes = Array.isArray(historial) ? historial.filter((e) => e?.tipo === "DESCARTADO") : [];
  return `${[INICIO_BANDA, ...quienYCuando(descartes.at(-1))].join(" ")}.`;
}
