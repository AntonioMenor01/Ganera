import { useState } from "react";
import { Combobox } from "@base-ui/react/combobox";
import { CheckIcon, ChevronDownIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { mensajeDeError } from "@/shared/api/errores";
import type { Explotacion } from "@/features/explotaciones/types";
import { consultaDeBusqueda, etiquetaExplotacion, type ExplotacionConEtiqueta } from "./buscarExplotacion";
import { useBuscarExplotaciones, type BusquedaExplotaciones } from "./useBuscarExplotaciones";
import type { ExplotacionAsignada } from "./useRevisionTramite";

/**
 * Valor del combobox: una explotación del resultado de la búsqueda (`Explotacion`) o la guardada
 * del detalle (`ExplotacionAsignada`, sin ganadero). Las dos se nombran con etiquetaExplotacion.
 */
type OpcionExplotacion = ExplotacionConEtiqueta & { id: number; nombreGanadero?: string | null };

/** Explotación guardada, en texto: la vista de solo lectura. */
export function TextoExplotacion({ asignada }: { asignada: ExplotacionAsignada | null }) {
  if (!asignada) return <span className="text-muted-foreground">Sin asignar</span>;
  return <span className="tabular-nums wrap-anywhere">{etiquetaExplotacion(asignada)}</span>;
}

interface CampoExplotacionProps {
  idCampo: string;
  /** Elegida en el formulario (puede no estar guardada aún). */
  explotacionId: number | null;
  /** La guardada, con su etiqueta del detalle (explotacionCodigoRega/explotacionNombre). */
  asignada: ExplotacionAsignada | null;
  onCambiar: (explotacionId: number) => void;
  deshabilitado: boolean;
}

/**
 * Selector de explotación con búsqueda (decisión 7, H3, H5). Usa el Combobox de base-ui (patrón
 * ARIA 1.2: input con role="combobox" + listbox), ya instalado: nada de dependencias nuevas.
 *
 * Desde la T4 de la tarea antes del piloto busca en el backend (`GET /explotaciones?q=`, ver
 * useBuscarExplotaciones) en vez de recibir la lista completa y filtrar en cliente: `filter={null}`
 * (el backend ya filtró) e `items` = resultados de la búsqueda. Solo se pide con el desplegable
 * abierto (D3a); abrir el modal no pide nada.
 *
 * El valor elegido ya no sale de una lista cargada: es la explotación elegida en esta sesión (el
 * objeto del resultado, que trae su etiqueta) o, si no, la guardada (`asignada`). Así el campo se
 * nombra igual con la lista abierta, cerrada o fallando (D3f).
 *
 * Una vez elegida no se puede vaciar (H5): no hay opción "ninguna" ni botón de borrar.
 */
export function CampoExplotacion({
  idCampo,
  explotacionId,
  asignada,
  onCambiar,
  deshabilitado,
}: CampoExplotacionProps) {
  /** La elegida en esta sesión (aún sin guardar o recién guardada): su etiqueta para el campo. */
  const [elegida, setElegida] = useState<OpcionExplotacion | null>(null);
  const [abierta, setAbierta] = useState(false);

  const seleccionada: OpcionExplotacion | null =
    explotacionId === null
      ? null
      : elegida?.id === explotacionId
        ? elegida
        : asignada?.id === explotacionId
          ? asignada
          : // No debería pasar (el formulario solo toma ids de la guardada o de una elegida aquí).
            { id: explotacionId, codigoRega: null, nombre: null };

  // Lo escrito en el campo. No se controla el input (base-ui escribe la etiqueta al elegir): solo
  // se sigue. Si la seleccionada cambia desde fuera (guardar, descartar, un 409 que recarga), el
  // combobox pone su etiqueta, y aquí se sigue igual para que D3d la trate como consulta vacía.
  const etiquetaSeleccionada = seleccionada ? etiquetaExplotacion(seleccionada) : "";
  const [texto, setTexto] = useState(etiquetaSeleccionada);
  const [etiquetaPrevia, setEtiquetaPrevia] = useState(etiquetaSeleccionada);
  if (etiquetaPrevia !== etiquetaSeleccionada) {
    setEtiquetaPrevia(etiquetaSeleccionada);
    setTexto(etiquetaSeleccionada);
  }

  const { busqueda, reintentar } = useBuscarExplotaciones({
    abierta,
    consulta: consultaDeBusqueda(texto, seleccionada),
  });
  const resultados = busqueda.estado === "lista" ? busqueda.explotaciones : SIN_RESULTADOS;

  const placeholder =
    explotacionId === null
      ? "Sin asignar · busca por código REGA, nombre o ganadero"
      : "Busca por código REGA, nombre o ganadero";

  return (
    <Combobox.Root<OpcionExplotacion>
      items={resultados}
      // El backend ya filtró (D3b): el combobox no vuelve a filtrar lo recibido.
      filter={null}
      open={abierta}
      onOpenChange={setAbierta}
      onInputValueChange={setTexto}
      value={seleccionada}
      onValueChange={(valor) => {
        // H5: vaciar no existe. Un null (p. ej. borrar el texto) no cambia lo elegido.
        if (!valor) return;
        setElegida(valor);
        setTexto(etiquetaExplotacion(valor));
        onCambiar(valor.id);
      }}
      itemToStringLabel={etiquetaExplotacion}
      isItemEqualToValue={(a, b) => a.id === b.id}
      disabled={deshabilitado}
    >
      <Combobox.InputGroup className="relative w-full min-w-0">
        {/* Clases de borde y foco copiadas de components/ui/input.tsx: si cambia Input, cambiarlas aquí.
            Sin maxLength (D3e): lo que pase de 100 caracteres lo rechaza el backend con su motivo. */}
        <Combobox.Input
          id={idCampo}
          placeholder={placeholder}
          autoComplete="off"
          spellCheck={false}
          className="h-8 w-full min-w-0 rounded-lg border border-input bg-transparent py-1 pr-9 pl-2.5 text-base tabular-nums transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 md:text-sm"
        />
        <Combobox.Trigger
          aria-label="Abrir la lista de explotaciones"
          className="absolute top-0 right-0 flex h-8 w-8 items-center justify-center rounded-r-lg text-muted-foreground outline-none hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50"
        >
          <ChevronDownIcon className="size-4" aria-hidden />
        </Combobox.Trigger>
      </Combobox.InputGroup>

      <Combobox.Portal>
        <Combobox.Positioner sideOffset={4} className="isolate z-50 outline-none">
          <Combobox.Popup className="w-(--anchor-width) max-w-(--available-width) origin-(--transform-origin) overflow-hidden rounded-lg bg-popover text-popover-foreground shadow-md ring-1 ring-foreground/10 duration-100 data-open:animate-in data-open:fade-in-0 data-closed:animate-out data-closed:fade-out-0">
            {/* Solo con la búsqueda terminada: mientras busca o si falla, "ninguna coincide" no es cierto. */}
            <Combobox.Empty className="px-3 py-2 text-sm text-muted-foreground empty:hidden">
              {busqueda.estado === "lista" ? "Ninguna explotación coincide con lo que has escrito." : null}
            </Combobox.Empty>
            <Combobox.List className="max-h-[min(20rem,var(--available-height))] scroll-py-1 overflow-y-auto overscroll-contain p-1 data-empty:p-0">
              {(explotacion: OpcionExplotacion) => (
                <Combobox.Item
                  key={explotacion.id}
                  value={explotacion}
                  className="relative grid cursor-default grid-cols-[1fr_1rem] items-center gap-x-2 rounded-md py-1.5 pr-2 pl-2 text-sm outline-none select-none data-highlighted:bg-accent data-highlighted:text-accent-foreground"
                >
                  <span className="min-w-0">
                    <span className="block wrap-anywhere">
                      <span className="tabular-nums">{explotacion.codigoRega}</span>
                      {explotacion.nombre ? ` · ${explotacion.nombre}` : null}
                    </span>
                    {explotacion.nombreGanadero && (
                      <span className="block text-xs text-muted-foreground wrap-anywhere">
                        <span className="sr-only">, ganadero:</span> {explotacion.nombreGanadero}
                      </span>
                    )}
                  </span>
                  <Combobox.ItemIndicator className="col-start-2">
                    <CheckIcon className="size-4" aria-hidden />
                  </Combobox.ItemIndicator>
                </Combobox.Item>
              )}
            </Combobox.List>
            <EstadoBusqueda busqueda={busqueda} onReintentar={reintentar} />
          </Combobox.Popup>
        </Combobox.Positioner>
      </Combobox.Portal>
    </Combobox.Root>
  );
}

const SIN_RESULTADOS: Explotacion[] = [];

/**
 * Pie del desplegable: UNA región `status` (Combobox.Status) siempre montada con la lista, para que
 * se anuncie cada cambio sin repetir avisos: buscando (D3a), "Hay N coincidencias" (D3c, la idea
 * del antiguo AvisoMasCoincidencias, m1 de 9b) o el error de búsqueda (D3f, con el `motivo` del
 * backend si lo trae, p. ej. el 400 de más de 100 caracteres, D3e). El botón "Reintentar" va fuera
 * de la región: un control no se anuncia como estado.
 */
function EstadoBusqueda({
  busqueda,
  onReintentar,
}: {
  busqueda: BusquedaExplotaciones;
  onReintentar: () => void;
}) {
  let texto: string | null = null;
  let clase = "border-t px-3 py-1.5 text-xs text-muted-foreground";
  if (busqueda.estado === "buscando") {
    texto = "Buscando explotaciones…";
  } else if (busqueda.estado === "lista" && busqueda.total > busqueda.explotaciones.length) {
    texto = `Hay ${busqueda.total.toLocaleString("es-ES")} coincidencias; escribe para acotar.`;
  } else if (busqueda.estado === "error") {
    texto = mensajeDeError(busqueda.error, "listar-explotaciones");
    clase = "px-3 pt-2 text-sm text-destructive";
  }
  return (
    <>
      <Combobox.Status className={texto ? clase : undefined}>{texto}</Combobox.Status>
      {busqueda.estado === "error" && (
        <div className="px-3 pt-1.5 pb-2">
          <Button variant="outline" size="sm" onClick={onReintentar}>
            Reintentar
          </Button>
        </div>
      )}
    </>
  );
}
