import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { CircleAlertIcon } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { CLASE_ENLACE, CLASE_ENLACE_TABLA } from "@/shared/ui/enlace";
import { useConsultaAncho } from "@/shared/layout/useConsultaAncho";
import { aErrorApi, mensajeDeError } from "@/shared/api/errores";
import { listarTramites } from "./api";
import type { EstadoTramite, Tramite } from "./types";
import { etiquetaTipoTramite, iconoTipoTramite } from "./etiquetas";
import { BadgeEstadoTramite } from "./BadgesTramite";
import { IconoTramite } from "./IconoTramite";
import { TramiteReviewDialog } from "./TramiteReviewDialog";

/** `xl` (80rem): igual que TramitesPage, tabla por encima y tarjetas-fila por debajo. */
const CONSULTA_TABLA = "(min-width: 80rem)";
/** Filas de muestra por grupo (el conteo real sale del totalElements, no de contar estas). */
const MUESTRA_POR_GRUPO = 4;
/** Se pide una página pequeña por estado: suficiente para la muestra del grupo, sin traer de más. */
const TAMANO_PETICION = 5;

/**
 * Grupos del "Control de trámites": bloques con sentido para el gestor, cada uno sobre uno o varios
 * EstadoTramite REALES (etiquetas.ts). No se inventan estados. El badge de cada fila usa
 * `presentacionEstadoTramite` (vía BadgeEstadoTramite), así un RECHAZADO + SIN_TRAMITE se ve
 * "Descartado" igual que en el resto de la app.
 */
interface GrupoControl {
  id: string;
  titulo: string;
  descripcion: string;
  estados: readonly EstadoTramite[];
}

const GRUPOS: readonly GrupoControl[] = [
  {
    id: "entrando",
    titulo: "Entrando",
    descripcion: "La IA está leyendo el mensaje de WhatsApp.",
    estados: ["PENDIENTE_EXTRACCION"],
  },
  {
    id: "por-revisar",
    titulo: "Por revisar",
    descripcion: "Esperan a que los revises y los apruebes.",
    estados: ["PENDIENTE_REVISION"],
  },
  {
    id: "falta-ovz",
    titulo: "Revisado · solo falta OVZ",
    descripcion: "Aprobados en Ganera; falta meterlos en OVZ.net.",
    estados: ["APROBADO"],
  },
  {
    id: "en-ovz",
    titulo: "Presentándose / presentado en OVZ",
    descripcion: "Ganera los está presentando o ya los presentó en OVZ.net.",
    estados: ["EN_PROCESO", "EJECUTADO_OVZ"],
  },
  {
    id: "hecho",
    titulo: "Hecho",
    descripcion: "Marcados como hechos en OVZ.net.",
    estados: ["HECHO_EN_OVZ"],
  },
  {
    id: "incidencias",
    titulo: "Con incidencia / rechazados",
    descripcion: "Con error al presentar, rechazados o descartados.",
    estados: ["ERROR_OVZ", "RECHAZADO"],
  },
];

/** Todos los estados que hay que consultar (una petición por estado). */
const ESTADOS: readonly EstadoTramite[] = GRUPOS.flatMap((g) => g.estados);

type CargaEstado =
  | { estado: "cargando" }
  | { estado: "error" }
  | { estado: "ok"; total: number; muestra: Tramite[] };

type MapaCarga = Record<string, CargaEstado>;

function inicial(): MapaCarga {
  const mapa: MapaCarga = {};
  for (const e of ESTADOS) mapa[e] = { estado: "cargando" };
  return mapa;
}

