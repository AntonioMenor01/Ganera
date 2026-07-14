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
