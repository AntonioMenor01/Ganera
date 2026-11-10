import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  CalendarIcon,
  CheckCircle2Icon,
  ChevronLeftIcon,
  ChevronRightIcon,
  ClipboardCheckIcon,
  ListIcon,
  type LucideIcon,
} from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { aErrorApi, mensajeDeError, type ErrorApi } from "@/shared/api/errores";
import { CLASE_ENLACE } from "@/shared/ui/enlace";
import { listarAlertas, type Alerta } from "@/features/alertas/api";
import { listarTramites } from "@/features/tramites/api";
import { etiquetaTipoTramite, iconoTipoTramite } from "@/features/tramites/etiquetas";
import { fechaHechoEnOvz } from "@/features/tramites/hechoEnOvz";
import { IconoTramite } from "@/features/tramites/IconoTramite";
import type { Tramite } from "@/features/tramites/types";

const TAMANIO = 100;
const TZ = "Europe/Madrid";

type Vista = "mes" | "lista";

type EventoHecho = {
  kind: "hecho";
  id: string;
  tramite: Tramite;
  diaClave: string;
  titulo: string;
};

type EventoPlazo = {
  kind: "plazo";
  id: string;
  alerta: Alerta;
  diaClave: string;
  titulo: string;
};

type EventoCal = EventoHecho | EventoPlazo;

type Celda = {
  clave: string | null;
  label: number;
  fuera: boolean;
  esHoy: boolean;
  eventos: EventoCal[];
};

const LEYENDA: { label: string; Icono: LucideIcon; bg: string; fg: string }[] = [
  { label: "Hecho en OVZ", Icono: CheckCircle2Icon, bg: "bg-success", fg: "text-success-foreground" },
  {
    label: "Vencimiento / plazo",
    Icono: CalendarIcon,
    bg: "bg-warning",
    fg: "text-warning-foreground",
  },
  {
    label: "Cola atascada",
    Icono: ClipboardCheckIcon,
    bg: "bg-warning",
    fg: "text-warning-foreground",
  },
];

