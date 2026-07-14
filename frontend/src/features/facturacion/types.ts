export type EstadoSuscripcion =
  | "TRIAL"
  | "TRIAL_EXPIRADO_SIN_PAGO"
  | "ACTIVA"
  | "IMPAGO_GRACIA"
  | "SUSPENDIDA"
  | "CANCELADA";

export interface SuscripcionEstado {
  estado: EstadoSuscripcion;
  puedeAprobarTramites: boolean;
  explotacionesContratadas: number | null;
}
