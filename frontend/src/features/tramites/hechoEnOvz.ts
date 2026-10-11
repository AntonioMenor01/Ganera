/** Hecho en OVZ (2026-10-07, D5 de la ficha OVZ): textos y fecha de la marca que pone el gestor
 * cuando ya ha metido el trámite en OVZ él mismo. Ganera no envía nada a OVZ: la persona es siempre
 * el sujeto («hecho en OVZ», «marcado por…»), nunca «registrado», «enviado» ni «procesado».
 * Lógica pura: sin React ni red. */

/** La hora se enseña siempre en Madrid (donde trabajan las gestorías), sea cual sea la zona del
 * equipo. `hourCycle: "h23"`: nunca «a. m./p. m.». En la hora repetida de octubre las dos 02:30 se
 * ven igual: es lo que marca el reloj de pared. Se crea en cada llamada (es barato y se usa poco):
 * así nada depende de la zona que tuviera el proceso al cargar el módulo. */
const OPCIONES_MADRID: Intl.DateTimeFormatOptions = {
  timeZone: "Europe/Madrid",
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
};

/** «07/10/2026, 12:30» (hora de Madrid) a partir del ISO del backend. Un valor ausente o que no es
 * una fecha da null: nunca lanza ni enseña «Invalid Date». */
export function fechaHechoEnOvz(iso: string | null | undefined): string | null {
  if (typeof iso !== "string" || iso.trim() === "") return null;
  const fecha = new Date(iso);
  if (Number.isNaN(fecha.getTime())) return null;
  try {
    return new Intl.DateTimeFormat("es-ES", OPCIONES_MADRID).format(fecha);
  } catch {
    return null;
  }
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
