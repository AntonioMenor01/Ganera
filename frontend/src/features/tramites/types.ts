export type EstadoTramite =
  | "PENDIENTE_EXTRACCION"
  | "PENDIENTE_REVISION"
  | "APROBADO"
  | "EN_PROCESO"
  | "EJECUTADO_OVZ"
  | "ERROR_OVZ"
  | "RECHAZADO";

export interface Tramite {
  id: number;
  explotacionId: number | null;
  tipoTramite: string | null;
  estado: EstadoTramite;
  motivoError: string | null;
}

export interface TramiteDetalle {
  id: number;
  tipoTramite: string | null;
  estado: EstadoTramite;
  motivoError: string | null;
  explotacionId: number | null;
  explotacionCodigoRega: string | null;
  explotacionNombre: string | null;
  mensajeOriginal: string | null;
}

/** Mapeo de EstadoTramite a la variante de Badge semántica (ver paleta de marca).
 * PENDIENTE_EXTRACCION/PENDIENTE_REVISION/EN_PROCESO: aún no resuelto -> aviso.
 * APROBADO/EJECUTADO_OVZ: resuelto favorablemente -> positivo.
 * ERROR_OVZ/RECHAZADO: resuelto desfavorablemente -> negativo. */
const BADGE_POR_ESTADO: Record<EstadoTramite, "success" | "warning" | "danger"> = {
  PENDIENTE_EXTRACCION: "warning",
  PENDIENTE_REVISION: "warning",
  EN_PROCESO: "warning",
  APROBADO: "success",
  EJECUTADO_OVZ: "success",
  ERROR_OVZ: "danger",
  RECHAZADO: "danger",
};

export function badgeVarianteDeEstado(estado: EstadoTramite): "success" | "warning" | "danger" {
  return BADGE_POR_ESTADO[estado];
}
