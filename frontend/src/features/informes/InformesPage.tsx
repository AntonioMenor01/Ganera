import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  CalendarIcon,
  ChartColumnIcon,
  CheckCircle2Icon,
  ClipboardListIcon,
  DownloadIcon,
  FileSpreadsheetIcon,
  UsersIcon,
  WarehouseIcon,
  type LucideIcon,
} from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
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

type Periodo = "mes" | "trimestre" | "ano" | "custom";

type Resumen = {
  porEstado: Record<EstadoTramite, number>;
  totalTramites: number;
  ganaderos: number;
  explotaciones: number;
};

const PERIODOS: { id: Periodo; label: string }[] = [
  { id: "mes", label: "Este mes" },
  { id: "trimestre", label: "Trimestre" },
  { id: "ano", label: "Año" },
  { id: "custom", label: "Personalizado" },
];

const SUB_PERIODO: Record<Periodo, string> = {
  mes: "Totales actuales (sin filtro por mes todavía)",
  trimestre: "Totales actuales (sin filtro por trimestre todavía)",
  ano: "Totales actuales (sin filtro por año todavía)",
  custom: "Totales actuales (rango personalizado aún no disponible)",
};

/** Informes al look del mock v2: cabecera con export, segmentos, KPIs y paneles. Solo conteos reales. */
export function InformesPage() {
  const [resumen, setResumen] = useState<Resumen | null>(null);
  const [error, setError] = useState<ErrorApi | null>(null);
  const [cargando, setCargando] = useState(true);
  const [version, setVersion] = useState(0);
  const [periodo, setPeriodo] = useState<Periodo>("trimestre");
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

  const kpis = useMemo(() => {
    const hechos = resumen?.porEstado.HECHO_EN_OVZ;
    const pendientes = resumen?.porEstado.PENDIENTE_REVISION;
    return [
      {
        label: "Hechos en OVZ",
        Icono: CheckCircle2Icon as LucideIcon,
        value: hechos,
        foot: "Marcados por el equipo",
        accent: false,
      },
      {
        label: "Pendientes de revisión",
        Icono: ClipboardListIcon as LucideIcon,
        value: pendientes,
        foot: "En la cola ahora",
        accent: true,
      },
      {
        label: "Ganaderos",
        Icono: UsersIcon as LucideIcon,
        value: resumen?.ganaderos,
        foot: "En tu gestoría",
        accent: false,
      },
      {
        label: "Explotaciones",
        Icono: WarehouseIcon as LucideIcon,
        value: resumen?.explotaciones,
        foot: "Activas en inventario",
        accent: false,
      },
    ];
  }, [resumen]);

  const barrasEstado = useMemo(() => {
    const max = Math.max(1, ...ESTADOS_INFORME.map((e) => resumen?.porEstado[e] ?? 0));
    return ESTADOS_INFORME.map((estado, i, arr) => {
      const v = resumen?.porEstado[estado] ?? 0;
      return {
        estado,
        label: ESTADOS_TRAMITE[estado].etiqueta,
        v,
        h: `${Math.round((v / max) * 180)}px`,
        actual: i === arr.length - 1,
      };
    });
  }, [resumen]);

  const maxTipo = Math.max(1, ...ESTADOS_INFORME.map((e) => resumen?.porEstado[e] ?? 0));

  return (
    <div className="mx-auto flex w-full max-w-[1360px] min-w-0 flex-col gap-5">
      <div className="flex flex-wrap items-end justify-between gap-4 border-b-2 border-divisor pb-5">
        <div className="min-w-0">
          <p className="mb-1.5 text-[13px] font-medium text-muted-foreground">
            {SUB_PERIODO[periodo]}
          </p>
          <h1 className="text-[28px] leading-tight font-bold tracking-[-0.02em]">Informes</h1>
        </div>
        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            className="h-10 gap-2 bg-card"
            disabled
            title="Exportar a PDF aún no está disponible"
          >
            <DownloadIcon aria-hidden="true" className="size-4" />
            PDF
          </Button>
          <Button
            type="button"
            variant="outline"
            className="h-10 gap-2 bg-card"
            disabled
            title="Exportar a Excel aún no está disponible"
          >
            <FileSpreadsheetIcon aria-hidden="true" className="size-4" />
            Excel
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <CalendarIcon aria-hidden="true" className="size-4 text-muted-foreground" />
        <div role="tablist" aria-label="Periodo" className="flex border border-input">
          {PERIODOS.map((p, i) => {
            const activo = periodo === p.id;
            return (
              <button
                key={p.id}
                type="button"
                role="tab"
                aria-selected={activo}
                onClick={() => setPeriodo(p.id)}
                className={cn(
                  "h-9 px-3.5 text-[13px] font-medium outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring",
                  i > 0 && "border-l border-input",
                  activo
                    ? "bg-primary text-primary-foreground"
                    : "bg-card text-foreground hover:bg-gris-200",
                )}
              >
                {p.label}
              </button>
            );
          })}
        </div>
        {periodo === "custom" && (
          <p className="text-[13px] text-muted-foreground">
            El rango de fechas personalizado llegará con el histórico.
          </p>
        )}
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
        aria-label="Indicadores"
        className="grid grid-cols-1 border border-border bg-card sm:grid-cols-2 lg:grid-cols-4"
      >
        {kpis.map((k, i) => (
          <div
            key={k.label}
            className={cn(
              "flex flex-col gap-2.5 px-5 py-5",
              i > 0 && "border-t border-border sm:border-t-0 sm:border-l",
            )}
          >
            <span className="flex items-center gap-2 text-[13px] font-medium">
              <k.Icono aria-hidden="true" className="size-4" />
              {k.label}
            </span>
            <span
              className={cn(
                "text-[40px] leading-none font-bold tabular-nums",
                k.accent && "text-primary",
              )}
            >
              {cargando ? "…" : k.value != null ? k.value : "—"}
            </span>
            <span className="text-[13px] text-muted-foreground">{k.foot}</span>
          </div>
        ))}
      </section>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
        <section className="border border-border bg-card">
          <div className="flex h-14 items-center gap-2.5 border-b-2 border-divisor px-5">
            <ChartColumnIcon aria-hidden="true" className="size-4" />
            <h2 className="text-lg font-bold">Trámites por estado</h2>
          </div>
          <div className="flex h-[260px] items-end gap-4 box-border px-6 pt-6 pb-4">
            {barrasEstado.map((b) => (
              <div key={b.estado} className="flex min-w-0 flex-1 flex-col items-stretch gap-1.5">
                <span className="text-[13px] font-bold tabular-nums">
                  {cargando ? "…" : b.v}
                </span>
                <div
                  className={cn("w-full", b.actual ? "bg-primary" : "bg-foreground")}
                  style={{ height: cargando ? "8px" : b.h }}
                />
                <span className="truncate text-[11px] text-muted-foreground" title={b.label}>
                  {abreviarEstado(b.label)}
                </span>
              </div>
            ))}
          </div>
        </section>

        <section className="border border-border bg-card">
          <div className="flex h-14 items-center gap-2.5 border-b-2 border-divisor px-5">
            <ClipboardListIcon aria-hidden="true" className="size-4" />
            <h2 className="text-lg font-bold">Detalle por estado</h2>
          </div>
          <ul className="flex flex-col gap-3 px-5 py-4">
            {ESTADOS_INFORME.map((estado) => {
              const v = resumen?.porEstado[estado] ?? 0;
              const w = `${Math.round((v / maxTipo) * 100)}%`;
              return (
                <li key={estado} className="flex flex-col gap-1">
                  <div className="flex items-center gap-2 text-[13px]">
                    <Link to="/tramites" className={cn(CLASE_ENLACE, "flex-1 font-medium")}>
                      {ESTADOS_TRAMITE[estado].etiqueta}
                    </Link>
                    <span className="font-bold tabular-nums">{cargando ? "…" : v}</span>
                  </div>
                  <div className="h-2 bg-muted">
                    <div className="h-full bg-foreground" style={{ width: cargando ? "0%" : w }} />
                  </div>
                </li>
              );
            })}
          </ul>
        </section>
      </div>

      <section className="border border-border bg-card">
        <div className="flex h-14 items-center gap-2.5 border-b-2 border-divisor px-5">
          <UsersIcon aria-hidden="true" className="size-4" />
          <h2 className="flex-1 text-lg font-bold">Actividad por ganadero</h2>
          <span className="text-[13px] text-muted-foreground">Trámites en el periodo</span>
        </div>
        <p className="px-5 py-8 text-sm text-muted-foreground">
          El ranking por ganadero aún no tiene endpoint. Mientras,{" "}
          <Link to="/ganaderos" className={CLASE_ENLACE}>
            revisa la lista de ganaderos
          </Link>
          {resumen != null && (
            <>
              {" "}
              ({resumen.ganaderos} en total · {resumen.totalTramites} trámites).
            </>
          )}
        </p>
      </section>
    </div>
  );
}

function abreviarEstado(etiqueta: string): string {
  if (etiqueta.length <= 12) return etiqueta;
  const corto: Record<string, string> = {
    "Pendiente de extracción": "Extracción",
    "Pendiente de revisión": "Revisión",
    Aprobado: "Aprobado",
    "Hecho en OVZ": "Hecho OVZ",
    Rechazado: "Rechazado",
  };
  return corto[etiqueta] ?? etiqueta.slice(0, 10);
}
