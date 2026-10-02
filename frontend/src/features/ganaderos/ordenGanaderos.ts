/** Ordenación del listado de Ganaderos (plan A2, decisión 14): solo por nombre y NIF. */

export type CampoOrdenGanadero = "nombre" | "nif";
export type DireccionOrden = "asc" | "desc";

export interface OrdenGanaderos {
  campo: CampoOrdenGanadero;
  direccion: DireccionOrden;
}

/** null = no se manda `sort`: el backend aplica su defecto (`nombre,id`), que se ve como nombre
 * ascendente. */
export const ORDEN_INICIAL: OrdenGanaderos | null = null;

const ORDEN_POR_DEFECTO_DEL_BACKEND: OrdenGanaderos = { campo: "nombre", direccion: "asc" };

function efectivo(orden: OrdenGanaderos | null): OrdenGanaderos {
  return orden ?? ORDEN_POR_DEFECTO_DEL_BACKEND;
}

/** Al pulsar una cabecera: la activa invierte su dirección; otra empieza en ascendente. */
export function siguienteOrden(
  actual: OrdenGanaderos | null,
  campo: CampoOrdenGanadero,
): OrdenGanaderos {
  const vigente = efectivo(actual);
  if (vigente.campo === campo) {
    return { campo, direccion: vigente.direccion === "asc" ? "desc" : "asc" };
  }
  return { campo, direccion: "asc" };
}

/** Valores de `sort` para la petición. Se añade `id` como desempate: con nombres repetidos (o NIF
 * vacíos) el orden sería inestable entre páginas y una fila podría salir dos veces o ninguna. */
export function parametrosSort(orden: OrdenGanaderos | null): string[] {
  if (!orden) return [];
  return [`${orden.campo},${orden.direccion}`, `id,${orden.direccion}`];
}

/** `aria-sort` de una cabecera: solo la columna activa lo lleva. */
export function ariaSortDe(
  orden: OrdenGanaderos | null,
  campo: CampoOrdenGanadero,
): "ascending" | "descending" | undefined {
  const vigente = efectivo(orden);
  if (vigente.campo !== campo) return undefined;
  return vigente.direccion === "asc" ? "ascending" : "descending";
}
