import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  BellIcon,
  ChevronRightIcon,
  CircleAlertIcon,
  ClipboardListIcon,
  InboxIcon,
  UsersIcon,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Card, CardContent } from "@/components/ui/card";
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
import { cargarColaPendiente, contarGanaderos } from "./api";
import { fechaLargaMadrid, nombrePila, saludo } from "./saludo";

export function DashboardPage() {
  const { usuario } = useAuth();
  const [cola, setCola] = useState<Pagina<Tramite> | null>(null);
  const [errorCola, setErrorCola] = useState<ErrorApi | null>(null);
  const [ganaderos, setGanaderos] = useState<number | null>(null);
  const [errorGanaderos, setErrorGanaderos] = useState<boolean>(false);
  const [cargando, setCargando] = useState(true);
  const [version, setVersion] = useState(0);

  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setErrorCola(null);
    setErrorGanaderos(false);
    // allSettled: que falle el conteo de ganaderos no debe vaciar la cola, ni al revés. Cada tarjeta
    // enseña su propio valor o su propio hueco.
    Promise.allSettled([cargarColaPendiente(), contarGanaderos()]).then(([colaRes, ganaderosRes]) => {
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
      setCargando(false);
    });
    return () => {
      cancelado = true;
    };
  }, [version]);

  const nombre = nombrePila(usuario?.nombre);
  const fecha = fechaLargaMadrid();

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-1">
        {fecha && <p className="text-sm text-muted-foreground">{fecha}</p>}
        <h1 className="text-xl font-bold">
          {saludo()}
          {nombre ? `, ${nombre}` : ""}
        </h1>
      </header>

      <section aria-label="Resumen" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <TarjetaKpi
          to="/tramites"
          icono={ClipboardListIcon}
          etiqueta="Trámites pendientes"
          pie="Por revisar"
          cargando={cargando}
          error={errorCola != null}
          valor={cola?.totalElements}
        />
        <TarjetaKpi
          to="/ganaderos"
          icono={UsersIcon}
          etiqueta="Ganaderos"
          pie="Clientes de la gestoría"
          cargando={cargando}
          error={errorGanaderos}
          valor={ganaderos ?? undefined}
        />
        {/* Honestidad (PRODUCT.md): Mensajes y Alertas aún no existen como datos reales (bandeja de
            WhatsApp y alertas son fases posteriores). No se inventa un número: se dice que llegará. */}
        <TarjetaKpiProximamente
          icono={InboxIcon}
          etiqueta="Mensajes nuevos"
          nota="La bandeja de WhatsApp llegará en una próxima versión."
        />
        <TarjetaKpiProximamente
          icono={BellIcon}
          etiqueta="Alertas activas"
          nota="Los avisos de vencimientos llegarán en una próxima versión."
        />
      </section>

      <ColaDeTrabajo cola={cola} error={errorCola} cargando={cargando} onReintentar={recargar} />
    </div>
  );
}

/** KPI con dato real y enlace a su sección. Clicable entero (el `<Link>` envuelve la tarjeta). */
function TarjetaKpi({
  to,
  icono: Icono,
  etiqueta,
  pie,
  valor,
  cargando,
  error,
}: {
  to: string;
  icono: LucideIcon;
  etiqueta: string;
  pie: string;
  valor: number | undefined;
  cargando: boolean;
  error: boolean;
}) {
  return (
    <Link
      to={to}
      className="group/kpi rounded-sm outline-none focus-visible:ring-3 focus-visible:ring-ring"
    >
      <Card className="h-full transition-colors group-hover/kpi:border-gris-400">
        <CardContent className="flex flex-col gap-2">
          <span className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
            <Icono aria-hidden="true" className="size-4 shrink-0" />
            {etiqueta}
            <ChevronRightIcon
              aria-hidden="true"
              className="ml-auto size-4 shrink-0 text-muted-foreground transition-transform group-hover/kpi:translate-x-0.5 motion-reduce:transition-none"
            />
          </span>
          <span className="text-3xl font-bold tabular-nums" aria-live="polite">
            {cargando ? (
              <span aria-hidden="true" className="inline-block h-8 w-12 animate-pulse bg-muted align-middle" />
            ) : error || valor === undefined ? (
              <span title="No disponible ahora mismo">—</span>
            ) : (
              valor
            )}
          </span>
          <span className="text-sm text-muted-foreground">{pie}</span>
        </CardContent>
      </Card>
    </Link>
  );
}

/** KPI de una capacidad que todavía no existe: sin número inventado, con su explicación honesta. */
function TarjetaKpiProximamente({
  icono: Icono,
  etiqueta,
  nota,
}: {
  icono: LucideIcon;
  etiqueta: string;
  nota: string;
}) {
  return (
    <Card className="h-full">
      <CardContent className="flex flex-col gap-2">
        <span className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
          <Icono aria-hidden="true" className="size-4 shrink-0" />
          {etiqueta}
        </span>
        <span className="inline-flex w-fit items-center bg-neutral px-2 py-0.5 text-xs font-medium text-neutral-foreground">
          Próximamente
        </span>
        <span className="text-sm text-muted-foreground">{nota}</span>
      </CardContent>
    </Card>
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
    <section aria-labelledby="cola-de-trabajo-titulo" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
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
          Ver todos los trámites
        </Link>
      </div>

      {error ? (
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
      ) : cargando ? (
        <div className="flex flex-col gap-2" aria-hidden="true">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-16 animate-pulse border border-border bg-card" />
          ))}
        </div>
      ) : total === 0 ? (
        <Card>
          <CardContent className="text-sm text-muted-foreground">
            No hay trámites pendientes de revisión. En cuanto llegue un mensaje de WhatsApp y la IA lo
            procese, aparecerá aquí para que lo revises.
          </CardContent>
        </Card>
      ) : (
        <ul className="flex flex-col border border-border bg-card">
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

/** Una fila de la previa enlaza a la cola (donde se abre la revisión). No inventa el nombre del
 * ganadero (GET /tramites no lo trae): identifica el trámite por su explotación y su tipo. */
function FilaCola({ tramite }: { tramite: Tramite }) {
  const icono = iconoTipoTramite(tramite.tipoTramite);
  const nombreExplotacion = tramite.explotacionNombre?.trim() || undefined;
  const crotales = tramite.crotales.length;
  return (
    <Link
      to="/tramites"
      className="flex items-center gap-3 px-4 py-3 outline-none transition-colors hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring"
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
