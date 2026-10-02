/** Teléfonos de contacto: llegan en E.164 (TelefonoNormalizador del backend). */

const MOVIL_O_FIJO_ESPANOL = /^\+34(\d{3})(\d{3})(\d{3})$/;

/** Texto visible, agrupado para leerlo de un vistazo: "+34 612 345 678". Solo se agrupa un número
 * español de 9 cifras; cualquier otro sale tal cual (no se inventan agrupaciones de otros países). */
export function formatearTelefono(telefono: string): string {
  const partes = MOVIL_O_FIJO_ESPANOL.exec(telefono);
  return partes ? `+34 ${partes[1]} ${partes[2]} ${partes[3]}` : telefono;
}

/** Destino del enlace: siempre el valor E.164 exacto, sin espacios. */
export function hrefTelefono(telefono: string): string {
  return `tel:${telefono.replace(/\s+/g, "")}`;
}
