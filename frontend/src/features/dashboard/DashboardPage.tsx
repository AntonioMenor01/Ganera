import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  CheckIcon,
  ChevronRightIcon,
  CircleAlertIcon,
  CompassIcon,
  EyeIcon,
  PlusIcon,
  XIcon,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { aErrorApi, mensajeDeError, type ErrorApi } from "@/shared/api/errores";
import { useAuth } from "@/shared/auth/AuthContext";
import { etiquetaTipoTramite, iconoTipoTramite, type EstadoTramite } from "@/features/tramites/etiquetas";
import { BadgeEstadoTramite } from "@/features/tramites/BadgesTramite";
import { IconoTramite } from "@/features/tramites/IconoTramite";
import { TramiteReviewDialog } from "@/features/tramites/TramiteReviewDialog";
import { AltaTramiteDialog } from "@/features/tramites/AltaTramiteDialog";
import { listarTramites } from "@/features/tramites/api";
import type { Tramite } from "@/features/tramites/types";
import { abrirPrimerosPasos } from "@/features/onboarding/primerosPasos";
import { usePrimerosPasos } from "@/features/onboarding/PrimerosPasosContexto";
import { fechaLargaMadrid, nombrePila, saludo } from "./saludo";

/** Cuántos trámites se previsualizan por columna del tablero (el contador real sale del totalElements). */
const TAMANIO_COLUMNA = 8;

type Tono = "warning" | "danger";

interface ColumnaDef {
  estado: EstadoTramite;
  titulo: string;
  descripcion: string;
  vacio: string;
  icono: LucideIcon;
  tono: Tono;
}

/**
 * Inicio = tablero del día (lo que hay que gestionar), no un panel de métricas. La auditoría de OVZ
 * (docs: «Auditoría OVZ») señala que su mayor carencia es no tener una bandeja de tareas pendientes:
 * el usuario tiene que acordarse de los trámites de dos pasos (p. ej. aprobar → meter en OVZ) sin
 * ninguna ayuda. Ganera lo resuelve poniendo en la primera pantalla, en un tablero grande, lo único
 * que de verdad importa al gestor: los trámites por revisar, los aprobados que aún faltan por pasar a
 * OVZ, y los rechazados/descartados. Cada trámite se revisa aquí mismo (sin cambiar de pantalla). No
 * se muestran métricas de vanidad (ganaderos, total gestionado): no ayudan al trabajo del día.
 */
const COLUMNAS: readonly ColumnaDef[] = [
  {
    estado: "PENDIENTE_REVISION",
    titulo: "Por revisar",
    descripcion: "Esperan tu revisión y aprobación.",
    vacio: "No hay trámites por revisar. Llegarán aquí en cuanto entren por WhatsApp.",
    icono: EyeIcon,
    tono: "warning",
  },
  {
    estado: "APROBADO",
    titulo: "Aprobado · falta OVZ",
    descripcion: "Aprobados en Ganera; falta meterlos en OVZ.net.",
    vacio: "Nada pendiente de pasar a OVZ.",
    icono: CheckIcon,
    tono: "warning",
  },
  {
    estado: "RECHAZADO",
    titulo: "Rechazados y descartados",
    descripcion: "Rechazados o descartados como mensaje sin trámite.",
    vacio: "Sin trámites rechazados ni descartados.",
    icono: XIcon,
    tono: "danger",
  },
];

type CargaColumna =
  | { estado: "cargando" }
  | { estado: "error"; error: ErrorApi }
  | { estado: "ok"; total: number; muestra: Tramite[] };

type MapaCarga = Record<string, CargaColumna>;

function inicial(): MapaCarga {
  const mapa: MapaCarga = {};
  for (const c of COLUMNAS) mapa[c.estado] = { estado: "cargando" };
  return mapa;
}

export function DashboardPage() {
  const { usuario } = useAuth();
  const navigate = useNavigate();
  const onboarding = usePrimerosPasos();
  const [datos, setDatos] = useState<MapaCarga>(inicial);
  const [version, setVersion] = useState(0);
  const [altaAbierta, setAltaAbierta] = useState(false);
  const [tramiteSeleccionado, setTramiteSeleccionado] = useState<number | null>(null);

  const recargar = useCallback(() => {
    setDatos(inicial());
    setVersion((v) => v + 1);
  }, []);

  useEffect(() => {
    let cancelado = false;
    for (const { estado } of COLUMNAS) {
      listarTramites(0, TAMANIO_COLUMNA, estado)
        .then((pagina) => {
          if (cancelado) return;
          setDatos((prev) => ({
            ...prev,
            [estado]: { estado: "ok", total: pagina.totalElements, muestra: pagina.content },
          }));
        })
        .catch((err: unknown) => {
          if (cancelado) return;
          setDatos((prev) => ({ ...prev, [estado]: { estado: "error", error: aErrorApi(err) } }));
        });
    }
    return () => {
      cancelado = true;
    };
  }, [version]);

  const nombre = nombrePila(usuario?.nombre);
  const fecha = fechaLargaMadrid();

  // Abre Control ya filtrado por ese estado (misma pantalla que gestiona todos los trámites).
  const verEnControl = useCallback(
    (filtroEstado: EstadoTramite) => navigate("/tramites", { state: { filtroEstado } }),
    [navigate],
  );

  return (
    <div className="mx-auto flex min-h-[calc(100vh-10rem)] w-full max-w-[1500px] flex-col gap-5">
      <header className="flex flex-col gap-4 border-b-2 border-divisor pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          {fecha && <p className="mb-1.5 text-[13px] font-medium text-muted-foreground">{fecha}</p>}
          <h1 className="text-[28px] leading-tight font-bold tracking-[-0.02em]">
            {nombre ? `${saludo()}, ${nombre}` : saludo()}
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">Esto es lo que tienes que gestionar hoy.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant="ghost"
            className="h-10 gap-2 px-3 text-enlace"
            onClick={abrirPrimerosPasos}
          >
            <CompassIcon aria-hidden="true" className="size-4" />
            Primeros pasos
            {onboarding.activo && (
              <span className="tabular-nums text-muted-foreground">
                · {onboarding.hechos}/{onboarding.total}
              </span>
            )}
          </Button>
          <Button type="button" className="h-10 gap-2 px-3" onClick={() => setAltaAbierta(true)}>
            <PlusIcon aria-hidden="true" className="size-4" />
            Nuevo trámite
          </Button>
        </div>
      </header>

      <section
        aria-label="Trámites por gestionar"
        className="grid flex-1 grid-cols-1 gap-5 lg:grid-cols-3"
      >
        {COLUMNAS.map((columna) => (
          <ColumnaTablero
            key={columna.estado}
            columna={columna}
            carga={datos[columna.estado]}
            onRevisar={setTramiteSeleccionado}
            onReintentar={recargar}
            onVerTodos={() => verEnControl(columna.estado)}
          />
        ))}
      </section>

      <TramiteReviewDialog
        tramiteId={tramiteSeleccionado}
        onClose={() => setTramiteSeleccionado(null)}
        onCambiado={recargar}
      />
      <AltaTramiteDialog
        open={altaAbierta}
        onOpenChange={setAltaAbierta}
        onCreado={(creado) => {
          setAltaAbierta(false);
          recargar();
          setTramiteSeleccionado(creado.id);
        }}
      />
    </div>
  );
}

