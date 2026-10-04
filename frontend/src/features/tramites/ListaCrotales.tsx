import { useEffect, useRef } from "react";
import { ArrowRightIcon, PlusIcon, XIcon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { BadgeResolucionCrotal } from "./BadgesTramite";
import type { TramiteCrotal } from "./types";

/**
 * Crotales del trámite (decisiones 7 y 31). Los badges muestran lo GUARDADO, nunca una predicción:
 * una fila editada o nueva enseña "Sin guardar" hasta que se guarda y responde el backend.
 */

/** Lo guardado para el valor de una fila del formulario, o null si esa fila no está guardada tal
 * cual. Por valor (no por posición): quitar una fila no convierte en "editadas" a las siguientes. */
function guardadoDe(valor: string, guardados: readonly TramiteCrotal[]): TramiteCrotal | null {
  const limpio = valor.trim();
  if (limpio === "") return null;
  return guardados.find((crotal) => crotal.crotalIndicado === limpio) ?? null;
}

/** Crotal completo resuelto, solo si difiere de lo indicado (tabular, para cotejarlo). */
function CrotalResuelto({ crotal }: { crotal: TramiteCrotal }) {
  if (!crotal.crotal || crotal.crotal === crotal.crotalIndicado) return null;
  return (
    <span className="text-muted-foreground tabular-nums wrap-anywhere">
      <span className="sr-only">Crotal completo: </span>
      <ArrowRightIcon aria-hidden className="mr-1 inline size-3.5 align-[-2px]" />
      {crotal.crotal}
    </span>
  );
}

/** Marca provisional (no un resultado): borde discontinuo para no confundirse con el outline
 * guardado de "No está en el inventario" (finish review #5). */
export function BadgeSinGuardar() {
  return (
    <Badge variant="outline" className="border-dashed border-foreground/25">
      Sin guardar
    </Badge>
  );
}

export function CrotalesSoloLectura({ crotales }: { crotales: readonly TramiteCrotal[] }) {
  if (crotales.length === 0) return <p className="text-muted-foreground">Sin crotales.</p>;
  return (
    <ul aria-label="Crotales" className="flex flex-col divide-y">
      {crotales.map((crotal, indice) => (
        <li
          key={`${indice}-${crotal.crotalIndicado}`}
          className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2 first:pt-0 last:pb-0"
        >
          <span className="font-medium tabular-nums wrap-anywhere">{crotal.crotalIndicado}</span>
          <CrotalResuelto crotal={crotal} />
          <BadgeResolucionCrotal crotal={crotal} />
        </li>
      ))}
    </ul>
  );
}

interface ListaCrotalesEditableProps {
  valores: readonly string[];
  guardados: readonly TramiteCrotal[];
  /** La explotación del formulario no es la guardada: todas las resoluciones cambiarán al guardar. */
  explotacionCambiada: boolean;
  deshabilitado: boolean;
  onCambiar: (indice: number, valor: string) => void;
  onQuitar: (indice: number) => void;
  onAnadir: () => void;
}

export function ListaCrotalesEditable({
  valores,
  guardados,
  explotacionCambiada,
  deshabilitado,
  onCambiar,
  onQuitar,
  onAnadir,
}: ListaCrotalesEditableProps) {
  const campos = useRef<(HTMLInputElement | null)[]>([]);
  const botonAnadir = useRef<HTMLButtonElement | null>(null);
  /** Índice que debe recibir el foco tras el próximo render (añadir o quitar), o "anadir". */
  const focoPendiente = useRef<number | "anadir" | null>(null);

  useEffect(() => {
    const destino = focoPendiente.current;
    if (destino === null) return;
    focoPendiente.current = null;
    if (destino === "anadir") botonAnadir.current?.focus();
    else campos.current[destino]?.focus();
  });

  function anadir() {
    focoPendiente.current = valores.length;
    onAnadir();
  }

  function quitar(indice: number) {
    // El foco no se pierde: pasa a la fila que ocupa su lugar, a la anterior o a "Añadir crotal".
    const quedan = valores.length - 1;
    focoPendiente.current = quedan === 0 ? "anadir" : Math.min(indice, quedan - 1);
    onQuitar(indice);
  }

  return (
    <div className="flex flex-col gap-3">
      {valores.length === 0 ? (
        <p className="text-muted-foreground">Sin crotales.</p>
      ) : (
        // Una sola rejilla para todas las filas (subgrid): todos los campos miden lo mismo y el badge
        // va en su columna. En móvil: campo + quitar en la primera línea y el badge siempre debajo.
        <ul
          aria-label="Crotales"
          className="grid grid-cols-[minmax(0,1fr)_auto] gap-x-2 gap-y-2 sm:grid-cols-[minmax(0,1fr)_minmax(11rem,auto)_auto]"
        >
          {valores.map((valor, indice) => {
            const guardado = explotacionCambiada ? null : guardadoDe(valor, guardados);
            const nombre = valor.trim() || `${indice + 1} (vacío)`;
            return (
              <li key={indice} className="col-span-full grid grid-cols-subgrid items-center gap-y-1">
                <Input
                  ref={(el) => {
                    campos.current[indice] = el;
                  }}
                  aria-label={`Crotal ${indice + 1}`}
                  value={valor}
                  onChange={(evento) => onCambiar(indice, evento.target.value)}
                  disabled={deshabilitado}
                  autoComplete="off"
                  spellCheck={false}
                  placeholder="Últimos dígitos o crotal completo"
                  className="col-start-1 row-start-1 min-w-0 tabular-nums"
                />
                <span className="col-start-1 row-start-2 justify-self-start sm:col-start-2 sm:row-start-1">
                  {guardado ? <BadgeResolucionCrotal crotal={guardado} /> : <BadgeSinGuardar />}
                </span>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  aria-label={`Quitar crotal ${nombre}`}
                  onClick={() => quitar(indice)}
                  disabled={deshabilitado}
                  className="col-start-2 row-start-1 sm:col-start-3"
                >
                  <XIcon aria-hidden />
                </Button>
                {guardado && guardado.crotal !== guardado.crotalIndicado && (
                  <span className="col-span-full row-start-3 pl-2.5 text-sm sm:row-start-2">
                    <CrotalResuelto crotal={guardado} />
                  </span>
                )}
              </li>
            );
          })}
        </ul>
      )}
      <Button
        ref={botonAnadir}
        type="button"
        variant="outline"
        size="sm"
        onClick={anadir}
        disabled={deshabilitado}
        className="self-start"
      >
        <PlusIcon aria-hidden />
        Añadir crotal
      </Button>
    </div>
  );
}
