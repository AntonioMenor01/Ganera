/**
 * Etiqueta y consulta del selector de explotación del modal de revisión. Desde la T4 de la tarea
 * antes del piloto la búsqueda la hace el backend (`GET /explotaciones?q=`, useBuscarExplotaciones):
 * aquí ya no se filtra nada en cliente.
 */

/**
 * Lo mínimo para nombrar una explotación: una del listado (`Explotacion`) o la guardada del
 * detalle del trámite (`ExplotacionAsignada`, con `explotacionCodigoRega`/`explotacionNombre`).
 */
export interface ExplotacionConEtiqueta {
  id?: number;
  codigoRega: string | null;
  nombre: string | null;
}

/** Lo que se ve en el campo y en cada opción: "REGA · nombre". */
export function etiquetaExplotacion(explotacion: ExplotacionConEtiqueta): string {
  if (!explotacion.codigoRega) return `Explotación #${explotacion.id ?? "?"}`;
  return explotacion.nombre ? `${explotacion.codigoRega} · ${explotacion.nombre}` : explotacion.codigoRega;
}

/**
 * La `q` que se pide para lo escrito en el campo. D3b: tal cual, solo recortada (sin partir en
 * palabras ni quitar tildes: lo decide el backend). D3d: si el texto es la etiqueta de la
 * explotación elegida (lo que el combobox escribe al elegirla o al abrirlo con una ya elegida), se
 * trata como consulta vacía, para enseñar la primera página y no solo la elegida.
 */
export function consultaDeBusqueda(texto: string, elegida: ExplotacionConEtiqueta | null): string {
  const consulta = texto.trim();
  // La etiqueta también se recorta: un nombre guardado con espacios al final no debe impedirlo.
  if (elegida && consulta === etiquetaExplotacion(elegida).trim()) return "";
  return consulta;
}
