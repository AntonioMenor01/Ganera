import { useCallback, useEffect, useState } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { CLASE_ENLACE } from "@/shared/ui/enlace";
import { aErrorApi, mensajeDeError, type ErrorApi } from "@/shared/api/errores";
import type { Pagina } from "@/shared/api/types";
import { listarTramites } from "./api";
import type { EstadoTramite, Tramite, TramiteCrotal } from "./types";
import { ESTADOS_TRAMITE, etiquetaTipoTramite } from "./etiquetas";
import { BadgeEstadoTramite, BadgeResolucionCrotal } from "./BadgesTramite";
import { TramiteReviewDialog } from "./TramiteReviewDialog";

const TAMANIO_PAGINA = 20;
const COLUMNAS = 5;
const FILAS_ESQUELETO = 5;
const BARRA_ESQUELETO = "h-4 w-full max-w-28 rounded-sm bg-muted motion-safe:animate-pulse";
/** Crotales que se ven en la fila; el resto se despliega con "+N más". */
const MAX_CROTALES_EN_FILA = 2;

type FiltroEstado = EstadoTramite | "TODOS";

/** Decisión 28: la cola se abre en lo que hay que revisar. "Todos" sigue en el filtro. */
const FILTRO_INICIAL: FiltroEstado = "PENDIENTE_REVISION";

// Las etiquetas del filtro salen de ESTADOS_TRAMITE: las mismas que los badges de la tabla.
const ESTADOS: { value: FiltroEstado; label: string }[] = [
  { value: "TODOS", label: "Todos los estados" },
  ...(Object.keys(ESTADOS_TRAMITE) as EstadoTramite[]).map((estado) => ({
    value: estado,
    label: ESTADOS_TRAMITE[estado].etiqueta,
  })),
];

function textoRecuento(total: number): string {
  return `${total} ${total === 1 ? "trámite" : "trámites"}`;
}

function textoVacio(filtro: FiltroEstado): string {
  return filtro === "TODOS"
    ? "No hay trámites que mostrar."
    : `No hay trámites en «${ESTADOS_TRAMITE[filtro].etiqueta}».`;
}

