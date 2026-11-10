import { Fragment, useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ChevronDown, CircleAlertIcon, FileSpreadsheetIcon, SearchIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { aErrorApi, mensajeDeError, type ErrorApi } from "@/shared/api/errores";
import type { Pagina } from "@/shared/api/types";
import { listarExplotaciones } from "./api";
import type { Explotacion } from "./types";
import { ImportarExcelSection } from "./ImportarExcelSection";
import { AnimalesDeExplotacion } from "./AnimalesDeExplotacion";
import { useDesdeSm } from "./useDesdeSm";
import { CLASE_ENLACE_TABLA } from "@/shared/ui/enlace";

const TAMANIO_PAGINA = 20;
/** Espera antes de mandar `q` al backend (evita un GET por tecla). */
const DEBOUNCE_BUSQUEDA_MS = 300;

const idPanelAnimales = (explotacionId: number) => `explotacion-${explotacionId}-animales`;
/** La columna sin título visible de "Ver animales"; la celda del panel desplegado se asocia a ella. */
const ID_CABECERA_ANIMALES = "explotaciones-col-animales";

function textoRecuento(total: number): string {
  return `${total} ${total === 1 ? "explotación" : "explotaciones"}`;
}

export function ExplotacionesPage() {
  const [pagina, setPagina] = useState<Pagina<Explotacion> | null>(null);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState<ErrorApi | null>(null);
  // Último total conocido: si falla una página, la paginación sigue a la vista para poder salir
  // de ella (a otra página o reintentando), en vez de quedarse atascado en la que falla.
  const [totalPaginas, setTotalPaginas] = useState(0);
  const [version, setVersion] = useState(0);
  const [qInput, setQInput] = useState("");
  // q + página juntos: al cambiar la búsqueda se vuelve a página 0 en el mismo update (sin un
  // GET intermedio con la página vieja).
  const [consulta, setConsulta] = useState({ q: "", page: 0 });
  const abrirImportacionRef = useRef<(() => void) | null>(null);

  // Paneles "Ver animales" desplegados. Pueden ser varios a la vez, como en la ficha del Ganadero
  // (cada sección se despliega por su cuenta): sirve para comparar dos inventarios.
  const [abiertas, setAbiertas] = useState<ReadonlySet<number>>(() => new Set());

  // I1 (revisión Task 8): por debajo de `sm` no hay columna Nombre; el nombre va en gris bajo el
  // código REGA y la tabla cabe en 375 px sin scroll lateral. Se decide en JS y no con una clase
  // porque cambia la ESTRUCTURA: el nombre está una sola vez en el DOM (nada duplicado para el
  // lector de pantalla) y el colSpan de las filas a todo el ancho es el nº real de columnas.
  const desdeSm = useDesdeSm();
  // Código REGA, [Nombre], Ganadero y el botón "Ver animales".
  const columnas = desdeSm ? 4 : 3;

  useEffect(() => {
    const id = window.setTimeout(() => {
      const q = qInput.trim();
      setConsulta((actual) => (actual.q === q ? actual : { q, page: 0 }));
    }, DEBOUNCE_BUSQUEDA_MS);
    return () => window.clearTimeout(id);
  }, [qInput]);

  // Nueva búsqueda: paneles de animales de la consulta anterior no valen.
  useEffect(() => {
    setAbiertas(new Set());
  }, [consulta.q]);

  // Cualquier recarga del listado (otra página, reintento, importación) pliega los paneles: al
  // volver, la fila puede ser otra o su inventario haber cambiado.
  const recargar = useCallback(() => {
    setAbiertas(new Set());
    setVersion((v) => v + 1);
  }, []);

  const irAPagina = useCallback((cambio: (p: number) => number) => {
    setAbiertas(new Set());
    setConsulta((actual) => ({ ...actual, page: cambio(actual.page) }));
  }, []);

  const alternarAnimales = useCallback((explotacionId: number) => {
    setAbiertas((actuales) => {
      const siguientes = new Set(actuales);
      if (!siguientes.delete(explotacionId)) siguientes.add(explotacionId);
      return siguientes;
    });
  }, []);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setErrorCarga(null);
    listarExplotaciones(consulta.page, TAMANIO_PAGINA, {
      q: consulta.q || undefined,
    })
      .then((resultado) => {
        if (cancelado) return;
        setPagina(resultado);
        setTotalPaginas(resultado.totalPages);
      })
      .catch((err: unknown) => {
        if (cancelado) return;
        // No se deja a la vista una página vieja como si fuera la respuesta actual.
        setPagina(null);
        setErrorCarga(aErrorApi(err));
      })
      .finally(() => {
        if (!cancelado) setCargando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [consulta, version]);

  const subtitulo = pagina
    ? textoRecuento(pagina.totalElements)
    : errorCarga
      ? "—"
      : "Cargando…";

  return (
    <div className="mx-auto flex w-full max-w-[1360px] min-w-0 flex-col gap-5">
      <div className="flex flex-wrap items-end justify-between gap-3 border-b-2 border-divisor pb-5">
        <div className="min-w-0">
          <p className="mb-1.5 text-[13px] font-medium text-muted-foreground tabular-nums">
            {subtitulo}
          </p>
          <h1 className="text-[28px] leading-tight font-bold tracking-[-0.02em]">Explotaciones</h1>
          <p className="mt-1 text-[13px] text-muted-foreground">
            REGA, fincas y animales de tus ganaderos
          </p>
        </div>
        <Button variant="outline" onClick={() => abrirImportacionRef.current?.()}>
          <FileSpreadsheetIcon aria-hidden="true" />
          Importar Excel
        </Button>
      </div>

        <ImportarExcelSection
        onImportado={() => {
          // recargar() pliega los paneles (su inventario puede haber cambiado).
          setConsulta((actual) => ({ ...actual, page: 0 }));
          recargar();
        }}
        registrarAbrir={(abrir) => {
          abrirImportacionRef.current = abrir;
        }}
        ocultarBotonPrincipal
      />

      <label className="relative max-w-[380px]">
        <span className="sr-only">Buscar explotaciones</span>
        <SearchIcon
          aria-hidden="true"
          className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground"
        />
        <Input
          value={qInput}
          onChange={(e) => setQInput(e.target.value)}
          placeholder="REGA, finca o ganadero"
          className="h-[38px] bg-card pl-9"
        />
      </label>

      {errorCarga && (
        <Alert variant="destructive">
          <CircleAlertIcon />
          <AlertTitle>No se han podido cargar las explotaciones</AlertTitle>
          <AlertDescription>
            <p>{mensajeDeError(errorCarga, "listar-explotaciones")}</p>
            <Button variant="outline" size="sm" onClick={recargar} className="mt-2">
              Reintentar
            </Button>
          </AlertDescription>
        </Alert>
      )}

      <div className="min-w-0 border border-border bg-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Código REGA</TableHead>
              {desdeSm && <TableHead>Nombre</TableHead>}
              <TableHead>Ganadero</TableHead>
              <TableHead id={ID_CABECERA_ANIMALES} className="text-right">
                {/* Visible desde sm; por debajo, solo para el lector de pantalla (falta sitio). */}
                <span className="sr-only sm:not-sr-only">Animales</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {pagina?.content.length === 0 && !cargando && !errorCarga && (
              <TableRow>
                <TableCell colSpan={columnas} className="text-center text-muted-foreground">
                  {consulta.q
                    ? "Ninguna explotación coincide con la búsqueda."
                    : "No hay explotaciones todavía. Importa un Excel para empezar."}
                </TableCell>
              </TableRow>
            )}
            {pagina?.content.map((explotacion) => {
              const abierta = abiertas.has(explotacion.id);
              return (
                <Fragment key={explotacion.id}>
                  {/* Abierta, la fila y su panel son una sola franja: sin raya entre ellas. */}
                  <TableRow className={cn(abierta && "border-b-0")}>
                    {desdeSm ? (
                      <>
                        <TableCell className="tabular-nums">
                          <Link
                            to={`/explotaciones/${explotacion.id}`}
                            className={CLASE_ENLACE_TABLA}
                          >
                            {explotacion.codigoRega}
                          </Link>
                        </TableCell>
                        <TableCell className="whitespace-normal wrap-anywhere">
                          <Link
                            to={`/explotaciones/${explotacion.id}`}
                            className={CLASE_ENLACE_TABLA}
                          >
                            {explotacion.nombre}
                          </Link>
                        </TableCell>
                      </>
                    ) : (
                      <TableCell>
                        <Link
                          to={`/explotaciones/${explotacion.id}`}
                          className={cn("block", CLASE_ENLACE_TABLA)}
                        >
                          <span className="block tabular-nums">{explotacion.codigoRega}</span>
                          <span className="block whitespace-normal wrap-anywhere text-muted-foreground">
                            {explotacion.nombre}
                          </span>
                        </Link>
                      </TableCell>
                    )}
                    <TableCell className="whitespace-normal wrap-anywhere">
                      {/* Decisión 14: el ganadero enlaza con su ficha. */}
                      <Link
                        to={`/ganaderos/${explotacion.ganaderoId}`}
                        className={CLASE_ENLACE_TABLA}
                      >
                        {explotacion.nombreGanadero}
                      </Link>
                    </TableCell>
                    {/* En móvil, celda y botón ceñidos al chevrón (28 px, como un icon-sm). */}
                    <TableCell className="text-right max-sm:px-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="max-sm:px-1.5"
                        aria-expanded={abierta}
                        // Solo apunta al panel cuando existe: plegado no se monta (no pide nada).
                        aria-controls={abierta ? idPanelAnimales(explotacion.id) : undefined}
                        // Un botón por fila: el nombre dice de qué explotación, y empieza por el
                        // texto visible (se puede pedir por voz diciendo "Ver animales").
                        aria-label={`Ver animales de ${explotacion.codigoRega}`}
                        onClick={() => alternarAnimales(explotacion.id)}
                      >
                        {/* En móvil queda solo el chevrón, para que la tabla quepa. */}
                        <span className="max-sm:hidden">Ver animales</span>
                        <ChevronDown
                          aria-hidden="true"
                          className={cn(
                            "size-4 transition-transform duration-200 motion-reduce:transition-none",
                            abierta && "rotate-180",
                          )}
                        />
                      </Button>
                    </TableCell>
                  </TableRow>
                  {abierta && (
                    <TableRow className="bg-muted/50 hover:bg-muted/50">
                      {/* px-2: el panel arranca en el mismo borde que el texto de las celdas (m1). */}
                      {/* headers: si no, la celda se anuncia bajo la primera columna, "Código REGA". */}
                      <TableCell
                        colSpan={columnas}
                        headers={ID_CABECERA_ANIMALES}
                        className="px-2 pt-1 pb-4 whitespace-normal"
                      >
                        <div id={idPanelAnimales(explotacion.id)}>
                          <AnimalesDeExplotacion explotacionId={explotacion.id} />
                        </div>
                      </TableCell>
                    </TableRow>
                  )}
                </Fragment>
              );
            })}
          </TableBody>
        </Table>
      </div>

      {totalPaginas > 1 && (
        <nav aria-label="Paginación" className="flex items-center justify-between gap-3">
          <Button
            variant="outline"
            size="sm"
            disabled={consulta.page === 0}
            onClick={() => irAPagina((p) => p - 1)}
          >
            Anterior
          </Button>
          <span className="text-sm text-muted-foreground tabular-nums">
            Página {consulta.page + 1} de {totalPaginas}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={consulta.page + 1 >= totalPaginas}
            onClick={() => irAPagina((p) => p + 1)}
          >
            Siguiente
          </Button>
        </nav>
      )}
    </div>
  );
}
