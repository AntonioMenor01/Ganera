import type { RolContacto } from "@/features/tramites/etiquetas";

/** Fila de GET /ganaderos (GanaderoResumenResponse). Nunca trae credenciales de OVZ.net. */
export interface GanaderoResumen {
  id: number;
  nombre: string;
  /** Nullable en BD: un ganadero anterior a V14 puede no tenerlo. */
  nif: string | null;
  numeroExplotaciones: number;
}

/** GET /ganaderos/{id} (GanaderoDetalleResponse). Nunca trae credenciales de OVZ.net. */
export interface GanaderoDetalle {
  id: number;
  nombre: string;
  nif: string | null;
  /** Ordenadas por código REGA en el backend. */
  explotaciones: ExplotacionDeGanadero[];
}

export interface ExplotacionDeGanadero {
  id: number;
  codigoRega: string;
  nombre: string;
  /** Solo contactos activos. */
  contactos: ContactoDeExplotacion[];
}

export interface ContactoDeExplotacion {
  contactoId: number;
  nombre: string;
  /** E.164 (TelefonoNormalizador del backend), p. ej. "+34612345678". */
  telefono: string;
  /** Texto del backend; un rol que el frontend aún no conozca se muestra tal cual. */
  rol: RolContacto | (string & {});
}