export function TramitesPage() {
  const [pagina, setPagina] = useState<Pagina<Tramite> | null>(null);
  const [numeroPagina, setNumeroPagina] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState<FiltroEstado>(FILTRO_INICIAL);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState<ErrorApi | null>(null);
  // Último total conocido: si falla una página, la paginación sigue a la vista para poder salir
  // de ella (a otra página o reintentando), en vez de quedarse atascado en la que falla.
  const [totalPaginas, setTotalPaginas] = useState(0);
  const [version, setVersion] = useState(0);
  const [tramiteSeleccionado, setTramiteSeleccionado] = useState<number | null>(null);

  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setErrorCarga(null);
    listarTramites(
      numeroPagina,
      TAMANIO_PAGINA,
      filtroEstado === "TODOS" ? undefined : filtroEstado,
    )
      .then((resultado) => {
        if (cancelado) return;
        if (numeroPagina > 0 && numeroPagina >= resultado.totalPages) {
          // N2: la página pedida ya no existe (p. ej. se aprobó el último trámite de la última
          // página). Se vuelve a la última válida en vez de enseñar una tabla vacía sin paginación.
          setPagina(null);
          setTotalPaginas(resultado.totalPages);
          setNumeroPagina(Math.max(0, resultado.totalPages - 1));
          return;
        }
        setPagina(resultado);
        setTotalPaginas(resultado.totalPages);
        setCargando(false);
      })
      .catch((err: unknown) => {
        if (cancelado) return;
        // No se deja a la vista una página vieja como si fuera la respuesta actual.
        setPagina(null);
        setErrorCarga(aErrorApi(err));
        setCargando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [numeroPagina, filtroEstado, version]);

  function cambiarFiltro(valor: FiltroEstado) {
    // N1: nada del filtro anterior (filas, total de páginas) queda a la vista mientras carga.
    setFiltroEstado(valor);
    setNumeroPagina(0);
    setTotalPaginas(0);
    setPagina(null);
  }

  const subtitulo = pagina
    ? textoRecuento(pagina.totalElements)
    : errorCarga
      ? "—"
      : "Cargando…";

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold">Cola de trámites</h1>
          <p className="text-sm text-muted-foreground">{subtitulo}</p>
        </div>
        <Select value={filtroEstado} onValueChange={(value) => cambiarFiltro(value as FiltroEstado)}>
          <SelectTrigger className="w-full sm:w-56" aria-label="Filtrar por estado">
            <SelectValue>
              {(value: FiltroEstado) =>
                ESTADOS.find((estado) => estado.value === value)?.label ?? value
              }
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            {ESTADOS.map((estado) => (
              <SelectItem key={estado.value} value={estado.value}>
                {estado.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {errorCarga && (
        <Alert variant="destructive">
          <AlertTitle>No se han podido cargar los trámites</AlertTitle>
          <AlertDescription>
            <p>{mensajeDeError(errorCarga, "listar-tramites")}</p>
            <Button variant="outline" size="sm" onClick={recargar} className="mt-2">
              Reintentar
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {/* La tabla hace scroll horizontal dentro de su contenedor, nunca la página (H12). */}
      <div className="min-w-0 rounded-xl border bg-card">
        {/* WCAG 4.1.3: estado siempre montado y fuera de aria-busy; solo cambia su texto, así el
            lector de pantalla lo anuncia (uno que aparece ya lleno dentro de un contenedor ocupado
            suele perderse). */}
        <p role="status" className="sr-only">
          {cargando ? "Cargando trámites…" : ""}
        </p>
        <Table aria-busy={cargando}>
          <TableHeader>
            <TableRow>
              <TableHead>Trámite</TableHead>
              <TableHead>Tipo</TableHead>
              <TableHead>Estado</TableHead>
              <TableHead>Explotación</TableHead>
              <TableHead>Crotales</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className={cargando && pagina ? "opacity-60 transition-opacity" : undefined}>
            {/* Esqueleto como en Ganaderos: el texto de carga está en el estado de arriba. */}
            {cargando &&
              !pagina &&
              Array.from({ length: FILAS_ESQUELETO }, (_, fila) => (
                <TableRow key={`esqueleto-${fila}`} className="hover:bg-transparent">
                  {Array.from({ length: COLUMNAS }, (_, columna) => (
                    <TableCell key={columna}>
                      <div aria-hidden="true" className={BARRA_ESQUELETO} />
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            {pagina?.content.length === 0 && !cargando && !errorCarga && (
              <TableRow>
                <TableCell colSpan={COLUMNAS} className="text-center text-muted-foreground">
                  {textoVacio(filtroEstado)}
                </TableCell>
              </TableRow>
            )}
            {pagina?.content.map((tramite) => (
              <TableRow
                key={tramite.id}
                className="cursor-pointer has-focus-visible:bg-muted/50"
                // Comodidad de ratón: toda la fila abre. El acceso real (teclado, lector de
                // pantalla) es el botón de la primera celda.
                onClick={() => setTramiteSeleccionado(tramite.id)}
              >
                <TableCell>
                  <button
                    type="button"
                    aria-label={`Revisar trámite #${tramite.id}`}
                    onClick={(evento) => {
                      evento.stopPropagation();
                      setTramiteSeleccionado(tramite.id);
                    }}
                    className={cn("-mx-1 px-1 font-medium tabular-nums", CLASE_ENLACE)}
                  >
                    #{tramite.id}
                  </button>
                </TableCell>
                <TableCell>
                  {tramite.tipoTramite ? (
                    etiquetaTipoTramite(tramite.tipoTramite)
                  ) : (
                    <span className="text-muted-foreground">Sin determinar</span>
                  )}
                </TableCell>
                <TableCell>
                  <BadgeEstadoTramite estado={tramite.estado} />
                </TableCell>
                <TableCell>
                  <CeldaExplotacion tramite={tramite} />
                </TableCell>
                <TableCell>
                  <CeldaCrotales crotales={tramite.crotales} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {totalPaginas > 1 && (
        <nav aria-label="Paginación" className="flex items-center justify-between gap-3">
          <Button
            variant="outline"
            size="sm"
            disabled={numeroPagina === 0}
            onClick={() => setNumeroPagina((p) => p - 1)}
          >
            Anterior
          </Button>
          <span className="text-sm text-muted-foreground tabular-nums">
            Página {numeroPagina + 1} de {totalPaginas}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={numeroPagina + 1 >= totalPaginas}
            onClick={() => setNumeroPagina((p) => p + 1)}
          >
            Siguiente
          </Button>
        </nav>
      )}

      <TramiteReviewDialog
        tramiteId={tramiteSeleccionado}
        onClose={() => setTramiteSeleccionado(null)}
        onCambiado={recargar}
      />
    </div>
  );
}

/** Columna Explotación (punto 4): el código REGA llega en cada fila de GET /tramites; nunca el id. */
function CeldaExplotacion({ tramite }: { tramite: Tramite }) {
  const { explotacionId, explotacionCodigoRega: codigoRega, explotacionNombre } = tramite;
  if (codigoRega) {
    const nombre = explotacionNombre?.trim() || undefined;
    return (
      <>
        <span className="tabular-nums" title={nombre}>
          {codigoRega}
        </span>
        {/* M3: el nombre no puede vivir solo en title (ni teclado ni táctil lo muestran). */}
        {nombre && <span className="sr-only">{`, ${nombre}`}</span>}
      </>
    );
  }
  if (explotacionId === null || explotacionId === undefined) {
    return <span className="text-muted-foreground">Sin asignar</span>;
  }
  // No debería pasar (el backend da el REGA siempre que hay explotación). "Sin asignar" sería falso:
  // el trámite sí tiene explotación; se dice que el código no está disponible, sin enseñar el id.
  return (
    <span className="text-muted-foreground">
      <span aria-hidden>—</span>
      <span className="sr-only">Código REGA no disponible</span>
    </span>
  );
}

function ItemCrotal({ crotal }: { crotal: TramiteCrotal }) {
  const indicado =
    crotal.crotal !== crotal.crotalIndicado ? `Indicado: ${crotal.crotalIndicado}` : null;
  return (
    <li className="flex items-center gap-1.5">
      <span className="tabular-nums" title={indicado ?? undefined}>
        {crotal.crotal || crotal.crotalIndicado}
      </span>
      {/* M3: lo que el title dice a quien usa ratón, también para lector de pantalla. */}
      {indicado && <span className="sr-only">{`, ${indicado}`}</span>}
      <BadgeResolucionCrotal crotal={crotal} />
    </li>
  );
}

function CeldaCrotales({ crotales }: { crotales: TramiteCrotal[] | undefined }) {
  // Por fila: las filas llevan key por id, así que cambiar de página lo vuelve a plegar.
  const [desplegado, setDesplegado] = useState(false);
  const lista = crotales ?? [];
  if (lista.length === 0) return <span className="text-muted-foreground">Sin crotales</span>;
  const resto = lista.length - MAX_CROTALES_EN_FILA;
  const visibles = desplegado ? lista : lista.slice(0, MAX_CROTALES_EN_FILA);
  return (
    <ul className={cn("flex items-center gap-3", desplegado && "flex-wrap")}>
      {visibles.map((crotal, indice) => (
        <ItemCrotal key={`${indice}-${crotal.crotalIndicado}`} crotal={crotal} />
      ))}
      {resto > 0 && (
        <li>
          {/* Task 6 M3: un title no se ve con el dedo ni con el teclado; esto sí recibe foco. */}
          <button
            type="button"
            aria-expanded={desplegado}
            onClick={(evento) => {
              // Desplegar no abre la revisión del trámite (la fila entera sí lo hace).
              evento.stopPropagation();
              setDesplegado((d) => !d);
            }}
            className="rounded-sm text-muted-foreground underline-offset-4 outline-none hover:underline focus-visible:underline focus-visible:ring-3 focus-visible:ring-ring/50"
          >
            {desplegado ? "Ver menos" : `+${resto} más`}
          </button>
        </li>
      )}
    </ul>
  );
}
