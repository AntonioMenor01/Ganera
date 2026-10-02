import { useCallback, useEffect, useState } from "react";
import { aErrorApi, esCancelacion, type ErrorApi } from "@/shared/api/errores";
import { cargarTodasLasExplotaciones } from "./todasLasExplotaciones";
import type { Explotacion } from "./types";

export type CargaTodasLasExplotaciones =
  | { estado: "cargando" }
  | { estado: "error"; error: ErrorApi }
  | {
      estado: "listo";
      explotaciones: Explotacion[];
      porId: ReadonlyMap<number, Explotacion>;
    };

/**
 * Lista completa de explotaciones (ver cargarTodasLasExplotaciones), cacheada durante la vida del
 * componente que usa el hook: se pide una vez al montar y solo se vuelve a pedir con `reintentar`.
 * Mientras carga o si falla no hay lista; nunca expone una lista parcial.
 */
export function useTodasLasExplotaciones(): {
  carga: CargaTodasLasExplotaciones;
  reintentar: () => void;
} {
  const [carga, setCarga] = useState<CargaTodasLasExplotaciones>({ estado: "cargando" });
  const [intento, setIntento] = useState(0);

  const reintentar = useCallback(() => {
    setCarga({ estado: "cargando" });
    setIntento((n) => n + 1);
  }, []);

  useEffect(() => {
    const controlador = new AbortController();
    cargarTodasLasExplotaciones(controlador.signal)
      .then((explotaciones) => {
        if (controlador.signal.aborted) return;
        setCarga({
          estado: "listo",
          explotaciones,
          porId: new Map(explotaciones.map((explotacion) => [explotacion.id, explotacion])),
        });
      })
      .catch((err: unknown) => {
        if (controlador.signal.aborted || esCancelacion(err)) return;
        setCarga({ estado: "error", error: aErrorApi(err) });
      });
    return () => controlador.abort();
  }, [intento]);

  return { carga, reintentar };
}
