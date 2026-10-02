import { useCallback, useEffect, useState, type MouseEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";
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
import { aErrorApi, mensajeDeError, type ErrorApi } from "@/shared/api/errores";
import type { Pagina } from "@/shared/api/types";
import { listarGanaderos } from "./api";
import type { GanaderoResumen } from "./types";
import {
  ORDEN_INICIAL,
  ariaSortDe,
  parametrosSort,
  siguienteOrden,
  type CampoOrdenGanadero,
  type OrdenGanaderos,
} from "./ordenGanaderos";
import { CLASE_ENLACE } from "@/shared/ui/enlace";

const TAMANIO_PAGINA = 20;
const COLUMNAS = 3;
const FILAS_ESQUELETO = 5;
const BARRA_ESQUELETO = "h-4 w-full max-w-40 rounded-sm bg-muted motion-safe:animate-pulse";

function textoRecuento(total: number): string {
  return `${total} ${total === 1 ? "ganadero" : "ganaderos"}`;
}

export function GanaderosPage() {
  const navigate = useNavigate();
  const [pagina, setPagina] = useState<Pagina<GanaderoResumen> | null>(null);
  const [numeroPagina, setNumeroPagina] = useState(0);
  const [orden, setOrden] = useState<OrdenGanaderos | null>(ORDEN_INICIAL);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState<ErrorApi | null>(null);
  // Último total conocido: si falla una página, la paginación sigue a la vista para poder salir
  // de ella (a otra página o reintentando), en vez de quedarse atascado en la que falla.
  const [totalPaginas, setTotalPaginas] = useState(0);
  const [version, setVersion] = useState(0);

  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setErrorCarga(null);
    listarGanaderos(numeroPagina, TAMANIO_PAGINA, { sort: parametrosSort(orden) })
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
  }, [numeroPagina, orden, version]);

  function ordenarPor(campo: CampoOrdenGanadero) {
    // Nuevo orden = nueva lista: se vuelve a la primera página y no queda a la vista la anterior.
    setOrden((actual) => siguienteOrden(actual, campo));
    setNumeroPagina(0);
    setPagina(null);
  }

  function abrirDesdeFila(evento: MouseEvent<HTMLTableRowElement>, id: number) {
    // Comodidad de ratón. Con modificadores (nueva pestaña/ventana) o texto seleccionado no se
    // navega aquí: para eso está el enlace del nombre.
    if (evento.defaultPrevented || evento.button !== 0) return;
    if (evento.ctrlKey || evento.metaKey || evento.shiftKey || evento.altKey) return;
    if (window.getSelection()?.toString()) return;
    navigate(`/ganaderos/${id}`);
  }

  const subtitulo = pagina
    ? textoRecuento(pagina.totalElements)
    : errorCarga
      ? "—"
      : "Cargando…";

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <div className="min-w-0">
        <h1 className="text-xl font-semibold">Ganaderos</h1>
        <p className="text-sm text-muted-foreground tabular-nums">{subtitulo}</p>
      </div>

      {errorCarga && (
        <Alert variant="destructive">
          <AlertTitle>No se han podido cargar los ganaderos</AlertTitle>
          <AlertDescription>
            <p>{mensajeDeError(errorCarga, "listar-ganaderos")}</p>
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
          {cargando ? "Cargando ganaderos…" : ""}
        </p>
        <Table aria-busy={cargando}>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <CabeceraOrdenable campo="nombre" orden={orden} onOrdenar={ordenarPor}>
                Nombre
              </CabeceraOrdenable>
              <CabeceraOrdenable campo="nif" orden={orden} onOrdenar={ordenarPor}>
                NIF
              </CabeceraOrdenable>
              {/* En móvil, "Expl." para que las tres columnas quepan; el nombre accesible es siempre
                  "Explotaciones". */}
              <TableHead className="text-right">
                <span className="sm:hidden" aria-hidden="true">
                  Expl.
                </span>
                <span className="max-sm:sr-only">Explotaciones</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className={cargando && pagina ? "opacity-60 transition-opacity" : undefined}>
            {cargando &&
              !pagina &&
              Array.from({ length: FILAS_ESQUELETO }, (_, fila) => (
                <TableRow key={`esqueleto-${fila}`} className="hover:bg-transparent">
                  <TableCell>
                    <div aria-hidden="true" className={BARRA_ESQUELETO} />
                  </TableCell>
                  <TableCell>
                    <div aria-hidden="true" className={BARRA_ESQUELETO} />
                  </TableCell>
                  <TableCell>
                    <div aria-hidden="true" className="ml-auto h-4 w-6 rounded-sm bg-muted motion-safe:animate-pulse" />
                  </TableCell>
                </TableRow>
              ))}
            {pagina?.content.length === 0 && !cargando && !errorCarga && (
              <TableRow className="hover:bg-transparent">
                <TableCell
                  colSpan={COLUMNAS}
                  className="py-6 text-center whitespace-normal text-muted-foreground"
                >
                  <p>Todavía no hay ganaderos. Se crean al importar el Excel de explotaciones.</p>
                  <Link to="/explotaciones" className={`mt-1 inline-block ${CLASE_ENLACE}`}>
                    Ir a Explotaciones
                  </Link>
                </TableCell>
              </TableRow>
            )}
            {pagina?.content.map((ganadero) => (
              <TableRow
                key={ganadero.id}
                className="cursor-pointer has-focus-visible:bg-muted/50"
                onClick={(evento) => abrirDesdeFila(evento, ganadero.id)}
              >
                {/* El nombre se parte: en móvil el recuento de la derecha sigue a la vista. */}
                <TableCell className="whitespace-normal wrap-anywhere">
                  <Link
                    to={`/ganaderos/${ganadero.id}`}
                    // El enlace ya navega: que la fila no lo repita (un solo paso en el historial).
                    onClick={(evento) => evento.stopPropagation()}
                    className={`font-medium ${CLASE_ENLACE}`}
                  >
                    {ganadero.nombre}
                  </Link>
                </TableCell>
                <TableCell>
                  {ganadero.nif ? (
                    <span className="tabular-nums">{ganadero.nif}</span>
                  ) : (
                    <span className="text-muted-foreground">Sin NIF</span>
                  )}
                </TableCell>
                <TableCell>
                  <span className="block text-right tabular-nums">
                    {ganadero.numeroExplotaciones}
                  </span>
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
    </div>
  );
}

function CabeceraOrdenable({
  campo,
  orden,
  onOrdenar,
  children,
}: {
  campo: CampoOrdenGanadero;
  orden: OrdenGanaderos | null;
  onOrdenar: (campo: CampoOrdenGanadero) => void;
  children: string;
}) {
  const ariaSort = ariaSortDe(orden, campo);
  const Icono = ariaSort === "ascending" ? ArrowUp : ariaSort === "descending" ? ArrowDown : ArrowUpDown;
  return (
    <TableHead aria-sort={ariaSort}>
      {/* El nombre accesible es solo el de la columna; la dirección la anuncia aria-sort. */}
      <button
        type="button"
        onClick={() => onOrdenar(campo)}
        className="-mx-1 inline-flex items-center gap-1 rounded-md px-1 py-0.5 font-medium outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50"
      >
        {children}
        <Icono
          aria-hidden="true"
          className={ariaSort ? "size-3.5 text-foreground" : "size-3.5 text-muted-foreground"}
        />
      </button>
    </TableHead>
  );
}
