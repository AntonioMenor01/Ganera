import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  BellRingIcon,
  ChevronRightIcon,
  CircleAlertIcon,
  ClockIcon,
  MessageCircleIcon,
  UserPlusIcon,
  UsersIcon,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { CLASE_ENLACE } from "@/shared/ui/enlace";
import { aErrorApi, mensajeDeError, type ErrorApi } from "@/shared/api/errores";
import type { Pagina } from "@/shared/api/types";
import { useAuth } from "@/shared/auth/AuthContext";
import { etiquetaTipoTramite, iconoTipoTramite } from "@/features/tramites/etiquetas";
import { BadgeEstadoTramite } from "@/features/tramites/BadgesTramite";
import { IconoTramite } from "@/features/tramites/IconoTramite";
import type { Tramite } from "@/features/tramites/types";
import { AltaGanaderoDialog } from "@/features/ganaderos/AltaGanaderoDialog";
import { listarAlertas, type Alerta } from "@/features/alertas/api";
import { cargarColaPendiente, contarGanaderos } from "./api";
import { fechaLargaMadrid, nombrePila, saludo } from "./saludo";

/**
 * Inicio alineado al mock «Ganera App v2»: cabecera con CTAs, franja de 4 KPIs y cola + panel
 * de alertas. Datos reales de GET /tramites, /ganaderos y /alertas.
 */
