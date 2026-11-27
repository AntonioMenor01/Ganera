import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import {
  CheckIcon,
  CircleAlertIcon,
  CircleCheckIcon,
  EyeIcon,
  PlusIcon,
  XIcon,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { aErrorApi, mensajeDeError, type ErrorApi } from "@/shared/api/errores";
import { listarTramites } from "./api";
import type { EstadoTramite, Tramite } from "./types";
import { TarjetaTramite } from "./TarjetaTramite";
import { AltaTramiteDialog } from "./AltaTramiteDialog";
import { TramiteReviewDialog } from "./TramiteReviewDialog";

/** Cuántos trámites se cargan por estado. La columna hace scroll por dentro; si hay más, lo avisa. */
const TAMANIO_ESTADO = 50;

type Tono = "warning" | "success" | "danger";

interface ColumnaDef {
  id: string;
  titulo: string;
  descripcion: string;
  vacio: string;
  icono: LucideIcon;
  tono: Tono;
  /** Estados que caen en esta columna (en el orden del flujo). Cubren LOS 8 sin huecos. */
  estados: readonly EstadoTramite[];
}

/**
 * Control = tablero de mando de TODOS los trámites, por fase del flujo. Rediseño (2026-11-27): se
 * quita el filtro de pestañas y la tabla; en su lugar, un tablero visual en el que, al entrar, el
 * gestor ve de un vistazo (sin scroll de página ni clics) cuántos trámites hay en cada fase y cuáles
 * son. Cada tarjeta abre la revisión en la misma pantalla. Las cuatro columnas cubren los ocho
 * estados sin perder ninguno (cada tarjeta lleva además su badge de estado real):
 *  - «Por revisar»: lo que entra y espera revisión (incluye la extracción en curso).
 *  - «Aprobado · falta OVZ»: aprobado en Ganera; aún hay que meterlo en OVZ (y los que Ganera está
 *    presentando sola o fallaron, que tampoco están cerrados).
 *  - «Hecho en OVZ»: ya está en OVZ (a mano por el gestor, o presentado por Ganera).
 *  - «Rechazados y descartados».
 * El Inicio es el tablero del DÍA (solo lo accionable); Control es el mando COMPLETO del flujo.
 */
const COLUMNAS: readonly ColumnaDef[] = [
  {
    id: "por-revisar",
    titulo: "Por revisar",
    descripcion: "Esperan tu revisión y aprobación.",
    vacio: "No hay trámites por revisar. Llegarán aquí en cuanto entren por WhatsApp.",
    icono: EyeIcon,
    tono: "warning",
    estados: ["PENDIENTE_EXTRACCION", "PENDIENTE_REVISION"],
  },
  {
    id: "aprobado",
    titulo: "Aprobado · falta OVZ",
    descripcion: "Aprobados en Ganera; mételos en OVZ.net y márcalos como hechos.",
    vacio: "Nada pendiente de pasar a OVZ.",
    icono: CheckIcon,
    tono: "warning",
    estados: ["APROBADO", "EN_PROCESO", "ERROR_OVZ"],
  },
  {
    id: "hecho-ovz",
    titulo: "Hecho en OVZ",
    descripcion: "Ya están en OVZ.net: metidos a mano o presentados por Ganera.",
    vacio: "Todavía no hay trámites hechos en OVZ.",
    icono: CircleCheckIcon,
    tono: "success",
    estados: ["HECHO_EN_OVZ", "EJECUTADO_OVZ"],
  },
  {
    id: "rechazados",
    titulo: "Rechazados y descartados",
    descripcion: "Rechazados o descartados como mensaje sin trámite.",
    vacio: "Sin trámites rechazados ni descartados.",
    icono: XIcon,
    tono: "danger",
    estados: ["RECHAZADO"],
  },
];

/** A qué columna pertenece cada estado (para desplazarse a ella al venir del Inicio). */
const COLUMNA_DE_ESTADO: Partial<Record<EstadoTramite, string>> = {};
for (const columna of COLUMNAS) {
  for (const estado of columna.estados) COLUMNA_DE_ESTADO[estado] = columna.id;
}

type CargaEstado =
  | { estado: "cargando" }
  | { estado: "error"; error: ErrorApi }
  | { estado: "ok"; total: number; muestra: Tramite[] };

type MapaEstados = Record<string, CargaEstado>;

/** Todos los estados que el tablero carga (los de todas las columnas). */
const ESTADOS_CARGADOS: readonly EstadoTramite[] = COLUMNAS.flatMap((c) => c.estados);

function inicial(): MapaEstados {
  const mapa: MapaEstados = {};
  for (const estado of ESTADOS_CARGADOS) mapa[estado] = { estado: "cargando" };
  return mapa;
}

/** Estado combinado de una columna a partir de sus estados: cargando si falta alguno, error si
 * alguno falló, y si no, el total sumado y la muestra concatenada (más reciente primero). */
interface CargaColumna {
  fase: "cargando" | "error" | "ok";
  error?: ErrorApi;
  total: number;
  /** Trámites cargados de la columna (puede ser menos que `total`). */
  muestra: Tramite[];
}

function componerColumna(columna: ColumnaDef, mapa: MapaEstados): CargaColumna {
  const cargas = columna.estados.map((e) => mapa[e] ?? { estado: "cargando" as const });
  const error = cargas.find((c) => c.estado === "error");
  if (error && error.estado === "error") return { fase: "error", error: error.error, total: 0, muestra: [] };
  if (cargas.some((c) => c.estado === "cargando")) return { fase: "cargando", total: 0, muestra: [] };
  let total = 0;
  const muestra: Tramite[] = [];
  for (const c of cargas) {
    if (c.estado === "ok") {
      total += c.total;
      muestra.push(...c.muestra);
    }
  }
  // Más reciente primero (id mayor ~ más nuevo): una columna agrupa estados, así se intercalan bien.
  muestra.sort((a, b) => b.id - a.id);
  return { fase: "ok", total, muestra };
}

export function TramitesPage() {
  const location = useLocation();
  const [datos, setDatos] = useState<MapaEstados>(inicial);
  const [version, setVersion] = useState(0);
  const [tramiteSeleccionado, setTramiteSeleccionado] = useState<number | null>(null);
  const [altaAbierta, setAltaAbierta] = useState(false);
  const columnasRef = useRef<Record<string, HTMLElement | null>>({});

  const recargar = useCallback(() => {
    setDatos(inicial());
    setVersion((v) => v + 1);
  }, []);

  // Abrir un trámite concreto (desde la campana de novedades o el alta de un ganadero/explotación),
  // o, al venir del Inicio con un estado, llevar su columna a la vista. Es la fusión con la cola:
  // cualquier enlace antiguo a /tramites sigue funcionando.
  useEffect(() => {
    const estado = location.state as
      | { abrirTramiteId?: number; filtroEstado?: EstadoTramite | "TODOS" }
      | null;
    const abrir = estado?.abrirTramiteId;
    if (typeof abrir === "number") {
      setTramiteSeleccionado(abrir);
      return;
    }
    const filtro = estado?.filtroEstado;
    const columnaId = filtro && filtro !== "TODOS" ? COLUMNA_DE_ESTADO[filtro] : undefined;
    if (columnaId) {
      // En escritorio las cuatro columnas ya se ven; en móvil (apiladas) se baja a la pedida.
      columnasRef.current[columnaId]?.scrollIntoView({ block: "start", behavior: "smooth" });
    }
  }, [location.state]);

  useEffect(() => {
    let cancelado = false;
    for (const estado of ESTADOS_CARGADOS) {
      listarTramites(0, TAMANIO_ESTADO, estado)
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

  const columnas = useMemo(
    () => COLUMNAS.map((columna) => ({ columna, carga: componerColumna(columna, datos) })),
    [datos],
  );

  // Recuento total de la cabecera: la suma de lo que ya se conoce (se va completando al cargar).
  const totalConocido = columnas.reduce(
    (suma, { carga }) => suma + (carga.fase === "ok" ? carga.total : 0),
    0,
  );
  const todasOk = columnas.every(({ carga }) => carga.fase === "ok");
  const algunError = columnas.some(({ carga }) => carga.fase === "error");
  const subtitulo = todasOk
    ? `${totalConocido} ${totalConocido === 1 ? "trámite" : "trámites"} en total`
    : algunError
      ? "—"
      : "Cargando…";

  return (
    <div className="mx-auto flex min-h-[calc(100vh-10rem)] w-full max-w-[1600px] min-w-0 flex-col gap-5">
      <header className="flex flex-wrap items-end justify-between gap-3 border-b-2 border-divisor pb-5">
        <div className="min-w-0">
          <p className="mb-1.5 text-[13px] font-medium text-muted-foreground tabular-nums">{subtitulo}</p>
          <h1 className="text-[28px] leading-tight font-bold tracking-[-0.02em]">Control</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Todos tus trámites por fase, de un vistazo. Pulsa cualquiera para revisarlo.
          </p>
        </div>
        <Button type="button" className="h-10 gap-2 px-3" onClick={() => setAltaAbierta(true)}>
          <PlusIcon aria-hidden="true" className="size-4" />
          Nuevo trámite
        </Button>
      </header>

      <section
        aria-label="Trámites por fase"
        className="grid flex-1 grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-4"
      >
        {columnas.map(({ columna, carga }) => (
          <ColumnaTablero
            key={columna.id}
            columna={columna}
            carga={carga}
            onRevisar={setTramiteSeleccionado}
            onReintentar={recargar}
            ref={(el) => {
              columnasRef.current[columna.id] = el;
            }}
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

const TONO_CABECERA: Record<Tono, string> = {
  warning: "bg-warning-foreground/10 text-warning-foreground",
  success: "bg-success/10 text-success-foreground",
  danger: "bg-danger/10 text-danger",
};

function ColumnaTablero({
  columna,
  carga,
  onRevisar,
  onReintentar,
  ref,
}: {
  columna: ColumnaDef;
  carga: CargaColumna;
  onRevisar: (id: number) => void;
  onReintentar: () => void;
  ref: (el: HTMLElement | null) => void;
}) {
  const { icono: Icono, tono } = columna;
  const total = carga.fase === "ok" ? carga.total : null;
  const ocultos = carga.fase === "ok" ? carga.total - carga.muestra.length : 0;
  return (
    <section
      ref={ref}
      aria-labelledby={`col-${columna.id}`}
      className="flex min-h-[20rem] min-w-0 flex-col border border-border bg-card xl:max-h-[calc(100vh-13rem)]"
    >
      <div className="flex items-start gap-3 border-b border-border px-5 py-4">
        <span className={cn("mt-0.5 grid size-8 shrink-0 place-items-center", TONO_CABECERA[tono])}>
          <Icono aria-hidden="true" className="size-[18px]" />
        </span>
        <div className="min-w-0 flex-1">
          <h2 id={`col-${columna.id}`} className="flex items-center gap-2 text-base font-bold">
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
        {carga.fase === "error" ? (
          <div className="p-5">
            <Alert variant="destructive">
              <CircleAlertIcon />
              <AlertTitle>No se ha podido cargar</AlertTitle>
              <AlertDescription>
                <p>{carga.error ? mensajeDeError(carga.error, "listar-tramites") : ""}</p>
                <Button variant="outline" size="sm" onClick={onReintentar} className="mt-2">
                  Reintentar
                </Button>
              </AlertDescription>
            </Alert>
          </div>
        ) : carga.fase === "cargando" ? (
          <div className="flex flex-col" aria-hidden="true">
            {[0, 1, 2].map((i) => (
              <div
                key={i}
                className="h-[72px] animate-pulse border-b border-border bg-muted/40 last:border-b-0"
              />
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

      {ocultos > 0 && (
        <p className="border-t border-border px-5 py-3 text-[13px] text-muted-foreground">
          Se muestran los {carga.muestra.length} más recientes de {total}.
        </p>
      )}
    </section>
  );
}
