/** Presentación automática de la baja por muerte en OVZ (escalón 2, T5). Lógica y textos puros: sin
 * React ni red. Los consume el modal de revisión (`TramiteReviewDialog`).
 *
 * Honestidad (D10): la aprobación en Ganera ES la revisión humana. Sobre un trámite ya aprobado,
 * Ganera presenta la baja por sí sola en OVZ, con verificación previa (crotal + explotación) y sin
 * reintento ante incertidumbre. Por eso estos textos SÍ dicen «presentar/presentado en OVZ» (es
 * cierto: lo hace Ganera), a diferencia de la vía manual, donde Ganera no envía nada. Nunca se
 * sugiere que Ganera presente sin la aprobación del gestor. */

import { fechaHoraMadrid } from "./fechaMadrid";
import type { TramiteDetalle } from "./types";

/** El botón de aprobar: si al aprobar Ganera va a presentar la baja en OVZ, lo dice. */
export const TEXTO_APROBAR = "Aprobar";
export const TEXTO_APROBAR_Y_PRESENTAR = "Aprobar y presentar en OVZ";

export function textoBotonAprobar(detalle: Pick<TramiteDetalle, "sePresentaraAutomaticamente">): string {
  return detalle.sePresentaraAutomaticamente ? TEXTO_APROBAR_Y_PRESENTAR : TEXTO_APROBAR;
}

/** Lo que se va a presentar en OVZ, para la vista previa antes de aprobar (crotal, explotación,
 * fecha de muerte, MER). Cada campo puede faltar: el modal lo pinta como «—». */
export interface VistaPreviaPresentacion {
  crotal: string | null;
  explotacion: string | null;
  fechaMuerte: string | null;
  numeroMer: string | null;
}

/** `yyyy-MM-dd` → `dd/MM/yyyy`; cualquier otra cosa se devuelve tal cual (nunca lanza ni oculta el
 * dato). Las fechas del backend son ISO date sin hora, así que no hay zona que aplicar. */
export function fechaDdMmAaaa(fecha: string | null | undefined): string | null {
  if (typeof fecha !== "string") return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(fecha.trim());
  return m ? `${m[3]}/${m[2]}/${m[1]}` : fecha;
}

function texto(valor: string | null | undefined): string | null {
  return typeof valor === "string" && valor.trim() !== "" ? valor : null;
}

export function vistaPreviaPresentacion(detalle: TramiteDetalle): VistaPreviaPresentacion {
  // La baja se presenta con un único crotal; si hubiera varios, se enseña el primero completo (el que
  // se presentaría). El backend ya exige exactamente uno para presentar: aquí solo se muestra.
  const primero = detalle.crotales[0];
  const explotacion =
    detalle.explotacionCodigoRega && detalle.explotacionNombre
      ? `${detalle.explotacionCodigoRega} · ${detalle.explotacionNombre}`
      : texto(detalle.explotacionCodigoRega) ?? texto(detalle.explotacionNombre);
  return {
    crotal: primero ? texto(primero.crotal) ?? texto(primero.crotalIndicado) : null,
    explotacion,
    fechaMuerte: fechaDdMmAaaa(detalle.fechaHecho),
    numeroMer: texto(detalle.numeroMer),
  };
}

// ---------------------------------------------------------------------------------------------
// Estado de la presentación automática para los estados NO editables del modal.

/** Qué panel de presentación automática toca enseñar, derivado del estado del trámite y del trabajo.
 * `null` = nada que enseñar (vía manual o estado sin presentación). */
export type EstadoPresentacionOvz =
  /** APROBADO con un trabajo PENDIENTE: encolado, esperando turno del worker. */
  | { clase: "en-cola" }
  /** EN_PROCESO: Ganera está presentando la baja en OVZ ahora mismo. */
  | { clase: "presentando" }
  /** EJECUTADO_OVZ: la baja se presentó; `fecha` es cuándo (hora de Madrid), o null. */
  | { clase: "presentada"; fecha: string | null }
  /** ERROR_OVZ: falló. `incierto` decide el aviso y la casilla de confirmación del reintento (D11). */
  | { clase: "error"; incierto: boolean }
  /** APROBADO con el trabajo en ENSAYO_OK: el worker llegó hasta antes de «Aceptar» sin presentar nada. */
  | { clase: "ensayo" };

export function estadoPresentacionOvz(detalle: TramiteDetalle): EstadoPresentacionOvz | null {
  const presentacion = detalle.presentacionOvz;
  switch (detalle.estado) {
    case "EN_PROCESO":
      return { clase: "presentando" };
    case "EJECUTADO_OVZ":
      return { clase: "presentada", fecha: fechaHoraMadrid(presentacion?.actualizadoEn ?? null) };
    case "ERROR_OVZ":
      return { clase: "error", incierto: presentacion?.claseError === "INCIERTO" };
    case "APROBADO":
      if (presentacion?.estadoTrabajo === "PENDIENTE") return { clase: "en-cola" };
      if (presentacion?.estadoTrabajo === "ENSAYO_OK") return { clase: "ensayo" };
      return null;
    default:
      return null;
  }
}

/** Reintentar la presentación de una baja en ERROR_OVZ (reencolar, D11). `incierto` exige confirmar
 * primero en OVZ que no se presentó (casilla obligatoria). */
export function esErrorIncierto(detalle: TramiteDetalle): boolean {
  const estado = estadoPresentacionOvz(detalle);
  return estado?.clase === "error" && estado.incierto;
}

// Textos de cada panel (una sola fuente; el modal solo los pinta).
export const TEXTOS_PRESENTACION = {
  enCola: {
    titulo: "Se presentará en OVZ automáticamente",
    cuerpo: "En cuanto le llegue el turno, Ganera entrará en OVZ con las credenciales del ganadero y presentará la baja. No tienes que hacer nada.",
  },
  presentando: {
    titulo: "Presentando en OVZ…",
    cuerpo: "Ganera está presentando la baja en OVZ. Puede tardar unos segundos; vuelve a abrir el trámite para ver el resultado.",
  },
  presentada: {
    titulo: "Presentado en OVZ",
    cuerpoSinFecha: "Ganera presentó la baja en OVZ. El justificante queda en la ficha del ganadero.",
    /** Con la fecha: «Ganera presentó la baja en OVZ el 07/10/2026, 12:30. …». */
    cuerpoConFecha: (fecha: string) =>
      `Ganera presentó la baja en OVZ el ${fecha}. El justificante queda en la ficha del ganadero.`,
  },
  errorSeguro: {
    titulo: "No se ha podido presentar en OVZ",
    cuerpo: "La baja no llegó a presentarse (falló antes de confirmar en OVZ). Revisa el motivo, corrige lo que haga falta y vuelve a intentarlo.",
  },
  errorIncierto: {
    titulo: "No sabemos si la baja se presentó en OVZ",
    cuerpo: "Ganera ya había confirmado en OVZ cuando perdió la conexión, así que la baja podría estar presentada. Compruébalo en OVZ antes de reintentar: reintentar sin comprobarlo podría duplicarla.",
    /** Casilla obligatoria para poder reintentar un error incierto (D11). */
    confirmacion: "He comprobado en OVZ que la baja NO está presentada.",
  },
  ensayo: {
    titulo: "Ensayo completado",
    cuerpo: "El ensayo llegó hasta el último paso en OVZ sin presentar nada (modo de pruebas). La baja no se ha presentado.",
  },
} as const;

export const TEXTO_BOTON_REINTENTAR = "Reintentar";
export const TEXTO_BOTON_REINTENTANDO = "Reintentando…";
