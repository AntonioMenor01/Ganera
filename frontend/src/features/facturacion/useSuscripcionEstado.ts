import { useCallback, useEffect, useState } from "react";
import { aErrorApi, type ErrorApi } from "@/shared/api/errores";
import { obtenerEstadoSuscripcion } from "./api";
import type { SuscripcionEstado } from "./types";

export interface SuscripcionEstadoHook {
  estado: SuscripcionEstado | null;
  cargando: boolean;
  /** null si no hay error. Se muestra con mensajeDeError(error, "suscripcion"). */
  error: ErrorApi | null;
  recargar: () => void;
}

/** null en estado (con cargando=false y error=false) significa "nunca tuvo Suscripcion", no un fallo. */
export function useSuscripcionEstado(): SuscripcionEstadoHook {
  const [estado, setEstado] = useState<SuscripcionEstado | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<ErrorApi | null>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setError(null);
    obtenerEstadoSuscripcion()
      .then((resultado) => {
        if (!cancelado) setEstado(resultado);
      })
      .catch((err: unknown) => {
        if (!cancelado) setError(aErrorApi(err));
      })
      .finally(() => {
        if (!cancelado) setCargando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [version]);

  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  return { estado, cargando, error, recargar };
}
