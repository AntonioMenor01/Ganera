import { useCallback, useEffect, useState } from "react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { aErrorApi, esCancelacion, mensajeDeError, type ErrorApi } from "@/shared/api/errores";
import type { Pagina } from "@/shared/api/types";
import { listarAnimalesDeExplotacion } from "./api";
import type { Animal } from "./types";

const TAMANIO_PAGINA = 20;
const CELDAS_ESQUELETO = 6;
const CONTEXTO_ERROR = "animales-explotacion";

/**
 * Rejilla de crotales: se rellena por filas (izquierda → derecha, luego abajo), así que el orden del
 * DOM, el de lectura y el del lector de pantalla son el mismo orden por crotal. 8rem caben un crotal
 * español (14 caracteres tabulares) y dejan dos columnas a 375 px; en escritorio salen seis o siete.
 */
const REJILLA = "grid grid-cols-[repeat(auto-fill,minmax(8rem,1fr))] gap-x-4 gap-y-1.5";

function textoRecuento(total: number): string {
  return `${total} ${total === 1 ? "animal" : "animales"}`;
}

/**
 * Inventario de animales de una explotación: panel "Ver animales" de Explotaciones y de la ficha
 * del Ganadero (decisión 15 y H1-A del plan A2). Pagina `GET /explotaciones/{id}/animales` de 20 en
 * 20, por crotal.
 *
 * Contrato con quien lo usa: se monta solo con el panel desplegado (la petición sale al abrirlo, no
 * al cargar la pantalla), una instancia por explotación (con `key`), y plegar lo desmonta, lo que
 * cancela la petición en vuelo. Una cancelación no es un error: no se enseña nada.
 */
export function AnimalesDeExplotacion({ explotacionId }: { explotacionId: number }) {
  const [numeroPagina, setNumeroPagina] = useState(0);
  const [intento, setIntento] = useState(0);
  const [pagina, setPagina] = useState<Pagina<Animal> | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<ErrorApi | null>(null);
  // Último total conocido: si falla una página, la paginación sigue a la vista para poder salir
  // de ella (a otra página o reintentando), igual que en los listados.
  const [totalPaginas, setTotalPaginas] = useState(0);

  const reintentar = useCallback(() => setIntento((n) => n + 1), []);

  useEffect(() => {
    const controlador = new AbortController();
    setCargando(true);
    setError(null);
    listarAnimalesDeExplotacion(explotacionId, numeroPagina, TAMANIO_PAGINA, controlador.signal)
      .then((resultado) => {
        // Una respuesta de una página que ya no es la pedida (o de un panel plegado) no pisa nada:
        // la cancelación lo cubre casi siempre, y esto cubre la respuesta que llegó justo antes.
        if (controlador.signal.aborted) return;
        setPagina(resultado);
        setTotalPaginas(resultado.totalPages);
        setCargando(false);
      })
      .catch((err: unknown) => {
        if (controlador.signal.aborted || esCancelacion(err)) return;
        // No se deja a la vista una página vieja como si fuera la respuesta actual.
        setPagina(null);
        setError(aErrorApi(err));
        setCargando(false);
      });
    return () => controlador.abort();
  }, [explotacionId, numeroPagina, intento]);

  const noEncontrada = error?.tipo === "no-encontrado";

  return (
    <div className="min-w-0">
      {/* WCAG 4.1.3: estado siempre montado y fuera de aria-busy; solo cambia su texto, así el
          lector de pantalla lo anuncia (uno que aparece ya lleno dentro de un contenedor ocupado
          suele perderse). */}
      <p role="status" className="sr-only">
        {cargando ? "Cargando animales…" : ""}
      </p>
      <div className="flex min-w-0 flex-col gap-3" aria-busy={cargando}>
        {cargando && !pagina && <Esqueleto />}

        {error &&
          (noEncontrada ? (
            // 404: de otra gestoría o inexistente, indistinguibles a propósito. Reintentar no lo arregla.
            <p className="text-sm text-muted-foreground">{mensajeDeError(error, CONTEXTO_ERROR)}</p>
          ) : (
            <Alert variant="destructive">
              <AlertTitle>No se han podido cargar los animales</AlertTitle>
              <AlertDescription>
                <p>{mensajeDeError(error, CONTEXTO_ERROR)}</p>
                <Button variant="outline" size="sm" onClick={reintentar} className="mt-2">
                  Reintentar
                </Button>
              </AlertDescription>
            </Alert>
          ))}

        {pagina && pagina.totalElements === 0 && (
          <p className="text-sm text-muted-foreground">
            Esta explotación no tiene animales en el inventario. Se cargan al importar el Excel.
          </p>
        )}

        {pagina && pagina.totalElements > 0 && (
          <>
            <p className="text-sm text-muted-foreground tabular-nums">
              {textoRecuento(pagina.totalElements)}
            </p>
            {/* Mientras llega otra página, la actual sigue a la vista atenuada: no salta el alto. */}
            <ol
              aria-label="Crotales"
              className={cn(REJILLA, "text-sm", cargando && "opacity-60 transition-opacity")}
            >
              {pagina.content.map((animal) => (
                <li key={animal.id} className="min-w-0 break-all tabular-nums">
                  <Crotal animal={animal} />
                </li>
              ))}
            </ol>
          </>
        )}

        {totalPaginas > 1 && !noEncontrada && (
          <nav aria-label="Páginas de animales" className="flex flex-wrap items-center gap-3">
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
    </div>
  );
}

/**
 * El crotal entero, con sus últimos dígitos en tinta y el resto en gris: es lo que los contactos
 * escriben por WhatsApp, así que se encuentra de un vistazo sin una columna repetida. Si el backend
 * mandara unos últimos dígitos que no son el final del crotal, se pinta entero, sin destacar nada.
 */
function Crotal({ animal }: { animal: Animal }) {
  const { crotal, crotalUltimosDigitos: cola } = animal;
  if (!cola || cola.length >= crotal.length || !crotal.endsWith(cola)) {
    return <span>{crotal}</span>;
  }
  return (
    <>
      <span className="text-muted-foreground">{crotal.slice(0, crotal.length - cola.length)}</span>
      <span className="font-medium text-foreground">{cola}</span>
    </>
  );
}

function Esqueleto() {
  return (
    // El estado para lector de pantalla vive fuera, en AnimalesDeExplotacion.
    <div aria-hidden="true" className="flex flex-col gap-3">
      <div className="h-4 w-20 rounded-sm bg-foreground/10 motion-safe:animate-pulse" />
      <div className={REJILLA}>
        {Array.from({ length: CELDAS_ESQUELETO }, (_, i) => (
          <div key={i} className="h-4 w-28 max-w-full rounded-sm bg-foreground/10 motion-safe:animate-pulse" />
        ))}
      </div>
    </div>
  );
}
