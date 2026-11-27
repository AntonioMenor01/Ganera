import type { Alerta } from "@/features/alertas/api";
import type { VarianteBadge } from "@/features/tramites/etiquetas";

/**
 * Centro de novedades (idea #2, cara gestoría): un indicador siempre visible con lo urgente que
 * necesita tu atención —plazos legales que vencen y trámites atascados—, para que Ganera te "tire"
 * de vuelta antes de que haya una multa, desde cualquier pantalla. Se compone de `GET /alertas`,
 * que YA existe en producción (plazos de baja/confirmación y cola atascada); no hay backend nuevo.
 * Los avisos salientes automáticos por email/WhatsApp (ideas #1/#2-email/#3) son backend y se
 * conectarán aquí cuando existan.
 */
export interface ItemNovedad {
  id: string;
  titulo: string;
  detalle: string;
  variante: VarianteBadge;
  /** Ruta a la que lleva (la cola o el calendario). */
  to: string;
  /** Si lleva a un trámite concreto, su id (su revisión se abre vía router state). */
  tramiteId: number | null;
  /** Fecha de vencimiento ISO (yyyy-MM-dd) o null. */
  venceEl: string | null;
}

/** La severidad del backend ("warning"/"critical") a la variante de color del sistema. */
export function varianteDeSeveridad(severidad: string): VarianteBadge {
  return severidad === "critical" ? "danger" : "warning";
}

/**
 * Convierte las alertas (ya ordenadas por vencimiento en el backend) en novedades. Nunca inventa
 * nada: sin alertas, la lista va vacía (estado "estás al día" honesto). Una alerta con `tramiteId`
 * lleva a su revisión; sin él, al calendario.
 */
export function construirNovedades(alertas: Alerta[]): ItemNovedad[] {
  return alertas.map((a) => ({
    id: a.id,
    titulo: a.titulo,
    detalle: a.detalle,
    variante: varianteDeSeveridad(a.severidad),
    to: a.tramiteId != null ? "/tramites" : "/calendario",
    tramiteId: a.tramiteId,
    venceEl: a.venceEl,
  }));
}

/** El número del badge de la campana (tope visual "9+"), o null si no hay nada. */
export function textoContador(n: number): string | null {
  if (n <= 0) return null;
  return n > 9 ? "9+" : String(n);
}
