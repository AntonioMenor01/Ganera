/** Fechas y horas en hora de Madrid (donde trabajan las gestorías), sea cual sea la zona del equipo.
 * Extraído de hechoEnOvz.ts (B2 reducido) para usarlo también en las respuestas por WhatsApp.
 * Lógica pura: sin React ni red. */

/** `hourCycle: "h23"`: nunca «a. m./p. m.». En la hora repetida de octubre las dos 02:30 se ven
 * igual: es lo que marca el reloj de pared. El formateador se crea en cada llamada (es barato y se usa
 * poco): así nada depende de la zona que tuviera el proceso al cargar el módulo. */
const OPCIONES_FECHA_HORA: Intl.DateTimeFormatOptions = {
  timeZone: "Europe/Madrid",
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
};

const OPCIONES_HORA: Intl.DateTimeFormatOptions = {
  timeZone: "Europe/Madrid",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
};

/** La fecha de un ISO, o null si falta o no es una fecha. Nunca lanza. */
export function fechaDeIso(iso: string | null | undefined): Date | null {
  if (typeof iso !== "string" || iso.trim() === "") return null;
  const fecha = new Date(iso);
  return Number.isNaN(fecha.getTime()) ? null : fecha;
}

function formatear(fecha: Date, opciones: Intl.DateTimeFormatOptions): string | null {
  try {
    return new Intl.DateTimeFormat("es-ES", opciones).format(fecha);
  } catch {
    return null;
  }
}

/** «07/10/2026, 12:30» (hora de Madrid) a partir del ISO del backend. Un valor ausente o que no es
 * una fecha da null: nunca lanza ni enseña «Invalid Date». */
export function fechaHoraMadrid(iso: string | null | undefined): string | null {
  const fecha = fechaDeIso(iso);
  return fecha ? formatear(fecha, OPCIONES_FECHA_HORA) : null;
}

/** «12:30» (hora de Madrid). Mismas reglas que `fechaHoraMadrid`. */
export function horaMadrid(iso: string | null | undefined): string | null {
  const fecha = fechaDeIso(iso);
  return fecha ? formatear(fecha, OPCIONES_HORA) : null;
}
