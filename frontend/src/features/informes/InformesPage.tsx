import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
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
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-1">
        <h1 className="text-xl font-bold tracking-tight">Informes</h1>
        <p className="text-sm text-muted-foreground">
          Resumen de tu gestoría con datos reales de trámites, ganaderos y explotaciones. No es un
          exportador: es el recuento vivo de lo que hay ahora en Ganera.
        </p>
      </header>

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

      <section aria-label="Inventario" className="grid gap-4 sm:grid-cols-3">
        <Tarjeta
          etiqueta="Trámites"
          valor={resumen?.totalTramites}
          cargando={cargando}
          to="/tramites"
        />
        <Tarjeta
          etiqueta="Ganaderos"
          valor={resumen?.ganaderos}
          cargando={cargando}
          to="/ganaderos"
        />
        <Tarjeta
          etiqueta="Explotaciones"
          valor={resumen?.explotaciones}
          cargando={cargando}
          to="/explotaciones"
        />
      </section>

      <section aria-label="Trámites por estado" className="flex flex-col gap-3">
        <h2 className="text-base font-bold">Trámites por estado</h2>
        <ul className="divide-y divide-border rounded-sm border border-border bg-card">
          {ESTADOS_INFORME.map((estado) => (
            <li key={estado} className="flex items-center justify-between gap-4 px-4 py-3 text-sm">
              <Link
                to="/tramites"
                className={CLASE_ENLACE}
                // La cola abre en pendiente; el usuario cambia el filtro allí.
              >
                {ESTADOS_TRAMITE[estado].etiqueta}
              </Link>
              <span className="tabular-nums font-medium">
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
}: {
  etiqueta: string;
  valor: number | undefined;
  cargando: boolean;
  to: string;
}) {
  return (
    <Link to={to} className="rounded-sm outline-none focus-visible:ring-3 focus-visible:ring-ring">
      <Card className="h-full transition-colors hover:border-gris-400">
        <CardContent className="flex flex-col gap-1">
          <span className="text-sm text-muted-foreground">{etiqueta}</span>
          <span className="text-2xl font-bold tabular-nums">
            {cargando ? "…" : valor != null ? valor : "—"}
          </span>
        </CardContent>
      </Card>
    </Link>
  );
}
