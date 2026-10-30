/** Resultados de GET /buscar (buscador global). DTOs del backend; nunca credenciales OVZ. */

export interface GanaderoResultado {
  id: number;
  nombre: string;
  nif: string | null;
}

export interface ExplotacionResultado {
  id: number;
  codigoRega: string;
  nombre: string;
  /** La explotación vive dentro de la ficha de su ganadero: se navega a ese ganadero. */
  ganaderoId: number;
  ganaderoNombre: string;
}

export interface AnimalResultado {
  crotal: string;
  explotacionCodigoRega: string;
  ganaderoId: number;
  ganaderoNombre: string;
}

export interface ResultadosBusqueda {
  ganaderos: GanaderoResultado[];
  explotaciones: ExplotacionResultado[];
  animales: AnimalResultado[];
}

/** true si no hay ningún resultado en ningún grupo. */
export function sinResultados(r: ResultadosBusqueda): boolean {
  return r.ganaderos.length === 0 && r.explotaciones.length === 0 && r.animales.length === 0;
}