/** Calendario: hechos en OVZ + vencimientos de plazos / cola (`GET /alertas`). */
export function CalendarioPage() {
  const [hechos, setHechos] = useState<Tramite[]>([]);
  const [alertas, setAlertas] = useState<Alerta[]>([]);
  const [error, setError] = useState<ErrorApi | null>(null);
  const [cargando, setCargando] = useState(true);
  const [version, setVersion] = useState(0);
  const [vista, setVista] = useState<Vista>("mes");
  const [anioMes, setAnioMes] = useState(() => inicioMesMadrid(new Date()));
  const [selId, setSelId] = useState<string | null>(null);
  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setError(null);
    Promise.allSettled([listarTramites(0, TAMANIO, "HECHO_EN_OVZ"), listarAlertas()]).then(
      ([hechosRes, alertasRes]) => {
        if (cancelado) return;
        if (hechosRes.status === "rejected" && alertasRes.status === "rejected") {
          setHechos([]);
          setAlertas([]);
          setError(aErrorApi(hechosRes.reason));
          setCargando(false);
          return;
        }
        if (hechosRes.status === "fulfilled") {
          setHechos(hechosRes.value.content.filter((t) => t.hechoEnOvzEn));
        } else {
          setHechos([]);
        }
        if (alertasRes.status === "fulfilled") {
          setAlertas(alertasRes.value);
        } else {
          setAlertas([]);
        }
        setCargando(false);
      },
    );
    return () => {
      cancelado = true;
    };
  }, [version]);

  const eventos = useMemo(
    () => [...hechosAEventos(hechos), ...alertasAEventos(alertas)],
    [hechos, alertas],
  );
  const celdas = useMemo(() => construirCeldas(anioMes, eventos), [anioMes, eventos]);
  const tituloMes = useMemo(() => etiquetaMes(anioMes), [anioMes]);
  const seleccionado = eventos.find((e) => e.id === selId) ?? null;

  const gruposLista = useMemo(() => {
    const mapa = new Map<string, { etiqueta: string; items: EventoCal[] }>();
    for (const ev of eventos) {
      const etiqueta = etiquetaDiaLargo(ev.diaClave);
      const g = mapa.get(ev.diaClave) ?? { etiqueta, items: [] };
      g.items.push(ev);
      mapa.set(ev.diaClave, g);
    }
    return [...mapa.entries()]
      .sort((a, b) => (a[0] < b[0] ? 1 : -1))
      .map(([clave, g]) => ({ clave, ...g, n: g.items.length }));
  }, [eventos]);

  return (
    <div className="mx-auto flex w-full max-w-[1360px] min-w-0 flex-col gap-5">
      <div className="flex flex-wrap items-end justify-between gap-4 border-b-2 border-divisor pb-5">
        <div className="min-w-0">
          <p className="mb-1.5 text-[13px] font-medium text-muted-foreground">
            Hechos en OVZ, plazos legales y cola atascada
          </p>
          <h1 className="text-[28px] leading-tight font-bold tracking-[-0.02em]">Calendario</h1>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-1">
            <Button
              type="button"
              variant="outline"
              size="icon"
              className="size-9 bg-card"
              title="Mes anterior"
              onClick={() => setAnioMes((d) => sumarMeses(d, -1))}
            >
              <ChevronLeftIcon aria-hidden="true" className="size-4" />
            </Button>
            <span className="min-w-[9.5rem] text-center text-sm font-bold capitalize">{tituloMes}</span>
            <Button
              type="button"
              variant="outline"
              size="icon"
              className="size-9 bg-card"
              title="Mes siguiente"
              onClick={() => setAnioMes((d) => sumarMeses(d, 1))}
            >
              <ChevronRightIcon aria-hidden="true" className="size-4" />
            </Button>
          </div>
          <div
            role="tablist"
            aria-label="Vista del calendario"
            className="flex border border-input"
          >
            {(
              [
                { id: "mes" as const, label: "Calendario", Icono: CalendarIcon },
                { id: "lista" as const, label: "Lista", Icono: ListIcon },
              ] as const
            ).map((op, i) => {
              const activo = vista === op.id;
              return (
                <button
                  key={op.id}
                  type="button"
                  role="tab"
                  aria-selected={activo}
                  onClick={() => setVista(op.id)}
                  className={cn(
                    "flex h-9 items-center gap-1.5 px-3 text-[13px] font-medium outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring",
                    i > 0 && "border-l border-input",
                    activo
                      ? "bg-primary text-primary-foreground"
                      : "bg-card text-foreground hover:bg-gris-200",
                  )}
                >
                  <op.Icono aria-hidden="true" className="size-3.5" />
                  {op.label}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      <div className="flex flex-wrap gap-4">
        {LEYENDA.map((l) => (
          <span
            key={l.label}
            className={cn(
              "inline-flex h-6 items-center gap-1.5 px-2 text-xs font-medium",
              l.bg,
              l.fg,
            )}
          >
            <l.Icono aria-hidden="true" className="size-3.5" />
            {l.label}
          </span>
        ))}
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertTitle>No se ha podido cargar el calendario</AlertTitle>
          <AlertDescription className="flex flex-col gap-3">
            <p>{mensajeDeError(error, "listar-tramites")}</p>
            <Button type="button" variant="outline" size="sm" className="w-fit" onClick={recargar}>
              Reintentar
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {!error && (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_340px] lg:items-start">
          <div className="min-w-0">
            {vista === "mes" && (
              <div className="grid grid-cols-7 gap-px border border-border bg-border">
                {["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"].map((w) => (
                  <div
                    key={w}
                    className="bg-background px-2.5 py-2 text-[11px] font-medium tracking-[0.08em] text-muted-foreground"
                  >
                    {w}
                  </div>
                ))}
                {celdas.map((c, i) => (
                  <div
                    key={c.clave ?? `out-${i}`}
                    className={cn(
                      "flex min-h-[112px] flex-col gap-1 p-1.5",
                      c.fuera ? "bg-background" : "bg-card",
                    )}
                  >
                    <span
                      className={cn(
                        "flex h-6 w-6 items-center justify-center text-[13px] font-medium tabular-nums",
                        c.esHoy && "bg-primary font-bold text-primary-foreground",
                        c.fuera && "text-muted-foreground",
                      )}
                    >
                      {c.label}
                    </span>
                    {c.eventos.map((ev) => (
                      <button
                        key={ev.id}
                        type="button"
                        onClick={() => setSelId(ev.id)}
                        className={cn(
                          "flex items-center gap-1 truncate px-1 py-0.5 text-left text-[11px] font-medium outline-none hover:brightness-95 focus-visible:ring-2 focus-visible:ring-ring",
                          ev.kind === "hecho"
                            ? "bg-success text-success-foreground"
                            : "bg-warning text-warning-foreground",
                          selId === ev.id && "ring-2 ring-foreground",
                        )}
                      >
                        {ev.kind === "hecho" && iconoTipoTramite(ev.tramite.tipoTramite) ? (
                          <IconoTramite
                            nombre={iconoTipoTramite(ev.tramite.tipoTramite)!}
                            className="size-3 shrink-0"
                          />
                        ) : (
                          <CalendarIcon aria-hidden="true" className="size-3 shrink-0" />
                        )}
                        <span className="truncate">{ev.titulo}</span>
                      </button>
                    ))}
                  </div>
                ))}
              </div>
            )}

            {vista === "lista" && (
              <div className="flex flex-col gap-6">
                {cargando && <p className="text-sm text-muted-foreground">Cargando…</p>}
                {!cargando && gruposLista.length === 0 && (
                  <p className="text-sm text-muted-foreground">
                    No hay hechos en OVZ ni plazos con fecha en este listado.
                  </p>
                )}
                {gruposLista.map((gr) => (
                  <div key={gr.clave}>
                    <div className="mb-2 flex items-baseline gap-2 border-b-2 border-divisor pb-2">
                      <h2 className="text-lg font-bold capitalize">{gr.etiqueta}</h2>
                      <span className="text-[13px] text-muted-foreground">{gr.n}</span>
                    </div>
                    <ul>
                      {gr.items.map((ev) => (
                        <li key={ev.id}>
                          <button
                            type="button"
                            onClick={() => setSelId(ev.id)}
                            className={cn(
                              "grid w-full grid-cols-[160px_36px_minmax(0,1fr)_auto] items-center gap-3 border-b border-border px-1 py-3 text-left outline-none hover:bg-gris-200 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring",
                              selId === ev.id && "bg-gris-200",
                            )}
                          >
                            <span className="text-[13px] font-medium">
                              {ev.kind === "hecho"
                                ? (fechaHechoEnOvz(ev.tramite.hechoEnOvzEn) ?? "")
                                : ev.diaClave}
                            </span>
                            <span
                              className={cn(
                                "grid size-8 place-items-center",
                                ev.kind === "hecho"
                                  ? "bg-success text-success-foreground"
                                  : "bg-warning text-warning-foreground",
                              )}
                            >
                              {ev.kind === "hecho" && iconoTipoTramite(ev.tramite.tipoTramite) ? (
                                <IconoTramite
                                  nombre={iconoTipoTramite(ev.tramite.tipoTramite)!}
                                  className="size-4"
                                />
                              ) : ev.kind === "hecho" ? (
                                <CheckCircle2Icon aria-hidden="true" className="size-4" />
                              ) : (
                                <CalendarIcon aria-hidden="true" className="size-4" />
                              )}
                            </span>
                            <span className="min-w-0">
                              <span className="block truncate text-sm font-medium">{ev.titulo}</span>
                              <span className="block truncate text-[13px] text-muted-foreground">
                                {ev.kind === "hecho"
                                  ? `${ev.tramite.explotacionCodigoRega ?? "Sin REGA"}${
                                      ev.tramite.hechoEnOvzPorNombre
                                        ? ` · ${ev.tramite.hechoEnOvzPorNombre}`
                                        : ""
                                    }`
                                  : ev.alerta.detalle}
                              </span>
                            </span>
                            <span className="text-[13px] text-muted-foreground">
                              {ev.kind === "hecho" ? "Resuelto" : etiquetaSeveridad(ev.alerta.severidad)}
                            </span>
                          </button>
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
            )}
          </div>

          <aside className="border border-border bg-card p-5">
            {!seleccionado && (
              <p className="text-sm text-muted-foreground">
                {cargando
                  ? "Cargando…"
                  : "Selecciona un hecho en OVZ o un plazo del calendario o de la lista."}
              </p>
            )}
            {seleccionado?.kind === "hecho" && (
              <div className="flex flex-col items-start gap-3">
                <span className="inline-flex h-6 items-center gap-1.5 bg-success px-2 text-xs font-medium text-success-foreground">
                  <CheckCircle2Icon aria-hidden="true" className="size-3.5" />
                  Hecho en OVZ
                </span>
                <p className="text-xl font-bold leading-snug">{seleccionado.titulo}</p>
                <p className="text-sm font-medium">
                  {fechaHechoEnOvz(seleccionado.tramite.hechoEnOvzEn) ?? ""}
                </p>
                <div className="flex w-full flex-col gap-2 border-t border-border pt-3 text-sm">
                  <div>
                    <span className="text-muted-foreground">Explotación</span>
                    <br />
                    <span className="font-medium">
                      {seleccionado.tramite.explotacionNombre ?? "—"}
                      {seleccionado.tramite.explotacionCodigoRega
                        ? ` · ${seleccionado.tramite.explotacionCodigoRega}`
                        : ""}
                    </span>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Marcado por</span>
                    <br />
                    <span className="font-medium">
                      {seleccionado.tramite.hechoEnOvzPorNombre ?? "—"}
                    </span>
                  </div>
                </div>
                <Link
                  to="/tramites"
                  className={cn(buttonVariants(), "mt-1 h-10 w-full justify-between")}
                >
                  Abrir trámite #{seleccionado.tramite.id}
                  <ChevronRightIcon aria-hidden="true" className="size-4" />
                </Link>
              </div>
            )}
            {seleccionado?.kind === "plazo" && (
              <div className="flex flex-col items-start gap-3">
                <span className="inline-flex h-6 items-center gap-1.5 bg-warning px-2 text-xs font-medium text-warning-foreground">
                  <CalendarIcon aria-hidden="true" className="size-3.5" />
                  {etiquetaSeveridad(seleccionado.alerta.severidad)}
                </span>
                <p className="text-xl font-bold leading-snug">{seleccionado.titulo}</p>
                <p className="text-sm text-muted-foreground">{seleccionado.alerta.detalle}</p>
                {seleccionado.alerta.tramiteId != null && (
                  <Link
                    to="/tramites"
                    className={cn(buttonVariants(), "mt-1 h-10 w-full justify-between")}
                  >
                    Abrir trámite #{seleccionado.alerta.tramiteId}
                    <ChevronRightIcon aria-hidden="true" className="size-4" />
                  </Link>
                )}
                <Link to="/tramites" className={cn(CLASE_ENLACE, "text-[13px]")}>
                  Ir a la cola de trámites
                </Link>
              </div>
            )}
          </aside>
        </div>
      )}
    </div>
  );
}

function hechosAEventos(tramites: Tramite[]): EventoHecho[] {
  const out: EventoHecho[] = [];
  for (const t of tramites) {
    const iso = t.hechoEnOvzEn;
    if (!iso) continue;
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) continue;
    out.push({
      kind: "hecho",
      id: `t-${t.id}`,
      tramite: t,
      diaClave: claveDiaMadrid(d),
      titulo: etiquetaTipoTramite(t.tipoTramite ?? "") || `Trámite #${t.id}`,
    });
  }
  return out;
}

function alertasAEventos(alertas: Alerta[]): EventoPlazo[] {
  const out: EventoPlazo[] = [];
  for (const a of alertas) {
    if (!a.venceEl) continue;
    out.push({
      kind: "plazo",
      id: `a-${a.id}`,
      alerta: a,
      diaClave: a.venceEl,
      titulo: a.titulo,
    });
  }
  return out;
}

function etiquetaSeveridad(sev: string): string {
  if (sev === "critical") return "Crítico";
  if (sev === "warning") return "Aviso";
  return sev;
}

function construirCeldas(inicioMes: Date, eventos: EventoCal[]): Celda[] {
  const y = inicioMes.getFullYear();
  const m = inicioMes.getMonth();
  const primerDia = new Date(y, m, 1);
  const offset = (primerDia.getDay() + 6) % 7;
  const diasEnMes = new Date(y, m + 1, 0).getDate();
  const hoyClave = claveDiaMadrid(new Date());
  const porDia = new Map<string, EventoCal[]>();
  for (const ev of eventos) {
    const list = porDia.get(ev.diaClave) ?? [];
    list.push(ev);
    porDia.set(ev.diaClave, list);
  }
  const celdas: Celda[] = [];
  const total = 35;
  for (let i = 0; i < total; i++) {
    const diaNum = i - offset + 1;
    const fuera = diaNum < 1 || diaNum > diasEnMes;
    if (fuera) {
      const ref = new Date(y, m, diaNum);
      celdas.push({
        clave: null,
        label: ref.getDate(),
        fuera: true,
        esHoy: false,
        eventos: [],
      });
      continue;
    }
    const clave = `${y}-${String(m + 1).padStart(2, "0")}-${String(diaNum).padStart(2, "0")}`;
    celdas.push({
      clave,
      label: diaNum,
      fuera: false,
      esHoy: clave === hoyClave,
      eventos: porDia.get(clave) ?? [],
    });
  }
  return celdas;
}

function inicioMesMadrid(ref: Date): Date {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(ref);
  const y = Number(parts.find((p) => p.type === "year")?.value);
  const m = Number(parts.find((p) => p.type === "month")?.value);
  return new Date(y, m - 1, 1);
}

function sumarMeses(d: Date, n: number): Date {
  return new Date(d.getFullYear(), d.getMonth() + n, 1);
}

function etiquetaMes(d: Date): string {
  return new Intl.DateTimeFormat("es-ES", { month: "long", year: "numeric" }).format(d);
}

function claveDiaMadrid(d: Date): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: TZ,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(d);
}

function etiquetaDiaLargo(clave: string): string {
  const [y, m, day] = clave.split("-").map(Number);
  const d = new Date(y, m - 1, day, 12);
  return new Intl.DateTimeFormat("es-ES", {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
  }).format(d);
}
