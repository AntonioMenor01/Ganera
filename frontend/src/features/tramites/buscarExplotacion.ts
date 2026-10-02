import type { Explotacion } from "@/features/explotaciones/types";

/**
 * Búsqueda del selector de explotación del modal de revisión (H3). Solo decide qué opciones se
 * MUESTRAN mientras el usuario escribe: no es una regla de negocio (cualquier explotación de la
 * gestoría se puede elegir, y el backend valida la elegida al guardar).
 */

/** Minúsculas y sin tildes, para que "martinez" encuentre "Martínez". */
function normalizar(texto: unknown): string {
  return typeof texto === "string"
    ? texto.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase()
    : "";
}

/** Lo que se ve en el campo y en cada opción: "REGA · nombre". */
export function etiquetaExplotacion(explotacion: Explotacion): string {
  return explotacion.nombre ? `${explotacion.codigoRega} · ${explotacion.nombre}` : explotacion.codigoRega;
}

/** Cada palabra de la consulta debe aparecer en el código REGA, el nombre o el ganadero. */
export function coincideExplotacion(explotacion: Explotacion, consulta: string): boolean {
  const palabras = normalizar(consulta).split(/[\s·]+/).filter(Boolean);
  if (palabras.length === 0) return true;
  const texto = [explotacion.codigoRega, explotacion.nombre, explotacion.nombreGanadero]
    .map(normalizar)
    .join(" ");
  return palabras.every((palabra) => texto.includes(palabra));
}
