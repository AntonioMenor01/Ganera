export type EstadoSuscripcion =
  | "TRIAL"
  | "TRIAL_EXPIRADO_SIN_PAGO"
  | "ACTIVA"
  | "IMPAGO_GRACIA"
  | "SUSPENDIDA"
  | "CANCELADA";

/** GET /facturacion/suscripcion. El backend también manda `explotacionesContratadas`, pero ya no lo
 * lee nadie en la app (lo enseñaba la página de Facturación, que se quitó): no se tipa. */
export interface SuscripcionEstado {
  estado: EstadoSuscripcion;
  puedeAprobarTramites: boolean;
}