export function ControlTramitesPage() {
  const [datos, setDatos] = useState<MapaCarga>(inicial);
  const [version, setVersion] = useState(0);
  const [tramiteSeleccionado, setTramiteSeleccionado] = useState<number | null>(null);
  const conTabla = useConsultaAncho(CONSULTA_TABLA);

  const recargar = useCallback(() => {
    setDatos(inicial());
    setVersion((v) => v + 1);
  }, []);

  useEffect(() => {
    let cancelado = false;
    for (const estado of ESTADOS) {
      listarTramites(0, TAMANO_PETICION, estado)
        .then((pagina) => {
          if (cancelado) return;
          setDatos((prev) => ({
            ...prev,
            [estado]: { estado: "ok", total: pagina.totalElements, muestra: pagina.content },
          }));
        })
        .catch((err: unknown) => {
          if (cancelado) return;
          // Se registra como error de ese estado; el grupo que lo contenga ofrece "Reintentar".
          void aErrorApi(err);
          setDatos((prev) => ({ ...prev, [estado]: { estado: "error" } }));
        });
    }
    return () => {
      cancelado = true;
    };
  }, [version]);

  // Total general (solo de los estados ya cargados bien): honesto, sin inventar.
  const totalConocido = useMemo(
    () =>
      ESTADOS.reduce((suma, e) => {
        const d = datos[e];
        return d.estado === "ok" ? suma + d.total : suma;
      }, 0),
    [datos],
  );
  const todoCargado = ESTADOS.every((e) => datos[e].estado !== "cargando");

  return (
    <div className="mx-auto flex w-full max-w-[1360px] min-w-0 flex-col gap-6">
      <header className="flex flex-wrap items-end justify-between gap-3 border-b-2 border-divisor pb-5">
        <div className="min-w-0">
          <p className="mb-1.5 text-[13px] font-medium text-muted-foreground">
            {todoCargado ? `${totalConocido} ${totalConocido === 1 ? "trámite" : "trámites"} en total` : "Cargando…"}
          </p>
          <h1 className="text-[28px] leading-tight font-bold tracking-[-0.02em]">Control de trámites</h1>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Link to="/tramites" className={cn(CLASE_ENLACE, "text-sm font-medium")}>
            Ir a la cola de trabajo
          </Link>
          <Button type="button" variant="outline" onClick={recargar}>
            Actualizar
          </Button>
        </div>
      </header>

      <div className="grid gap-5 lg:grid-cols-2">
        {GRUPOS.map((grupo) => (
          <GrupoSeccion
            key={grupo.id}
            grupo={grupo}
            datos={datos}
            conTabla={conTabla}
            onReintentar={recargar}
            onRevisar={setTramiteSeleccionado}
          />
        ))}
      </div>

      <TramiteReviewDialog
        tramiteId={tramiteSeleccionado}
        onClose={() => setTramiteSeleccionado(null)}
        onCambiado={recargar}
      />
    </div>
  );
}

function GrupoSeccion({
  grupo,
  datos,
  conTabla,
  onReintentar,
  onRevisar,
}: {
  grupo: GrupoControl;
  datos: MapaCarga;
  conTabla: boolean;
  onReintentar: () => void;
  onRevisar: (id: number) => void;
}) {
  const cargas = grupo.estados.map((e) => datos[e]);
  const cargando = cargas.some((c) => c.estado === "cargando");
  const hayError = cargas.some((c) => c.estado === "error");
  const total = cargas.reduce((s, c) => (c.estado === "ok" ? s + c.total : s), 0);
  // Muestra combinada de los estados del grupo, más recientes primero (id desc), hasta el tope.
  const muestra = cargas
    .flatMap((c) => (c.estado === "ok" ? c.muestra : []))
    .sort((a, b) => b.id - a.id)
    .slice(0, MUESTRA_POR_GRUPO);

  return (
    <section
      aria-labelledby={`grupo-${grupo.id}`}
      className="flex min-w-0 flex-col border border-border bg-card"
    >
      <div className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
        <div className="min-w-0">
          <h2 id={`grupo-${grupo.id}`} className="flex items-center gap-2 text-base font-bold">
            {grupo.titulo}
            {!cargando && !hayError && (
              <span className="inline-grid min-w-6 place-items-center bg-muted px-1.5 text-sm font-semibold tabular-nums text-foreground">
                {total}
              </span>
            )}
          </h2>
          <p className="mt-0.5 text-[13px] text-muted-foreground">{grupo.descripcion}</p>
        </div>
      </div>

      {hayError ? (
        <div className="p-5">
          <Alert variant="destructive">
            <CircleAlertIcon />
            <AlertTitle>No se ha podido cargar este grupo</AlertTitle>
            <AlertDescription>
              <p>{mensajeDeError(aErrorApi(new Error()), "listar-tramites")}</p>
              <Button variant="outline" size="sm" onClick={onReintentar} className="mt-2">
                Reintentar
              </Button>
            </AlertDescription>
          </Alert>
        </div>
      ) : cargando ? (
        <div aria-hidden="true" className="flex flex-col divide-y divide-border">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-14 motion-safe:animate-pulse bg-muted/40" />
          ))}
        </div>
      ) : muestra.length === 0 ? (
        <p className="px-5 py-6 text-sm text-muted-foreground">No hay trámites en este grupo.</p>
      ) : conTabla ? (
        <TablaGrupo muestra={muestra} onRevisar={onRevisar} />
      ) : (
        <ListaGrupo muestra={muestra} onRevisar={onRevisar} />
      )}

      {!cargando && !hayError && total > muestra.length && (
        <div className="border-t border-border px-5 py-3">
          <Link to="/tramites" className={cn(CLASE_ENLACE, "text-[13px] font-medium")}>
            Ver los {total} en Trámites
          </Link>
        </div>
      )}
    </section>
  );
}

