import { useCallback, useEffect, useState } from "react";
import { aErrorApi, esCancelacion, type ErrorApi } from "@/shared/api/errores";
import { buscar } from "./api";
import type { ResultadosBusqueda } from "./types";

/** Espera entre pulsaciones antes de pedir. */
export const ESPERA_BUSQUEDA_MS = 300;
/** Por debajo de esto no se pide (coincide con el backend). */
export const LONGITUD_MINIMA = 2;

export type EstadoBusqueda =
  /** Buscador cerrado: no se pide nada. */
  | { estado: "inactiva" }
  /** Consulta demasiado corta: se invita a escribir, sin pedir. */
  | { estado: "corta" }
  | { estado: "buscando" }
  | { estado: "error"; error: ErrorApi }
  | { estado: "lista"; resultados: ResultadosBusqueda };

/**
 * Búsqueda global con debounce, solo mientras el buscador está abierto. Cada petición nueva cancela
 * la anterior (AbortController) y una respuesta vieja nunca pisa a una nueva: el resultado se guarda
 * con la CLAVE (intento + consulta) que lo pidió y solo se muestra si coincide con la actual. Mismo
 * patrón que `useBuscarExplotaciones`.
 */
export function useBuscadorGlobal({
  abierto,
  consulta,
}: {
  abierto: boolean;
  consulta: string;
}): { busqueda: EstadoBusqueda; reintentar: () => void } {
  const q = consulta.trim();
  const corta = q.length < LONGITUD_MINIMA;
  const [intento, setIntento] = useState(0);
  const clave = `${intento}|${q}`;
  const [resultado, setResultado] = useState<{
    clave: string;
    r: { estado: "error"; error: ErrorApi } | { estado: "lista"; resultados: ResultadosBusqueda };
  } | null>(null);

  useEffect(() => {
    if (!abierto || corta) return;
    const controlador = new AbortController();
    const temporizador = setTimeout(() => {
      buscar(q, controlador.signal)
        .then((resultados) => {
          if (controlador.signal.aborted) return;
          setResultado({ clave, r: { estado: "lista", resultados } });
        })
        .catch((err: unknown) => {
          if (controlador.signal.aborted || esCancelacion(err)) return;
          setResultado({ clave, r: { estado: "error", error: aErrorApi(err) } });
        });
    }, ESPERA_BUSQUEDA_MS);
    return () => {
      clearTimeout(temporizador);
      controlador.abort();
    };
  }, [abierto, corta, q, clave]);

  const reintentar = useCallback(() => setIntento((n) => n + 1), []);

  let busqueda: EstadoBusqueda;
  if (!abierto) busqueda = { estado: "inactiva" };
  else if (corta) busqueda = { estado: "corta" };
  else if (resultado?.clave === clave) busqueda = resultado.r;
  else busqueda = { estado: "buscando" };

  return { busqueda, reintentar };
}
