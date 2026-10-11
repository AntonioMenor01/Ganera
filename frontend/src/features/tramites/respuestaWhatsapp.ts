/**
 * Responder por WhatsApp (B2 reducido, D2/D4/D5): texto propuesto por estado, validación del texto
 * (los mismos motivos que los 400 del backend) y estado de la ventana de 24 h. Lógica pura: sin React
 * ni red.
 *
 * Honestidad (decisión de Antonio sobre D2): solo la plantilla de `HECHO_EN_OVZ` menciona OVZ («ya
 * está hecho en OVZ», con la persona de la gestoría como sujeto). La de `APROBADO` no da nada por
 * presentado. Ninguna usa la lista de palabras prohibidas del test de honestidad del modal.
 */
import { etiquetaTipoTramite } from "./etiquetas";
import { fechaDeIso, fechaHoraMadrid, horaMadrid } from "./fechaMadrid";
import type { TramiteDetalle } from "./types";

/** Hueco que la persona tiene que completar antes de enviar (pendiente y rechazado). */
export const HUECO = "___";

/** Límite de Twilio. Se cuenta con `.length` (unidades UTF-16), igual que `String.length` del
 * backend: un emoji cuenta 2. */
export const MAX_CARACTERES = 1600;

/** Textos idénticos a los `400 {motivo}` del backend (T1, «Motivos exactos»). */
export const MOTIVO_TEXTO_VACIO = "Escribe el texto de la respuesta.";
export const MOTIVO_TEXTO_LARGO = `La respuesta admite como máximo ${MAX_CARACTERES} caracteres.`;
export const MOTIVO_HUECO = `Completa el hueco ${HUECO} antes de enviar.`;

const MAX_CROTALES_EN_TEXTO = 3;

type DatosPropuesta = Pick<TramiteDetalle, "estado" | "tipoTramite" | "crotales" | "whatsapp">;

function tipoEnFrase(tipoTramite: string | null): string | null {
  if (!tipoTramite) return null;
  const etiqueta = etiquetaTipoTramite(tipoTramite);
  // Solo baja la inicial de una etiqueta conocida («Baja por muerte» → «baja por muerte»); un tipo
  // desconocido se escribe tal cual.
  return etiqueta === tipoTramite ? etiqueta : `${etiqueta.charAt(0).toLowerCase()}${etiqueta.slice(1)}`;
}

/** «A», «A y B», «A, B y C», «A, B, C y 2 más». */
function listaCrotales(crotales: TramiteDetalle["crotales"]): string | null {
  const textos = crotales
    .map((c) => (c.crotal?.trim() ? c.crotal.trim() : (c.crotalIndicado ?? "").trim()))
    .filter((c) => c !== "");
  if (textos.length === 0) return null;
  const vistos = textos.slice(0, MAX_CROTALES_EN_TEXTO);
  const resto = textos.length - vistos.length;
  if (resto > 0) return `${vistos.join(", ")} y ${resto} más`;
  if (vistos.length === 1) return vistos[0];
  return `${vistos.slice(0, -1).join(", ")} y ${vistos[vistos.length - 1]}`;
}

/** «(baja por muerte de ES…)», «(baja por muerte)», «(ES…)» o nada. Empieza con espacio. */
function resumenTramite(datos: DatosPropuesta): string {
  const tipo = tipoEnFrase(datos.tipoTramite);
  const crotales = listaCrotales(datos.crotales);
  const contenido = tipo && crotales ? `${tipo} de ${crotales}` : (tipo ?? crotales);
  return contenido ? ` (${contenido})` : "";
}

/**
 * Texto propuesto al abrir el panel, siempre editable. Termina con la firma de la gestoría (el
 * número de WhatsApp es compartido). Cadena vacía si en ese estado no se ofrece responder o si el
 * trámite no tiene bloque de WhatsApp.
 */
export function textoPropuesto(datos: DatosPropuesta): string {
  const whatsapp = datos.whatsapp;
  if (!whatsapp) return "";
  const nombre = whatsapp.destinatarioNombre?.trim();
  const saludo = nombre ? `Hola, ${nombre}.` : "Hola.";
  let cuerpo: string;
  switch (datos.estado) {
    case "PENDIENTE_REVISION": {
      const tipo = tipoEnFrase(datos.tipoTramite);
      cuerpo = `Para tramitar ${tipo ? `tu ${tipo}` : "tu mensaje"} necesitamos: ${HUECO}`;
      break;
    }
    case "APROBADO":
      cuerpo = `Hemos revisado tu trámite${resumenTramite(datos)} y lo tramitamos en breve.`;
      break;
    case "HECHO_EN_OVZ":
      cuerpo = `Tu trámite${resumenTramite(datos)} ya está hecho en OVZ.`;
      break;
    case "RECHAZADO":
      cuerpo = `No hemos podido tramitar tu mensaje. ${HUECO}`;
      break;
    default:
      return "";
  }
  const firma = whatsapp.firmaGestoria?.trim();
  return `${saludo} ${cuerpo}${firma ? `\n— ${firma}` : ""}`;
}

/** Lo que hace falta de `crypto` para generar la clave (inyectable para los tests). */
export type FuenteAleatoria = Pick<Crypto, "getRandomValues"> & { randomUUID?: () => string };

/**
 * Clave de idempotencia (UUID v4) de una respuesta (D7). `crypto.randomUUID` solo existe en un
 * contexto seguro (https o localhost): entrando por http con la IP de la red local no está, y el panel
 * no se podría abrir (N5 de la revisión de la T2). Entonces se genera con `getRandomValues`, que sí
 * existe siempre, fijando los bits de versión (4) y variante (10xx) del RFC 4122.
 */
export function claveIdempotencia(fuente: FuenteAleatoria = crypto): string {
  if (typeof fuente.randomUUID === "function") return fuente.randomUUID();
  const bytes = fuente.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** Por qué no se puede enviar el texto, o null si se puede. Mismo orden y textos que el backend. */
export function problemaDelTexto(texto: string): string | null {
  if (texto.trim() === "") return MOTIVO_TEXTO_VACIO;
  if (texto.length > MAX_CARACTERES) return MOTIVO_TEXTO_LARGO;
  if (texto.includes(HUECO)) return MOTIVO_HUECO;
  return null;
}

export type EstadoVentana =
  | { tipo: "cerrada" }
  /** `hasta`: «11/03/2031, 07:00» en hora de Madrid. */
  | { tipo: "abierta"; hasta: string }
  /** Menos de 1 h: `hora`, «10:45» en hora de Madrid. */
  | { tipo: "cierra-pronto"; hora: string };

const UNA_HORA_MS = 60 * 60 * 1000;

/** Refleja la ventana de 24 h que calcula el servidor (el que decide es él). Nunca lanza. */
export function estadoVentana(ventanaAbiertaHasta: string | null | undefined, ahora: Date): EstadoVentana {
  const hasta = fechaDeIso(ventanaAbiertaHasta);
  if (!hasta) return { tipo: "cerrada" };
  const restante = hasta.getTime() - ahora.getTime();
  if (restante <= 0) return { tipo: "cerrada" };
  if (restante < UNA_HORA_MS) {
    const hora = horaMadrid(ventanaAbiertaHasta);
    return hora ? { tipo: "cierra-pronto", hora } : { tipo: "cerrada" };
  }
  const texto = fechaHoraMadrid(ventanaAbiertaHasta);
  return texto ? { tipo: "abierta", hasta: texto } : { tipo: "cerrada" };
}
