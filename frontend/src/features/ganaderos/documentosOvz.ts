/** Justificantes de OVZ en la ficha del ganadero (escalón 2, T5). Lógica y textos puros: sin React
 * ni red. La fecha se formatea con `fechaHoraMadrid` (hora de Madrid) donde se use. */

/** Etiqueta legible del tipo de documento (`TipoDocumentoOvz` del backend). Uno desconocido se muestra
 * tal cual, nunca en blanco. */
export function etiquetaTipoDocumento(tipo: string): string {
  const TIPOS: Record<string, string> = {
    BAJA_ANIMAL: "Baja de animal",
  };
  return TIPOS[tipo] ?? (tipo.trim() === "" ? "Documento" : tipo);
}

/** Tamaño de un fichero, legible (B / KB / MB). Un valor no finito o negativo da «—» (nunca «NaN»). */
export function formatearTamano(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return "—";
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb < 10 ? kb.toFixed(1) : Math.round(kb)} KB`;
  const mb = kb / 1024;
  return `${mb.toFixed(1)} MB`;
}
