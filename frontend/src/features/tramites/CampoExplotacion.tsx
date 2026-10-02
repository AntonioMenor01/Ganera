import { Combobox } from "@base-ui/react/combobox";
import { CheckIcon, ChevronDownIcon } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { mensajeDeError } from "@/shared/api/errores";
import type { Explotacion } from "@/features/explotaciones/types";
import type { CargaTodasLasExplotaciones } from "@/features/explotaciones/useTodasLasExplotaciones";
import { coincideExplotacion, etiquetaExplotacion } from "./buscarExplotacion";
import type { ExplotacionAsignada } from "./useRevisionTramite";

/** Opciones que se pintan a la vez: con miles de explotaciones, escribir acota (H3). */
export const LIMITE_OPCIONES_EXPLOTACION = 100;

function etiquetaAsignada(asignada: ExplotacionAsignada): string {
  if (!asignada.codigoRega) return `Explotación #${asignada.id}`;
  return asignada.nombre ? `${asignada.codigoRega} · ${asignada.nombre}` : asignada.codigoRega;
}

/** Explotación guardada, en texto: solo lectura, y de referencia mientras la lista no está. */
export function TextoExplotacion({ asignada }: { asignada: ExplotacionAsignada | null }) {
  if (!asignada) return <span className="text-muted-foreground">Sin asignar</span>;
  return <span className="tabular-nums wrap-anywhere">{etiquetaAsignada(asignada)}</span>;
}

interface CampoExplotacionProps {
  idCampo: string;
  /** Elegida en el formulario (puede no estar guardada aún). */
  explotacionId: number | null;
  /** La guardada, con su etiqueta del detalle. */
  asignada: ExplotacionAsignada | null;
  carga: CargaTodasLasExplotaciones;
  onReintentarCarga: () => void;
  onCambiar: (explotacionId: number) => void;
  deshabilitado: boolean;
}

/**
 * Selector de explotación con búsqueda (decisión 7, H3, H5). Usa el Combobox de base-ui (patrón
 * ARIA 1.2: input con role="combobox" + listbox), ya instalado: nada de dependencias nuevas.
 * La lista completa llega de TramitesPage (decisiones 20 y 22): aquí nunca se pide otra vez.
 * Una vez elegida no se puede vaciar (H5): no hay opción "ninguna" ni botón de borrar.
 */
export function CampoExplotacion({
  idCampo,
  explotacionId,
  asignada,
  carga,
  onReintentarCarga,
  onCambiar,
  deshabilitado,
}: CampoExplotacionProps) {
  if (carga.estado === "cargando") {
    return (
      <div className="flex flex-col gap-1.5">
        <span aria-hidden className="h-8 w-full rounded-lg bg-muted motion-safe:animate-pulse" />
        <span role="status" className="sr-only">
          Cargando explotaciones…
        </span>
        {asignada && (
          <p className="text-muted-foreground">
            Asignada: <TextoExplotacion asignada={asignada} />
          </p>
        )}
      </div>
    );
  }

  if (carga.estado === "error") {
    // Decisión 20: nunca una lista parcial. Sin lista no se puede elegir; se ve la guardada.
    return (
      <div className="flex flex-col gap-2">
        <p>
          <TextoExplotacion asignada={asignada} />
        </p>
        <Alert variant="destructive">
          <AlertTitle>No se ha podido cargar la lista de explotaciones</AlertTitle>
          <AlertDescription>
            <p>{mensajeDeError(carga.error, "listar-explotaciones")}</p>
            <Button variant="outline" size="sm" onClick={onReintentarCarga} className="mt-2">
              Reintentar
            </Button>
          </AlertDescription>
        </Alert>
      </div>
    );
  }

  const seleccionada = explotacionId === null ? null : (carga.porId.get(explotacionId) ?? null);
  // La guardada puede no estar en la lista (importada después de abrir la cola): se nombra igual.
  const placeholder =
    explotacionId !== null && !seleccionada && asignada
      ? `${etiquetaAsignada(asignada)} (no está en la lista cargada)`
      : explotacionId === null
        ? "Sin asignar · busca por código REGA, nombre o ganadero"
        : "Busca por código REGA, nombre o ganadero";

  return (
    <Combobox.Root<Explotacion>
      items={carga.explotaciones}
      value={seleccionada}
      onValueChange={(valor) => {
        // H5: vaciar no existe. Un null (p. ej. borrar el texto) no cambia lo elegido.
        if (valor) onCambiar(valor.id);
      }}
      itemToStringLabel={etiquetaExplotacion}
      isItemEqualToValue={(a, b) => a.id === b.id}
      filter={(explotacion, consulta) => coincideExplotacion(explotacion, consulta)}
      // Una más que el límite: así se sabe si hay MÁS coincidencias que las mostradas (m1).
      limit={LIMITE_OPCIONES_EXPLOTACION + 1}
      disabled={deshabilitado}
    >
      <Combobox.InputGroup className="relative w-full min-w-0">
        {/* Clases de borde y foco copiadas de components/ui/input.tsx: si cambia Input, cambiarlas aquí. */}
        <Combobox.Input
          id={idCampo}
          placeholder={placeholder}
          autoComplete="off"
          spellCheck={false}
          className="h-8 w-full min-w-0 rounded-lg border border-input bg-transparent py-1 pr-9 pl-2.5 text-base tabular-nums transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 md:text-sm"
        />
        <Combobox.Trigger
          aria-label="Ver todas las explotaciones"
          className="absolute top-0 right-0 flex h-8 w-8 items-center justify-center rounded-r-lg text-muted-foreground outline-none hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50"
        >
          <ChevronDownIcon className="size-4" aria-hidden />
        </Combobox.Trigger>
      </Combobox.InputGroup>

      <Combobox.Portal>
        <Combobox.Positioner sideOffset={4} className="isolate z-50 outline-none">
          <Combobox.Popup className="w-(--anchor-width) max-w-(--available-width) origin-(--transform-origin) overflow-hidden rounded-lg bg-popover text-popover-foreground shadow-md ring-1 ring-foreground/10 duration-100 data-open:animate-in data-open:fade-in-0 data-closed:animate-out data-closed:fade-out-0">
            <Combobox.Empty className="px-3 py-2 text-sm text-muted-foreground empty:hidden">
              Ninguna explotación coincide con lo que has escrito.
            </Combobox.Empty>
            <Combobox.List className="max-h-[min(20rem,var(--available-height))] scroll-py-1 overflow-y-auto overscroll-contain p-1 data-empty:p-0">
              {(explotacion: Explotacion) => (
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
            <AvisoMasCoincidencias />
          </Combobox.Popup>
        </Combobox.Positioner>
      </Combobox.Portal>
    </Combobox.Root>
  );
}

/**
 * m1 (revisión 9b): el aviso depende de las coincidencias de lo escrito, no del total, y va en una
 * región `status` (Combobox.Status) siempre montada con la lista, para que se anuncie.
 */
function AvisoMasCoincidencias() {
  const coincidencias = Combobox.useFilteredItems<Explotacion>();
  const hayMas = coincidencias.length > LIMITE_OPCIONES_EXPLOTACION;
  return (
    <Combobox.Status className={hayMas ? "border-t px-3 py-1.5 text-xs text-muted-foreground" : undefined}>
      {hayMas ? `Hay más de ${LIMITE_OPCIONES_EXPLOTACION} coincidencias: escribe para acotar.` : null}
    </Combobox.Status>
  );
}
