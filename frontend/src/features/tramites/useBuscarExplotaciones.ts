import { useCallback, useEffect, useRef, useState } from "react";
import { listarExplotaciones } from "@/features/explotaciones/api";
import type { Explotacion } from "@/features/explotaciones/types";
import { aErrorApi, esCancelacion, type ErrorApi } from "@/shared/api/errores";

/** Espera entre pulsaciones antes de pedir (D3a). */
export const ESPERA_BUSQUEDA_MS = 300;
/** Resultados por búsqueda: la primera página (D3c). El orden es el del backend (codigoRega, id). */
export const TAMANIO_BUSQUEDA = 20;

export type BusquedaExplotaciones =
  /** Desplegable cerrado: no se pide nada. */
  | { estado: "inactiva" }
  | { estado: "buscando" }
  | { estado: "error"; error: ErrorApi }
  /** `total` es el `totalElements` del backend: puede ser mayor que las recibidas (D3c). */
  | { estado: "lista"; explotaciones: Explotacion[]; total: number };

type Resultado =
  | { estado: "error"; error: ErrorApi }
  | { estado: "lista"; explotaciones: Explotacion[]; total: number };

/**
 * Búsqueda de explotaciones del combobox del modal de revisión con `GET /explotaciones?q=` (punto 3
 * del plan de la tarea antes del piloto), en vez de la lista completa filtrada en cliente.
 *
 * - D3a: solo con el desplegable abierto. Al abrir (y al reintentar) se pide enseguida; al teclear,
 *   300 ms después de la última pulsación. Cada petición nueva cancela la anterior con su
 *   AbortController, y una cancelación nunca se enseña.
 * - Una respuesta vieja nunca pisa a una nueva: el resultado se guarda con la CLAVE de la búsqueda
 *   que lo pidió (apertura + intento + consulta) y solo se enseña si coincide con la actual; además,
 *   la limpieza del efecto aborta la anterior y su `then` comprueba `signal.aborted` (en jsdom el
 *   abort de XHR no siempre corta la respuesta, así que la clave es la garantía).
 * - D3b: la consulta va tal cual (solo recortada): sin partir en palabras ni quitar tildes, que es
 *   cosa del backend (regresión aceptada, anotada en ganera-prompts.md).
 *
 * El "buscando" no se guarda: se deriva de que la clave actual aún no tiene resultado. Así no hay
 * setState síncrono dentro del efecto.
 */
export function useBuscarExplotaciones({
  abierta,
  consulta,
}: {
  abierta: boolean;
  consulta: string;
}): { busqueda: BusquedaExplotaciones; reintentar: () => void } {
  const q = consulta.trim();
  const [intento, setIntento] = useState(0);
  // Cada apertura cuenta como búsqueda nueva: reabrir vuelve a pedir (los datos pueden haber
  // cambiado, p. ej. tras importar) aunque la consulta sea la misma.
  const [aperturas, setAperturas] = useState(abierta ? 1 : 0);
  const [abiertaPrevia, setAbiertaPrevia] = useState(abierta);
  if (abiertaPrevia !== abierta) {
    setAbiertaPrevia(abierta);
    if (abierta) setAperturas((n) => n + 1);
  }

  const clave = `${aperturas}|${intento}|${q}`;
  const [resultado, setResultado] = useState<{ clave: string; resultado: Resultado } | null>(null);

  // Qué apertura/intento se lanzó la última vez: si solo cambió la consulta, se espera (teclear);
  // si es una apertura o un reintento, se pide ya.
  const ultimoLanzado = useRef<string | null>(null);

  useEffect(() => {
    if (!abierta) return;
    const origen = `${aperturas}|${intento}`;
    const espera = ultimoLanzado.current === origen ? ESPERA_BUSQUEDA_MS : 0;
    const controlador = new AbortController();
    const temporizador = setTimeout(() => {
      // Se anota al salir de verdad (no al programarlo): si la limpieza lo cancela antes (p. ej. el
      // doble efecto de StrictMode), la apertura sigue pidiéndose sin espera.
      ultimoLanzado.current = origen;
      listarExplotaciones(0, TAMANIO_BUSQUEDA, { q, signal: controlador.signal })
        .then((pagina) => {
          if (controlador.signal.aborted) return;
          setResultado({
            clave,
            resultado: { estado: "lista", explotaciones: pagina.content, total: pagina.totalElements },
          });
        })
        .catch((err: unknown) => {
          if (controlador.signal.aborted || esCancelacion(err)) return;
          setResultado({ clave, resultado: { estado: "error", error: aErrorApi(err) } });
        });
    }, espera);
    return () => {
      clearTimeout(temporizador);
      controlador.abort();
    };
  }, [abierta, aperturas, intento, q, clave]);

  const reintentar = useCallback(() => setIntento((n) => n + 1), []);

  let busqueda: BusquedaExplotaciones;
  if (!abierta) busqueda = { estado: "inactiva" };
  else if (resultado?.clave === clave) busqueda = resultado.resultado;
  else busqueda = { estado: "buscando" };

  return { busqueda, reintentar };
}
