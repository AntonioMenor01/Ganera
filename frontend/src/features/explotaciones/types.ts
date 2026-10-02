export interface Explotacion {
  id: number;
  codigoRega: string;
  nombre: string;
  ganaderoId: number;
  nombreGanadero: string;
}

export interface ImportHojaResumen {
  filasProcesadas: number;
  creadas: number;
  actualizadas: number;
}

export interface ImportErrorFila {
  hoja: string;
  fila: number;
  motivo: string;
}

export interface ImportResumen {
  explotaciones: ImportHojaResumen;
  animales: ImportHojaResumen;
  /** Hoja opcional: si el Excel no la trae, el backend manda 0/0/0. */
  contactos: ImportHojaResumen;
  errores: ImportErrorFila[];
}

/** Un animal del inventario de una explotación (GET /explotaciones/{id}/animales). */
export interface Animal {
  id: number;
  /** Crotal completo tal como está en el inventario (normalizado al importar desde A1). */
  crotal: string;
  /** Últimos 6 caracteres del crotal: lo que los contactos suelen escribir por WhatsApp. */
  crotalUltimosDigitos: string;
}
