import { useCallback, useEffect, useState } from "react";
import { CircleAlertIcon, MessageCircleIcon, TriangleAlertIcon } from "lucide-react";
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
import { CLASE_ENLACE_TABLA } from "@/shared/ui/enlace";
import { useConsultaAncho } from "@/shared/layout/useConsultaAncho";
import { aErrorApi, mensajeDeError, type ErrorApi } from "@/shared/api/errores";
import type { Pagina } from "@/shared/api/types";
import { listarTramites } from "./api";
import type { EstadoTramite, Tramite, TramiteCrotal } from "./types";
import {
  ESTADOS_TRAMITE,
  avisosExtraccion,
  etiquetaOrigen,
  etiquetaTipoTramite,
  iconoTipoTramite,
} from "./etiquetas";
import { BadgeEstadoTramite, BadgeResolucionCrotal } from "./BadgesTramite";
import { IconoTramite } from "./IconoTramite";
import { TramiteReviewDialog } from "./TramiteReviewDialog";

const TAMANIO_PAGINA = 20;
const COLUMNAS = 5;
const FILAS_ESQUELETO = 5;
const BARRA_ESQUELETO = "h-4 w-full max-w-28 rounded-sm bg-muted motion-safe:animate-pulse";

/** `xl` de Tailwind v4 (80rem = 1280 px): por debajo, tarjetas-fila en vez de tabla. A 1024 la tabla
 * con 1 crotal no cabe (869 px en 718: la barra lateral y el margen quitan 304); a 1280 sí (974). */
const CONSULTA_TABLA = "(min-width: 80rem)";
/** 90rem = 1440 px: desde aquí la fila lleva 2 crotales; por debajo, 1 (así la tabla cabe a 1280 px). */
const CONSULTA_DOS_CROTALES = "(min-width: 90rem)";

type FiltroEstado = EstadoTramite | "TODOS";

/** Decisión 28: la cola se abre en lo que hay que revisar. "Todos" sigue en el filtro. */
const FILTRO_INICIAL: FiltroEstado = "PENDIENTE_REVISION";

/** Estados que se pueden filtrar (Hecho en OVZ, D5): sin EN_PROCESO/EJECUTADO_OVZ/ERROR_OVZ, que son
 * de la automatización (3c) y hoy no se dan. Siguen en ESTADOS_TRAMITE para etiquetarlos si llegan. */
const ESTADOS_FILTRABLES: readonly EstadoTramite[] = [
  "PENDIENTE_EXTRACCION",
  "PENDIENTE_REVISION",
  "APROBADO",
  "HECHO_EN_OVZ",
  "RECHAZADO",
];

// Las etiquetas del filtro salen de ESTADOS_TRAMITE: las mismas que los badges de la tabla.
const ESTADOS: { value: FiltroEstado; label: string }[] = [
  { value: "TODOS", label: "Todos los estados" },
  ...ESTADOS_FILTRABLES.map((estado) => ({
    value: estado,
    label: ESTADOS_TRAMITE[estado].etiqueta,
  })),
];

/** Vacío con frase propia; el resto, «No hay trámites en «{etiqueta}».». */
const TEXTOS_VACIO: Partial<Record<EstadoTramite, string>> = {
  APROBADO: "No hay trámites aprobados pendientes de hacer en OVZ.",
  HECHO_EN_OVZ: "No hay trámites marcados como hechos en OVZ.",
};

function textoRecuento(total: number): string {
  return `${total} ${total === 1 ? "trámite" : "trámites"}`;
}

