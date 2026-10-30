/** Saludo y fecha del encabezado del inicio, en hora de Madrid (donde trabajan las gestorías), sea
 * cual sea la zona del equipo. Lógica pura: sin React ni red, para poder fijar «ahora» en los tests.
 * Comparte el criterio de zona de `features/tramites/fechaMadrid.ts`. */

/** Franja del saludo por la hora (0-23) de Madrid. Mismos cortes que usa el español de oficina:
 * madrugada y mañana → «Buenos días»; tarde → «Buenas tardes»; noche → «Buenas noches». */
export function saludoPorHora(hora: number): string {
  if (hora >= 6 && hora < 14) return "Buenos días";
  if (hora >= 14 && hora < 21) return "Buenas tardes";
  return "Buenas noches";
}

/** La hora (0-23) que marca el reloj de pared en Madrid para ese instante. Si Intl falla (muy raro),
 * cae a la hora local en vez de lanzar. */
export function horaEnMadrid(fecha: Date): number {
  try {
    const texto = new Intl.DateTimeFormat("es-ES", {
      timeZone: "Europe/Madrid",
      hour: "2-digit",
      hourCycle: "h23",
    }).format(fecha);
    const hora = Number.parseInt(texto, 10);
    return Number.isNaN(hora) ? fecha.getHours() : hora;
  } catch {
    return fecha.getHours();
  }
}

/** Saludo completo para un instante dado (por defecto, ahora). */
export function saludo(fecha: Date = new Date()): string {
  return saludoPorHora(horaEnMadrid(fecha));
}

/** El nombre de pila: la primera palabra del nombre, para un saludo cercano («Buenas tardes, Lucía»).
 * Un nombre vacío o solo espacios devuelve cadena vacía (quien llama decide si saluda sin nombre). */
export function nombrePila(nombre: string | null | undefined): string {
  if (typeof nombre !== "string") return "";
  const [primera = ""] = nombre.trim().split(/\s+/);
  return primera;
}

/** «Viernes, 30 de octubre de 2026» (hora de Madrid). La primera letra va en mayúscula (es-ES deja
 * el día de la semana en minúscula). null si Intl falla: el encabezado la omite en vez de lanzar. */
export function fechaLargaMadrid(fecha: Date = new Date()): string | null {
  try {
    const texto = new Intl.DateTimeFormat("es-ES", {
      timeZone: "Europe/Madrid",
      weekday: "long",
      day: "numeric",
      month: "long",
      year: "numeric",
    }).format(fecha);
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  } catch {
    return null;
  }
}
