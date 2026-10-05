import { Fragment, useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ChevronDown, CircleAlertIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
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

const idPanelAnimales = (explotacionId: number) => `explotacion-${explotacionId}-animales`;
/** La columna sin título visible de "Ver animales"; la celda del panel desplegado se asocia a ella. */
const ID_CABECERA_ANIMALES = "explotaciones-col-animales";

export function ExplotacionesPage() {
  const [pagina, setPagina] = useState<Pagina<Explotacion> | null>(null);
  const [numeroPagina, setNumeroPagina] = useState(0);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState<ErrorApi | null>(null);
  // Último total conocido: si falla una página, la paginación sigue a la vista para poder salir
  // de ella (a otra página o reintentando), en vez de quedarse atascado en la que falla.
  const [totalPaginas, setTotalPaginas] = useState(0);
  const [version, setVersion] = useState(0);

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

  // Cualquier recarga del listado (otra página, reintento, importación) pliega los paneles: al
  // volver, la fila puede ser otra o su inventario haber cambiado.
  const recargar = useCallback(() => {
    setAbiertas(new Set());
    setVersion((v) => v + 1);
  }, []);

  const irAPagina = useCallback((cambio: (p: number) => number) => {
    setAbiertas(new Set());
    setNumeroPagina(cambio);
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
    listarExplotaciones(numeroPagina, TAMANIO_PAGINA)
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
  }, [numeroPagina, version]);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-extrabold">Explotaciones</h1>
      </div>

      <Card className="w-fit min-w-48">
        <CardContent className="flex flex-col gap-1">
          <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">
            Explotaciones totales
          </span>
          <span className="text-3xl font-extrabold">
            {pagina ? pagina.totalElements : "—"}
          </span>
        </CardContent>
      </Card>

      <ImportarExcelSection
        onImportado={() => {
          // recargar() pliega los paneles (su inventario puede haber cambiado): no hace falta irAPagina.
          setNumeroPagina(0);
          recargar();
        }}
      />

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

      <div className="rounded-xl border bg-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Código REGA</TableHead>
              {desdeSm && <TableHead>Nombre</TableHead>}
              <TableHead>Ganadero</TableHead>
              <TableHead id={ID_CABECERA_ANIMALES}>
                <span className="sr-only">Animales</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {pagina?.content.length === 0 && !cargando && !errorCarga && (
              <TableRow>
                <TableCell colSpan={columnas} className="text-center text-muted-foreground">
                  No hay explotaciones todavía. Importa un Excel para empezar.
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
                        <TableCell className="tabular-nums">{explotacion.codigoRega}</TableCell>
                        <TableCell className="whitespace-normal wrap-anywhere">{explotacion.nombre}</TableCell>
                      </>
                    ) : (
                      <TableCell>
                        <span className="block tabular-nums">{explotacion.codigoRega}</span>
                        <span className="block whitespace-normal wrap-anywhere text-muted-foreground">
                          {explotacion.nombre}
                        </span>
                      </TableCell>
                    )}
                    <TableCell className="whitespace-normal wrap-anywhere">
                      {/* Decisión 14: el ganadero enlaza con su ficha. */}
                      <Link to={`/ganaderos/${explotacion.ganaderoId}`} className={CLASE_ENLACE_TABLA}>
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
            disabled={numeroPagina === 0}
            onClick={() => irAPagina((p) => p - 1)}
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
            onClick={() => irAPagina((p) => p + 1)}
          >
            Siguiente
          </Button>
        </nav>
      )}
    </div>
  );
}