function textoVacio(filtro: FiltroEstado): string {
  if (filtro === "TODOS") return "No hay trámites que mostrar.";
  return TEXTOS_VACIO[filtro] ?? `No hay trámites en «${ESTADOS_TRAMITE[filtro].etiqueta}».`;
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

  // Se decide en JS y no con clases: cambia la ESTRUCTURA (tabla o lista, cuántos crotales), y así
  // el DOM tiene un solo marcado, sin botones «Revisar trámite #N» duplicados para el lector.
  const conTabla = useConsultaAncho(CONSULTA_TABLA);
  const maxCrotales = useConsultaAncho(CONSULTA_DOS_CROTALES) ? 2 : 1;
  // Solo cuando la carga terminó bien y no hay filas (ni durante la carga ni con error).
  const vacio =
    pagina?.content.length === 0 && !cargando && !errorCarga ? textoVacio(filtroEstado) : null;

  const subtitulo = pagina
    ? textoRecuento(pagina.totalElements)
    : errorCarga
      ? "—"
      : "Cargando…";

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3 border-b-2 border-divisor pb-4">
        <div className="min-w-0">
          <h1 className="text-xl font-bold">Cola de trámites</h1>
          <p className="text-sm text-muted-foreground">{subtitulo}</p>
        </div>
        <Select value={filtroEstado} onValueChange={(value) => cambiarFiltro(value as FiltroEstado)}>
          <SelectTrigger className="w-full data-[size=default]:h-[38px] sm:w-56" aria-label="Filtrar por estado">
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
          <CircleAlertIcon />
          <AlertTitle>No se han podido cargar los trámites</AlertTitle>
          <AlertDescription>
            <p>{mensajeDeError(errorCarga, "listar-tramites")}</p>
            <Button variant="outline" size="sm" onClick={recargar} className="mt-2">
              Reintentar
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {/* Por debajo de xl, tarjetas-fila; desde xl, la tabla, que si no cabe hace scroll horizontal
          dentro de su contenedor, nunca la página (H12). */}
      <div className="min-w-0 border border-border bg-card">
        {/* WCAG 4.1.3: estado siempre montado y fuera de aria-busy; solo cambia su texto, así el
            lector de pantalla lo anuncia (uno que aparece ya lleno dentro de un contenedor ocupado
            suele perderse). */}
        <p role="status" className="sr-only">
          {cargando ? "Cargando trámites…" : ""}
        </p>
        {conTabla ? (
          <TablaTramites
            pagina={pagina}
            cargando={cargando}
            textoVacio={vacio}
            maxCrotales={maxCrotales}
            onRevisar={setTramiteSeleccionado}
          />
        ) : (
          <ListaTramites
            pagina={pagina}
            cargando={cargando}
            textoVacio={vacio}
            maxCrotales={maxCrotales}
            onRevisar={setTramiteSeleccionado}
          />
        )}
      </div>

      {totalPaginas > 1 && (
        <nav aria-label="Paginación" className="flex items-center justify-between gap-3">
          <Button
            variant="outline"
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

interface VistaColaProps {
  pagina: Pagina<Tramite> | null;
  cargando: boolean;
  /** Frase del vacío, o null si no toca enseñarla. */
  textoVacio: string | null;
  maxCrotales: number;
  onRevisar: (id: number) => void;
}

/** Desde xl. Las celdas son los mismos componentes que las tarjetas de `ListaTramites`. */
function TablaTramites({ pagina, cargando, textoVacio, maxCrotales, onRevisar }: VistaColaProps) {
  return (
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
            <TableRow key={`esqueleto-${fila}`} className="h-14 hover:bg-transparent">
              {Array.from({ length: COLUMNAS }, (_, columna) => (
                <TableCell key={columna}>
                  <div aria-hidden="true" className={BARRA_ESQUELETO} />
                </TableCell>
              ))}
            </TableRow>
          ))}
        {textoVacio && (
          <TableRow>
            <TableCell colSpan={COLUMNAS} className="text-center text-muted-foreground">
              {textoVacio}
            </TableCell>
          </TableRow>
        )}
        {pagina?.content.map((tramite) => (
          <TableRow
            key={tramite.id}
            className="h-14 cursor-pointer has-focus-visible:bg-muted/50"
            // Comodidad de ratón: toda la fila abre. El acceso real (teclado, lector de
            // pantalla) es el botón de la primera celda.
            onClick={() => onRevisar(tramite.id)}
          >
            <TableCell>
              <BotonRevisar id={tramite.id} onRevisar={onRevisar} />
              {/* Fuera del botón: su nombre accesible sigue siendo "Revisar trámite #N". */}
              <IndicadorOrigen origen={tramite.origen} />
            </TableCell>
            <TableCell>
              <Tipo tipo={tramite.tipoTramite} />
            </TableCell>
            <TableCell>
              <BadgeEstadoTramite estado={tramite.estado} motivoRechazo={tramite.motivoRechazo} />
              <LineaExtraccion tramite={tramite} />
            </TableCell>
            <TableCell>
              <CeldaExplotacion tramite={tramite} />
            </TableCell>
            <TableCell>
              <CeldaCrotales crotales={tramite.crotales} maximo={maxCrotales} />
              <LineaDescartados tramite={tramite} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/** Por debajo de xl: una tarjeta-fila por trámite, con los mismos datos y el mismo orden de
 * tabulación que la fila de la tabla (#N y, si lo hay, «+N más»). */
function ListaTramites({ pagina, cargando, textoVacio, maxCrotales, onRevisar }: VistaColaProps) {
  return (
    <ul
      aria-label="Trámites"
      aria-busy={cargando}
      className={cn("divide-y divide-border text-sm", cargando && pagina && "opacity-60 transition-opacity")}
    >
      {cargando &&
        !pagina &&
        Array.from({ length: FILAS_ESQUELETO }, (_, fila) => (
          // aria-hidden: no se anuncian 5 elementos vacíos; la carga la dice el role="status".
          <li key={`esqueleto-${fila}`} aria-hidden="true" className="flex flex-col gap-2 px-3 py-3">
            <div aria-hidden="true" className={BARRA_ESQUELETO} />
            <div aria-hidden="true" className={BARRA_ESQUELETO} />
          </li>
        ))}
      {textoVacio && <li className="px-3 py-4 text-center text-muted-foreground">{textoVacio}</li>}
      {pagina?.content.map((tramite) => (
        <li
          key={tramite.id}
          className="flex min-w-0 cursor-pointer flex-col gap-1.5 px-3 py-3 transition-colors hover:bg-foreground/4 has-focus-visible:bg-muted/50 has-aria-expanded:bg-muted/50"
          // Como la fila: toda la tarjeta abre; el acceso real es el botón #N.
          onClick={() => onRevisar(tramite.id)}
        >
          <div className="flex items-start justify-between gap-3">
            <div className="shrink-0">
              <BotonRevisar id={tramite.id} onRevisar={onRevisar} />
              <IndicadorOrigen origen={tramite.origen} />
            </div>
            <div className="flex min-w-0 flex-col items-end text-right">
              <BadgeEstadoTramite estado={tramite.estado} motivoRechazo={tramite.motivoRechazo} />
              <LineaExtraccion tramite={tramite} />
            </div>
          </div>
          <div>
            <Tipo tipo={tramite.tipoTramite} />
          </div>
          <ExplotacionTarjeta tramite={tramite} />
          <div>
            <CeldaCrotales crotales={tramite.crotales} maximo={maxCrotales} cortarLinea />
            <LineaDescartados tramite={tramite} />
          </div>
        </li>
      ))}
    </ul>
  );
}

/** El #N de la fila o de la tarjeta: abre la revisión (la fila o tarjeta entera también, con ratón). */
function BotonRevisar({ id, onRevisar }: { id: number; onRevisar: (id: number) => void }) {
  return (
    <button
      type="button"
      aria-label={`Revisar trámite #${id}`}
      onClick={(evento) => {
        evento.stopPropagation();
        onRevisar(id);
      }}
      className={cn("-mx-1 px-1 font-medium tabular-nums", CLASE_ENLACE_TABLA)}
    >
      #{id}
    </button>
  );
}

function Tipo({ tipo }: { tipo: string | null | undefined }) {
  return tipo ? <CeldaTipo tipo={tipo} /> : <span className="text-muted-foreground">Sin determinar</span>;
}

/** En la tarjeta, «REGA · nombre» a la vista (con el dedo no hay title); el nombre se recorta con
 * «…» si no cabe y lleva title para el ratón. Sin REGA, lo mismo que la celda de la tabla. */
function ExplotacionTarjeta({ tramite }: { tramite: Tramite }) {
  const { explotacionCodigoRega: codigoRega, explotacionNombre } = tramite;
  if (!codigoRega) {
    return (
      <p>
        <CeldaExplotacion tramite={tramite} />
      </p>
    );
  }
  const nombre = explotacionNombre?.trim() || undefined;
  return (
    <p className="flex min-w-0">
      <span className="shrink-0 tabular-nums">{codigoRega}</span>
      {nombre && (
        <>
          {/* El «·» es solo visual; el lector oye una pausa, como con el sr-only de la tabla. */}
          <span aria-hidden="true" className="shrink-0 whitespace-pre text-muted-foreground">
            {" · "}
          </span>
          <span className="sr-only">, </span>
          <span className="truncate" title={nombre}>
            {nombre}
          </span>
        </>
      )}
    </p>
  );
}

/** Columna Tipo (diseño de Álvaro, fase 1): icono decorativo de 16 px en Gris 700 (sin color de
 * estado) + la etiqueta en 500. Un tipo desconocido sale tal cual, sin icono. */
function CeldaTipo({ tipo }: { tipo: string }) {
  const icono = iconoTipoTramite(tipo);
  return (
    <span className="inline-flex items-center gap-2">
      {icono && <IconoTramite nombre={icono} className="size-4 shrink-0 text-muted-foreground" />}
      <span className="font-medium">{etiquetaTipoTramite(tipo)}</span>
    </span>
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

function ItemCrotal({ crotal, cortarLinea }: { crotal: TramiteCrotal; cortarLinea: boolean }) {
  const indicado =
    crotal.crotal !== crotal.crotalIndicado ? `Indicado: ${crotal.crotalIndicado}` : null;
  return (
    <li className={cn("flex items-center gap-1.5", cortarLinea && "flex-wrap gap-y-1")}>
      <span className="tabular-nums" title={indicado ?? undefined}>
        {crotal.crotal || crotal.crotalIndicado}
      </span>
      {/* M3: lo que el title dice a quien usa ratón, también para lector de pantalla. */}
      {indicado && <span className="sr-only">{`, ${indicado}`}</span>}
      <BadgeResolucionCrotal crotal={crotal} />
    </li>
  );
}

/** Nombre accesible de «+N más»: empieza por el texto visible (WCAG 2.5.3) y dice cuántos son. */
function nombreResto(resto: number): string {
  return `+${resto} más: ${resto} ${resto === 1 ? "crotal" : "crotales"} más`;
}

/** Los `maximo` primeros crotales y «+N más» para desplegar el resto. `cortarLinea` (tarjetas): el
 * crotal, su badge y «+N más» pasan a la línea siguiente si no caben. */
function CeldaCrotales({
  crotales,
  maximo,
  cortarLinea = false,
}: {
  crotales: TramiteCrotal[] | undefined;
  maximo: number;
  cortarLinea?: boolean;
}) {
  // Por fila: las filas llevan key por id, así que cambiar de página lo vuelve a plegar.
  const [desplegado, setDesplegado] = useState(false);
  const lista = crotales ?? [];
  if (lista.length === 0) return <span className="text-muted-foreground">Sin crotales</span>;
  const resto = lista.length - maximo;
  const visibles = desplegado ? lista : lista.slice(0, maximo);
  return (
    <ul className={cn("flex items-center gap-3", (desplegado || cortarLinea) && "flex-wrap", cortarLinea && "gap-y-1")}>
      {visibles.map((crotal, indice) => (
        <ItemCrotal key={`${indice}-${crotal.crotalIndicado}`} crotal={crotal} cortarLinea={cortarLinea} />
      ))}
      {resto > 0 && (
        <li>
          {/* Task 6 M3: un title no se ve con el dedo ni con el teclado; esto sí recibe foco. */}
          <button
            type="button"
            aria-label={desplegado ? undefined : nombreResto(resto)}
            aria-expanded={desplegado}
            onClick={(evento) => {
              // Desplegar no abre la revisión del trámite (la fila entera sí lo hace).
              evento.stopPropagation();
              setDesplegado((d) => !d);
            }}
            className="rounded-sm text-muted-foreground underline-offset-4 outline-none hover:underline focus-visible:underline focus-visible:ring-3 focus-visible:ring-ring"
          >
            {desplegado ? "Ver menos" : `+${resto} más`}
          </button>
        </li>
      )}
    </ul>
  );
}

/** B1 (D7): de dónde vino el trámite. Un icono en Gris Texto, no un badge (los badges son estados).
 * El `title` (tooltip para el ratón) va en la parte visual, oculta al lector de pantalla con el
 * icono; el texto `sr-only` es un hermano aparte. Así se anuncia una sola vez (n2 de la revisión de
 * T4: un `title` sobre el mismo elemento que el texto oculto puede leerse como descripción extra). */
function IndicadorOrigen({ origen }: { origen: string | null | undefined }) {
  const etiqueta = etiquetaOrigen(origen);
  if (!etiqueta) return null;
  return (
    <span className="ml-1.5 inline-flex align-middle text-muted-foreground">
      <span aria-hidden title={etiqueta} className="inline-flex">
        <MessageCircleIcon className="size-3.5" />
      </span>
      <span className="sr-only">{etiqueta}</span>
    </span>
  );
}

const CLASE_LINEA_AVISO = "mt-1 flex items-center gap-1 text-xs";

/** Línea de 12px bajo un dato de la fila. `aviso`: ámbar del par de aviso con icono decorativo;
 * `neutro`: Gris Texto sin icono. */
function LineaAviso({ texto, tono }: { texto: string; tono: "aviso" | "neutro" }) {
  if (tono === "neutro") return <p className={cn(CLASE_LINEA_AVISO, "text-muted-foreground")}>{texto}</p>;
  return (
    <p className={cn(CLASE_LINEA_AVISO, "text-warning-foreground")}>
      <TriangleAlertIcon aria-hidden className="size-3.5 shrink-0" />
      {texto}
    </p>
  );
}

/** B1 (A3, brief de T4): estado de la extracción bajo el badge de Estado, solo si el trámite sigue
 * pendiente (la regla vive en `avisosExtraccion`). Sin refresco automático: cambia al recargar. */
function LineaExtraccion({ tramite }: { tramite: Tramite }) {
  const { extraccion } = avisosExtraccion(tramite);
  return extraccion ? <LineaAviso texto={extraccion.cola} tono={extraccion.tono} /> : null;
}

/** B1 (D5): identificadores que la IA devolvió y no parecían crotales, en la celda de Crotales. */
function LineaDescartados({ tramite }: { tramite: Tramite }) {
  const { descartados } = avisosExtraccion(tramite);
  return descartados ? <LineaAviso texto={descartados.cola} tono="aviso" /> : null;
}
