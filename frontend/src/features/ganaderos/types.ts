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
  /** E.164 (TelefonoNormalizador del backend), p. ej. "+34612345678"; null si no tiene. Solo en el
   * detalle: ni el listado ni el buscador lo traen. */
  telefono: string | null;
  /** Hay contraseña de OVZ.net guardada (cifrada en el backend). La contraseña NUNCA llega al frontend:
   * solo este booleano. */
  tieneCredencialesOvz: boolean;
  /** La cuenta de OVZ.net entra con certificado digital: la baja no se podrá enviar en automático (se hace
   * a mano). */
  usaCertificadoOvz: boolean;
  /** Ordenadas por código REGA en el backend. */
  explotaciones: ExplotacionDeGanadero[];
}

export interface ExplotacionDeGanadero {
  id: number;
  codigoRega: string;
  nombre: string;
  /** Animales en inventario de esta explotación. */
  numeroAnimales: number;
  /** Trámites en PENDIENTE_REVISION de esta explotación. */
  tramitesPendientesRevision: number;
  /** Solo contactos activos. */
  contactos: ContactoDeExplotacion[];
}

/** Metadatos de un justificante de OVZ de la ficha del ganadero (`DocumentoOvzResponse`, escalón 2, T5).
 * Nunca trae el binario: se descarga aparte por `GET /documentos-ovz/{id}/contenido`. */
export interface DocumentoOvz {
  id: number;
  tramiteId: number;
  /** `TipoDocumentoOvz` del backend, p. ej. `"BAJA_ANIMAL"`. Se etiqueta con `etiquetaTipoDocumento`. */
  tipo: string;
  /** Nombre del fichero, p. ej. `"Baja Animal.pdf"`. */
  nombre: string;
  /** Tamaño en bytes. */
  tamano: number;
  contentType: string;
  sha256: string;
  /** ISO-8601 UTC con `Z`. Se enseña en hora de Madrid. */
  creadoEn: string;
}

export interface ContactoDeExplotacion {
  contactoId: number;
  nombre: string;
  /** E.164 (TelefonoNormalizador del backend), p. ej. "+34612345678". */
  telefono: string;
  /** Texto del backend; un rol que el frontend aún no conozca se muestra tal cual. */
  rol: RolContacto | (string & {});
}