function ColumnaTablero({
  columna,
  carga,
  onRevisar,
  onReintentar,
  onVerTodos,
}: {
  columna: ColumnaDef;
  carga: CargaColumna;
  onRevisar: (id: number) => void;
  onReintentar: () => void;
  onVerTodos: () => void;
}) {
  const { icono: Icono, tono } = columna;
  const total = carga.estado === "ok" ? carga.total : null;
  return (
    <section
      aria-labelledby={`col-${columna.estado}`}
      className="flex min-h-[20rem] min-w-0 flex-col border border-border bg-card"
    >
      <div className="flex items-start gap-3 border-b border-border px-5 py-4">
        <span
          className={cn(
            "mt-0.5 grid size-8 shrink-0 place-items-center",
            tono === "danger"
              ? "bg-danger/10 text-danger"
              : "bg-warning-foreground/10 text-warning-foreground",
          )}
        >
          <Icono aria-hidden="true" className="size-[18px]" />
        </span>
        <div className="min-w-0 flex-1">
          <h2 id={`col-${columna.estado}`} className="flex items-center gap-2 text-base font-bold">
            {columna.titulo}
            {total !== null && (
              <span className="inline-grid min-w-6 place-items-center bg-muted px-1.5 text-sm font-semibold tabular-nums text-foreground">
                {total}
              </span>
            )}
          </h2>
          <p className="mt-0.5 text-[13px] text-muted-foreground">{columna.descripcion}</p>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        {carga.estado === "error" ? (
          <div className="p-5">
            <Alert variant="destructive">
              <CircleAlertIcon />
              <AlertTitle>No se ha podido cargar</AlertTitle>
              <AlertDescription>
                <p>{mensajeDeError(carga.error, "listar-tramites")}</p>
                <Button variant="outline" size="sm" onClick={onReintentar} className="mt-2">
                  Reintentar
                </Button>
              </AlertDescription>
            </Alert>
          </div>
        ) : carga.estado === "cargando" ? (
          <div className="flex flex-col" aria-hidden="true">
            {[0, 1, 2].map((i) => (
              <div key={i} className="h-[72px] animate-pulse border-b border-border bg-muted/40 last:border-b-0" />
            ))}
          </div>
        ) : carga.muestra.length === 0 ? (
          <p className="px-5 py-8 text-center text-sm text-muted-foreground">{columna.vacio}</p>
        ) : (
          <ul className="flex flex-col">
            {carga.muestra.map((tramite) => (
              <li key={tramite.id} className="border-b border-border last:border-b-0">
                <TarjetaTramite tramite={tramite} onRevisar={onRevisar} />
              </li>
            ))}
          </ul>
        )}
      </div>

      {total !== null && total > 0 && (
        <div className="border-t border-border px-5 py-3">
          <button
            type="button"
            onClick={onVerTodos}
            className="text-[13px] font-medium text-enlace outline-none hover:underline focus-visible:underline focus-visible:ring-3 focus-visible:ring-ring"
          >
            {carga.estado === "ok" && total > carga.muestra.length
              ? `Ver los ${total} en Control`
              : "Ver en Control"}
          </button>
        </div>
      )}
    </section>
  );
}

function TarjetaTramite({ tramite, onRevisar }: { tramite: Tramite; onRevisar: (id: number) => void }) {
  const icono = iconoTipoTramite(tramite.tipoTramite);
  const nombreExplotacion = tramite.explotacionNombre?.trim() || undefined;
  const crotales = tramite.crotales.length;
  return (
    <button
      type="button"
      aria-label={`Revisar trámite #${tramite.id}`}
      onClick={() => onRevisar(tramite.id)}
      className="flex w-full items-center gap-3 px-5 py-3.5 text-left outline-none transition-colors hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring"
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
        <span className="mt-0.5">
          <BadgeEstadoTramite estado={tramite.estado} motivoRechazo={tramite.motivoRechazo} />
        </span>
      </span>
      <ChevronRightIcon aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
    </button>
  );
}
