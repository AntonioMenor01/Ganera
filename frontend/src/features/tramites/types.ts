import type { EstadoTramite, ResolucionCrotal } from "./etiquetas";

// Estados, tipos, resoluciones y sus etiquetas viven en etiquetas.ts (una sola fuente por dominio).
// badgeVarianteDeEstado se reexporta aquí porque DESIGN.md la cita desde este fichero.
export { badgeVarianteDeEstado } from "./etiquetas";
export type { EstadoTramite, ResolucionCrotal, TipoTramite } from "./etiquetas";

/** Crotal de un trámite (`TramiteCrotalResponse`). `crotal` es el completo si `EN_INVENTARIO` y,
 * si no, igual a `crotalIndicado`; `animalId` es null salvo en `EN_INVENTARIO`. */
export interface TramiteCrotal {
  crotalIndicado: string;
  crotal: string;
  animalId: number | null;
  enInventario: boolean;
  resolucion: ResolucionCrotal;
}

// tipoTramite es string (no TipoTramite): el backend puede traer tipos que el frontend aún no
// conoce (prompt B). Se etiqueta con etiquetaTipoTramite, que muestra tal cual lo desconocido.
export interface Tramite {
  id: number;
  explotacionId: number | null;
  tipoTramite: string | null;
  estado: EstadoTramite;
  motivoError: string | null;
  crotales: TramiteCrotal[];
  version: number;
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
  crotales: TramiteCrotal[];
  version: number;
}
