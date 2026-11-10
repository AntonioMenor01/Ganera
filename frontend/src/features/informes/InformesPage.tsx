import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { aErrorApi, mensajeDeError, type ErrorApi } from "@/shared/api/errores";
import { CLASE_ENLACE } from "@/shared/ui/enlace";
import { ESTADOS_TRAMITE } from "@/features/tramites/etiquetas";
import type { EstadoTramite } from "@/features/tramites/types";
import {
  contarExplotaciones,
  contarGanaderos,
  contarTramitesPorEstado,
  contarTramitesTotales,
  ESTADOS_INFORME,
} from "./api";

type Resumen = {
  porEstado: Record<EstadoTramite, number>;
  totalTramites: number;
  ganaderos: number;
  explotaciones: number;
};

export function InformesPage() {
  const [resumen, setResumen] = useState<Resumen | null>(null);
  const [error, setError] = useState<ErrorApi | null>(null);
  const [cargando, setCargando] = useState(true);
  const [version, setVersion] = useState(0);
  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setError(null);
    Promise.all([
      Promise.all(ESTADOS_INFORME.map((estado) => contarTramitesPorEstado(estado))),
      contarTramitesTotales(),
      contarGanaderos(),
      contarExplotaciones(),
    ])
      .then(([conteos, totalTramites, ganaderos, explotaciones]) => {
        if (cancelado) return;
        const porEstado = {} as Record<EstadoTramite, number>;
        ESTADOS_INFORME.forEach((estado, i) => {
          porEstado[estado] = conteos[i] ?? 0;
        });
        setResumen({ porEstado, totalTramites, ganaderos, explotaciones });
        setCargando(false);
      })
      .catch((err) => {
        if (cancelado) return;
        setResumen(null);
        setError(aErrorApi(err));
        setCargando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [version]);

  return (
    <div className="mx-auto flex w-full max-w-[1360px] min-w-0 flex-col gap-5">
      <div className="border-b-2 border-divisor pb-5">
        <p className="mb-1.5 text-[13px] font-medium text-muted-foreground">
          Recuento vivo de tu gestoría
        </p>
        <h1 className="text-[28px] leading-tight font-bold tracking-[-0.02em]">Informes</h1>
        <p className="mt-1 text-[13px] text-muted-foreground">
          Datos reales de trámites, ganaderos y explotaciones. Sin exportar a PDF/Excel todavía.
        </p>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertTitle>No se ha podido cargar el resumen</AlertTitle>
          <AlertDescription className="flex flex-col gap-3">
            <p>{mensajeDeError(error, "listar-tramites")}</p>
            <Button type="button" variant="outline" size="sm" className="w-fit" onClick={recargar}>
              Reintentar
            </Button>
          </AlertDescription>
        </Alert>
      )}

      <section
        aria-label="Inventario"
        className="grid grid-cols-1 border border-border bg-card sm:grid-cols-3"
      >
        <Tarjeta
          etiqueta="Trámites"
          valor={resumen?.totalTramites}
          cargando={cargando}
          to="/tramites"
          className="sm:border-r sm:border-border"
        />
        <Tarjeta
          etiqueta="Ganaderos"
          valor={resumen?.ganaderos}
          cargando={cargando}
          to="/ganaderos"
          className="border-t border-border sm:border-t-0 sm:border-r"
        />
        <Tarjeta
          etiqueta="Explotaciones"
          valor={resumen?.explotaciones}
          cargando={cargando}
          to="/explotaciones"
          className="border-t border-border sm:border-t-0"
        />
      </section>

      <section aria-label="Trámites por estado" className="flex flex-col gap-0 border border-border bg-card">
        <div className="flex h-14 items-center border-b-2 border-divisor px-5">
          <h2 className="text-lg font-bold">Trámites por estado</h2>
        </div>
        <ul className="divide-y divide-border">
          {ESTADOS_INFORME.map((estado) => (
            <li key={estado} className="flex items-center justify-between gap-4 px-5 py-3 text-sm">
              <Link to="/tramites" className={CLASE_ENLACE}>
                {ESTADOS_TRAMITE[estado].etiqueta}
              </Link>
              <span className="font-medium tabular-nums">
                {cargando ? "…" : (resumen?.porEstado[estado] ?? "—")}
              </span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

function Tarjeta({
  etiqueta,
  valor,
  cargando,
  to,
  className,
}: {
  etiqueta: string;
  valor: number | undefined;
  cargando: boolean;
  to: string;
  className?: string;
}) {
  return (
    <Link
      to={to}
      className={`flex flex-col gap-1 px-5 py-4 outline-none transition-colors hover:bg-gris-200 focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring ${className ?? ""}`}
    >
      <span className="text-[13px] text-muted-foreground">{etiqueta}</span>
      <span className="text-2xl font-bold tabular-nums">
        {cargando ? "…" : valor != null ? valor : "—"}
      </span>
    </Link>
  );
}
