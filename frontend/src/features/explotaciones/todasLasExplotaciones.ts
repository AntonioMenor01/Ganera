import { ErrorApi } from "@/shared/api/errores";
import { listarExplotaciones } from "./api";
import type { Explotacion } from "./types";

export const TAMANIO_PAGINA_TODAS_LAS_EXPLOTACIONES = 500;
export const ORDEN_TODAS_LAS_EXPLOTACIONES = "codigoRega,asc";

/** La lista no llegó completa o cambió mientras se pedía: se trata como un fallo de carga. */
function listaIncompleta(): ErrorApi {
  return new ErrorApi({ tipo: "desconocido" });
}

/**
 * Lista COMPLETA de explotaciones de la gestoría (plan A2, decisión 20). La usan la cola de
 * trámites (id → código REGA, decisión 22) y el combobox del modal de revisión (Task 9).
 *
 * Recorre todas las páginas de `GET /explotaciones` (500 por página, ordenadas por código REGA)
 * hasta `totalPages`. Es todo o nada: si falla cualquier página, si el total cambia entre páginas,
 * o si lo recibido no cuadra con `totalElements` (en número o con ids repetidos), lanza un
 * ErrorApi y no devuelve nada. Nunca una lista parcial.
 */
export async function cargarTodasLasExplotaciones(signal?: AbortSignal): Promise<Explotacion[]> {
  const opciones = { sort: ORDEN_TODAS_LAS_EXPLOTACIONES, signal };
  const primera = await listarExplotaciones(0, TAMANIO_PAGINA_TODAS_LAS_EXPLOTACIONES, opciones);
  const { totalElements, totalPages } = primera;
  const todas = [...primera.content];

  for (let pagina = 1; pagina < totalPages; pagina++) {
    const siguiente = await listarExplotaciones(
      pagina,
      TAMANIO_PAGINA_TODAS_LAS_EXPLOTACIONES,
      opciones,
    );
    if (siguiente.totalElements !== totalElements || siguiente.totalPages !== totalPages) {
      throw listaIncompleta();
    }
    todas.push(...siguiente.content);
  }

  const idsDistintos = new Set(todas.map((explotacion) => explotacion.id)).size;
  if (todas.length !== totalElements || idsDistintos !== totalElements) {
    throw listaIncompleta();
  }
  return todas;
}
