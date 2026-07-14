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
  errores: ImportErrorFila[];
}
