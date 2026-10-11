/** Hecho en OVZ (2026-10-07, D5 de la ficha OVZ): textos y fecha de la marca que pone el gestor
 * cuando ya ha metido el trámite en OVZ él mismo. Ganera no envía nada a OVZ: la persona es siempre
 * el sujeto («hecho en OVZ», «marcado por…»), nunca «registrado», «enviado» ni «procesado».
 * Lógica pura: sin React ni red. */

import { fechaHoraMadrid } from "./fechaMadrid";

/** «07/10/2026, 12:30» (hora de Madrid, sea cual sea la zona del equipo) a partir del ISO del
 * backend. Un valor ausente o que no es una fecha da null: nunca lanza ni enseña «Invalid Date». El
 * formateo vive en fechaMadrid.ts (B2 reducido lo comparte con las respuestas por WhatsApp). */
export function fechaHechoEnOvz(iso: string | null | undefined): string | null {
  return fechaHoraMadrid(iso);
}

/** La franja: «Hecho en OVZ por Ana Pérez el 07/10/2026, 12:30». Sin nombre, sin «por…»; sin fecha
 * válida, sin «el…». */
export function textoHechoEnOvz(marca: {
  hechoEnOvzPorNombre: string | null | undefined;
  hechoEnOvzEn: string | null | undefined;
}): string {
  const nombre = marca.hechoEnOvzPorNombre?.trim();
  const fecha = fechaHechoEnOvz(marca.hechoEnOvzEn);
  return ["Hecho en OVZ", nombre ? `por ${nombre}` : null, fecha ? `el ${fecha}` : null]
    .filter((parte) => parte !== null)
    .join(" ");
}

export const TEXTO_BOTON_MARCAR = "Marcar como hecho en OVZ";
export const TEXTO_BOTON_MARCANDO = "Marcando…";
/** Pista del botón de marcar: quien lo hace en OVZ es la persona. */
export const PISTA_MARCAR_HECHO_EN_OVZ = "Púlsalo cuando lo hayas terminado tú en OVZ. Ganera no envía nada.";

export const TEXTO_BOTON_QUITAR_MARCA = "Quitar la marca";
export const TEXTO_BOTON_QUITANDO_MARCA = "Quitando la marca…";
/** Confirmación en línea de «Quitar la marca» (mismo patrón que Rechazar). */
export const PREGUNTA_QUITAR_MARCA = "¿Quitar la marca de hecho en OVZ? En OVZ no cambia nada.";
export const TEXTO_CONFIRMAR_QUITAR_MARCA = "Sí, quitar la marca";