/** Desde xl: tabla compacta (#, Tipo, Estado, Explotación). Cada fila abre la revisión existente. */
function TablaGrupo({ muestra, onRevisar }: { muestra: Tramite[]; onRevisar: (id: number) => void }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-16">Trámite</TableHead>
          <TableHead>Tipo</TableHead>
          <TableHead>Estado</TableHead>
          <TableHead>Explotación</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {muestra.map((t) => (
          <TableRow
            key={t.id}
            className="h-12 cursor-pointer has-focus-visible:bg-muted/50"
            onClick={() => onRevisar(t.id)}
          >
            <TableCell>
              <BotonRevisar id={t.id} onRevisar={onRevisar} />
            </TableCell>
            <TableCell>
              <Tipo tipo={t.tipoTramite} />
            </TableCell>
            <TableCell>
              <BadgeEstadoTramite estado={t.estado} motivoRechazo={t.motivoRechazo} />
            </TableCell>
            <TableCell>
              <Explotacion tramite={t} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/** Por debajo de xl: tarjetas-fila con los mismos datos y el mismo acceso (#N abre la revisión). */
function ListaGrupo({ muestra, onRevisar }: { muestra: Tramite[]; onRevisar: (id: number) => void }) {
  return (
    <ul className="divide-y divide-border text-sm">
      {muestra.map((t) => (
        <li
          key={t.id}
          className="flex min-w-0 cursor-pointer flex-col gap-1.5 px-5 py-3 transition-colors hover:bg-foreground/4 has-focus-visible:bg-muted/50"
          onClick={() => onRevisar(t.id)}
        >
          <div className="flex items-center justify-between gap-3">
            <BotonRevisar id={t.id} onRevisar={onRevisar} />
            <BadgeEstadoTramite estado={t.estado} motivoRechazo={t.motivoRechazo} />
          </div>
          <Tipo tipo={t.tipoTramite} />
          <Explotacion tramite={t} />
        </li>
      ))}
    </ul>
  );
}

function BotonRevisar({ id, onRevisar }: { id: number; onRevisar: (id: number) => void }) {
  return (
    <button
      type="button"
      aria-label={`Revisar trámite #${id}`}
      onClick={(e) => {
        e.stopPropagation();
        onRevisar(id);
      }}
      className={cn("-mx-1 px-1 font-medium tabular-nums", CLASE_ENLACE_TABLA)}
    >
      #{id}
    </button>
  );
}

function Tipo({ tipo }: { tipo: string | null | undefined }) {
  if (!tipo) return <span className="text-muted-foreground">Sin determinar</span>;
  const icono = iconoTipoTramite(tipo);
  return (
    <span className="inline-flex items-center gap-2">
      {icono && <IconoTramite nombre={icono} className="size-4 shrink-0 text-muted-foreground" />}
      <span className="font-medium">{etiquetaTipoTramite(tipo)}</span>
    </span>
  );
}

function Explotacion({ tramite }: { tramite: Tramite }) {
  const { explotacionCodigoRega: rega, explotacionNombre } = tramite;
  if (!rega) return <span className="text-muted-foreground">Sin asignar</span>;
  const nombre = explotacionNombre?.trim() || undefined;
  return (
    <span className="min-w-0">
      <span className="tabular-nums" title={nombre}>
        {rega}
      </span>
      {nombre && <span className="sr-only">{`, ${nombre}`}</span>}
    </span>
  );
}