export function DashboardPage() {
  const { usuario } = useAuth();
  const navigate = useNavigate();
  const [cola, setCola] = useState<Pagina<Tramite> | null>(null);
  const [errorCola, setErrorCola] = useState<ErrorApi | null>(null);
  const [ganaderos, setGanaderos] = useState<number | null>(null);
  const [errorGanaderos, setErrorGanaderos] = useState(false);
  const [alertas, setAlertas] = useState<Alerta[] | null>(null);
  const [errorAlertas, setErrorAlertas] = useState(false);
  const [cargando, setCargando] = useState(true);
  const [version, setVersion] = useState(0);
  const [altaAbierta, setAltaAbierta] = useState(false);

  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setErrorCola(null);
    setErrorGanaderos(false);
    setErrorAlertas(false);
    Promise.allSettled([cargarColaPendiente(), contarGanaderos(), listarAlertas()]).then(
      ([colaRes, ganaderosRes, alertasRes]) => {
        if (cancelado) return;
        if (colaRes.status === "fulfilled") {
          setCola(colaRes.value);
        } else {
          setCola(null);
          setErrorCola(aErrorApi(colaRes.reason));
        }
        if (ganaderosRes.status === "fulfilled") {
          setGanaderos(ganaderosRes.value);
        } else {
          setGanaderos(null);
          setErrorGanaderos(true);
        }
        if (alertasRes.status === "fulfilled") {
          setAlertas(alertasRes.value);
        } else {
          setAlertas(null);
          setErrorAlertas(true);
        }
        setCargando(false);
      },
    );
    return () => {
      cancelado = true;
    };
  }, [version]);

  const nombre = nombrePila(usuario?.nombre);
  const fecha = fechaLargaMadrid();
  const pendientes = cola?.totalElements;
  const mensajesWhatsapp =
    cola == null ? undefined : cola.content.filter((t) => t.origen === "WHATSAPP").length;

  return (
    <div className="mx-auto flex w-full max-w-[1360px] flex-col gap-6">
      <header className="flex flex-col gap-4 border-b-2 border-divisor pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          {fecha && <p className="mb-1.5 text-[13px] font-medium text-muted-foreground">{fecha}</p>}
          <h1 className="text-[28px] leading-tight font-bold tracking-[-0.02em]">
            {nombre ? `${saludo()}, ${nombre}` : saludo()}
          </h1>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant="outline"
            className="h-10 gap-2 bg-card px-3"
            onClick={() => setAltaAbierta(true)}
          >
            <UserPlusIcon aria-hidden="true" className="size-4" />
            Añadir ganadero
          </Button>
          <Button
            type="button"
            className="h-10 gap-2 px-3"
            onClick={() => navigate("/bandeja")}
          >
            <MessageCircleIcon aria-hidden="true" className="size-4" />
            Abrir bandeja
          </Button>
        </div>
      </header>

      <section aria-label="Resumen" className="grid grid-cols-1 overflow-hidden border border-border bg-card sm:grid-cols-2 lg:grid-cols-4">
        <CeldaKpi
          to="/tramites"
          icono={ClockIcon}
          etiqueta="Trámites pendientes"
          pie={
            cargando
              ? "…"
              : pendientes === 0
                ? "Nada por revisar"
                : "Por revisar"
          }
          valor={pendientes}
          cargando={cargando}
          error={errorCola != null}
          acento={!!pendientes && pendientes > 0}
        />
        <CeldaKpi
          to="/bandeja"
          icono={MessageCircleIcon}
          etiqueta="Mensajes nuevos"
          pie={
            cargando
              ? "…"
              : mensajesWhatsapp === 0
                ? "Bandeja al día"
                : "En la cola pendiente"
          }
          valor={mensajesWhatsapp}
          cargando={cargando}
          error={errorCola != null}
        />
        <CeldaKpi
          to="/calendario"
          icono={BellRingIcon}
          etiqueta="Alertas activas"
          pie={
            errorAlertas
              ? "No se han podido cargar"
              : (alertas?.length ?? 0) === 0
                ? "Sin plazos cercanos"
                : "Plazos y cola"
          }
          valor={alertas?.length ?? undefined}
          cargando={cargando}
          error={errorAlertas}
          acento={!!alertas && alertas.length > 0}
        />
        <CeldaKpi
          to="/ganaderos"
          icono={UsersIcon}
          etiqueta="Ganaderos"
          pie="Clientes de la gestoría"
          valor={ganaderos ?? undefined}
          cargando={cargando}
          error={errorGanaderos}
        />
      </section>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <ColaDeTrabajo cola={cola} error={errorCola} cargando={cargando} onReintentar={recargar} />
        <aside className="flex flex-col gap-6">
          <section
            aria-labelledby="alertas-proximas-titulo"
            className="border border-border bg-card"
          >
            <div className="flex items-center justify-between gap-2 border-b border-border px-5 py-4">
              <h2 id="alertas-proximas-titulo" className="text-sm font-bold">
                Alertas próximas
              </h2>
              <Link
                to="/calendario"
                className={cn(CLASE_ENLACE, "text-[13px] font-medium")}
              >
                Calendario
              </Link>
            </div>
            {errorAlertas ? (
              <p className="px-5 py-4 text-sm text-muted-foreground">No se han podido cargar las alertas.</p>
            ) : !alertas || alertas.length === 0 ? (
              <p className="px-5 py-4 text-sm text-muted-foreground">
                Sin plazos cercanos ni trámites atascados.
              </p>
            ) : (
              <ul className="divide-y divide-border">
                {alertas.slice(0, 5).map((a) => (
                  <li key={a.id} className="px-5 py-3 text-sm">
                    {a.tramiteId != null ? (
                      <Link to={`/tramites?abrir=${a.tramiteId}`} className={cn(CLASE_ENLACE, "font-medium")}>
                        {a.titulo}
                      </Link>
                    ) : (
                      <p className="font-medium">{a.titulo}</p>
                    )}
                    <p className="text-muted-foreground">{a.detalle}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </aside>
      </div>

      <AltaGanaderoDialog
        open={altaAbierta}
        onOpenChange={setAltaAbierta}
        onCreado={(ganadero) => {
          setAltaAbierta(false);
          navigate(`/ganaderos/${ganadero.id}`);
        }}
      />
    </div>
  );
}

function CeldaKpi({
  to,
  icono: Icono,
  etiqueta,
  pie,
  valor,
  cargando,
  error,
  acento = false,
}: {
  to: string;
  icono: LucideIcon;
  etiqueta: string;
  pie: string;
  valor: number | undefined;
  cargando: boolean;
  error: boolean;
  acento?: boolean;
}) {
  return (
    <Link
      to={to}
      className="group/kpi flex flex-col gap-3 border-b border-border p-5 outline-none last:border-b-0 sm:border-r sm:odd:border-b sm:even:border-b sm:[&:nth-child(2)]:border-r-0 lg:border-b-0 lg:border-r lg:last:border-r-0 lg:[&:nth-child(2)]:border-r focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring"
    >
      <span className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
        <Icono aria-hidden="true" className="size-4 shrink-0" />
        {etiqueta}
        <ChevronRightIcon
          aria-hidden="true"
          className="ml-auto size-4 shrink-0 text-muted-foreground transition-transform group-hover/kpi:translate-x-0.5 motion-reduce:transition-none"
        />
      </span>
      <span
        className={cn(
          "text-[38px] leading-none font-bold tabular-nums tracking-tight",
          acento && "text-primary",
        )}
        aria-live="polite"
      >
        {cargando ? (
          <span aria-hidden="true" className="inline-block h-9 w-14 animate-pulse bg-muted align-middle" />
        ) : error || valor === undefined ? (
          <span title="No disponible ahora mismo">—</span>
        ) : (
          valor
        )}
      </span>
      <span className="text-[13px] text-muted-foreground">{pie}</span>
    </Link>
  );
}

function ColaDeTrabajo({
  cola,
  error,
  cargando,
  onReintentar,
}: {
  cola: Pagina<Tramite> | null;
  error: ErrorApi | null;
  cargando: boolean;
  onReintentar: () => void;
}) {
  const total = cola?.totalElements ?? 0;
  return (
    <section
      aria-labelledby="cola-de-trabajo-titulo"
      className="flex min-w-0 flex-col border border-border bg-card"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 border-b border-border px-5 py-4">
        <div className="flex items-baseline gap-2">
          <h2 id="cola-de-trabajo-titulo" className="text-base font-bold">
            Cola de trabajo
          </h2>
          {!cargando && !error && (
            <span className="text-sm text-muted-foreground tabular-nums">
              {total === 0 ? "sin pendientes" : `${total} por revisar`}
            </span>
          )}
        </div>
        <Link to="/tramites" className={cn(CLASE_ENLACE, "text-sm font-medium")}>
          {total > 0 ? `Ver los ${total} trámites pendientes` : "Ver todos los trámites"}
        </Link>
      </div>

      {error ? (
        <div className="p-5">
          <Alert variant="destructive">
            <CircleAlertIcon />
            <AlertTitle>No se ha podido cargar la cola</AlertTitle>
            <AlertDescription>
              <p>{mensajeDeError(error, "listar-tramites")}</p>
              <Button variant="outline" size="sm" onClick={onReintentar} className="mt-2">
                Reintentar
              </Button>
            </AlertDescription>
          </Alert>
        </div>
      ) : cargando ? (
        <div className="flex flex-col gap-0" aria-hidden="true">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-16 animate-pulse border-b border-border bg-muted/40 last:border-b-0" />
          ))}
        </div>
      ) : total === 0 ? (
        <p className="px-5 py-6 text-sm text-muted-foreground">
          No hay trámites pendientes de revisión. En cuanto llegue un mensaje de WhatsApp y la IA lo
          procese, aparecerá aquí para que lo revises.
        </p>
      ) : (
        <ul className="flex flex-col">
          {cola?.content.map((tramite) => (
            <li key={tramite.id} className="border-b border-border last:border-b-0">
              <FilaCola tramite={tramite} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function FilaCola({ tramite }: { tramite: Tramite }) {
  const icono = iconoTipoTramite(tramite.tipoTramite);
  const nombreExplotacion = tramite.explotacionNombre?.trim() || undefined;
  const crotales = tramite.crotales.length;
  return (
    <Link
      to="/tramites"
      className="flex items-center gap-3 px-5 py-3.5 outline-none transition-colors hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring"
    >
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="flex items-center gap-2 font-medium">
          {icono && <IconoTramite nombre={icono} className="size-4 shrink-0 text-muted-foreground" />}
          <span className="truncate">
            {tramite.tipoTramite ? etiquetaTipoTramite(tramite.tipoTramite) : "Sin determinar"}
          </span>
        </span>
        <span className="min-w-0 truncate text-sm text-muted-foreground">
          {tramite.explotacionCodigoRega ? (
            <>
              <span className="tabular-nums">{tramite.explotacionCodigoRega}</span>
              {nombreExplotacion && <span> · {nombreExplotacion}</span>}
            </>
          ) : (
            "Sin explotación asignada"
          )}
          {crotales > 0 && (
            <>
              {" · "}
              <span className="tabular-nums">{crotales}</span> {crotales === 1 ? "crotal" : "crotales"}
            </>
          )}
        </span>
      </span>
      <BadgeEstadoTramite estado={tramite.estado} motivoRechazo={tramite.motivoRechazo} />
      <span className="hidden text-sm font-medium text-enlace sm:inline">Revisar</span>
      <ChevronRightIcon aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
    </Link>
  );
}
