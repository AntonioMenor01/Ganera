import { useCallback, useEffect, useState } from "react";
import { listarAlertas } from "@/features/alertas/api";
import { aErrorApi, type ErrorApi } from "@/shared/api/errores";
import { construirNovedades, type ItemNovedad } from "./novedades";

export interface EstadoNovedades {
  items: ItemNovedad[];
  /** Nº para el badge de la campana (= nº de alertas urgentes). */
  total: number;
  cargando: boolean;
  error: ErrorApi | null;
  recargar: () => void;
}

/**
 * Trae las novedades de `GET /alertas` (sin backend nuevo). Carga al montar y cuando se pide
 * recargar (p. ej. al abrir el panel), así el badge no se queda obsoleto. Un fallo deja la campana
 * vacía y sin número (nunca miente ni rompe el layout que la contiene).
 */
export function useNovedades(): EstadoNovedades {
  const [items, setItems] = useState<ItemNovedad[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<ErrorApi | null>(null);
  const [version, setVersion] = useState(0);

  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    listarAlertas()
      .then((alertas) => {
        if (cancelado) return;
        setError(null);
        setItems(construirNovedades(alertas));
        setCargando(false);
      })
      .catch((err: unknown) => {
        if (cancelado) return;
        setError(aErrorApi(err));
        setItems([]);
        setCargando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [version]);

  return { items, total: items.length, cargando, error, recargar };
}
