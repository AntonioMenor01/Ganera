import { useEffect, useId, useRef, useState } from "react";
import { ArrowLeftIcon, ArrowRightIcon, CheckIcon, XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { LogoGanera } from "@/shared/brand/LogoGanera";
import { usePrimerosPasos } from "./PrimerosPasosContexto";

/**
 * Guía visual de primeros pasos (overlay a pantalla completa). Lee todo del contexto
 * ({@link usePrimerosPasos}): qué pasos hay, cuáles están hechos, por dónde va y el requisito de
 * OVZ. Al pulsar la CTA de un paso lleva a la pantalla real SIN terminar el onboarding (se puede
 * volver a continuar). No se puede «Empezar» (terminar) sin el paso de OVZ hecho.
 */
export function PrimerosPasosGuia() {
  const { abierta, pasos, indiceActivo, hechos, total, ovzHecho, cerrar, confirmarPaso, irAPantalla, terminar } =
    usePrimerosPasos();
  const [indice, setIndice] = useState(0);
  const tituloId = useId();
  const tituloRef = useRef<HTMLHeadingElement>(null);

  // Al ABRIR (transición cerrada→abierta), empieza por el primer paso no hecho. No se reposiciona
  // si `indiceActivo` cambia con la guía ya abierta (la detección reordena al confirmar pasos): eso
  // se pelearía con la navegación manual (Siguiente/Atrás).
  const estabaAbierta = useRef(false);
  useEffect(() => {
    if (abierta && !estabaAbierta.current) setIndice(indiceActivo);
    estabaAbierta.current = abierta;
  }, [abierta, indiceActivo]);

  useEffect(() => {
    if (abierta) tituloRef.current?.focus();
  }, [abierta, indice]);

  if (!abierta) return null;

  const actual = pasos[Math.min(indice, pasos.length - 1)];
  const { paso, completado } = actual;
  const esPrimero = indice === 0;
  const esUltimo = indice === total - 1;
  const Icono = paso.Icono;
  const esExplicativo = !paso.deteccion && !esUltimo;

  function atras() {
    setIndice((i) => Math.max(0, i - 1));
  }
  function siguiente() {
    if (esExplicativo) confirmarPaso(paso.id);
    setIndice((i) => Math.min(total - 1, i + 1));
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={tituloId}
      onKeyDown={(e) => {
        if (e.key === "Escape") cerrar();
      }}
      className="fixed inset-0 z-50 flex flex-col bg-background"
    >
      <header className="flex h-16 flex-none items-center gap-4 border-b-2 border-divisor px-5 sm:px-8">
        <LogoGanera decorativo className="size-[22px] text-marca" />
        <span className="font-bold tracking-wide">GANERA</span>
        <span aria-hidden="true" className="hidden h-5 w-px bg-border sm:block" />
        <span className="hidden text-sm font-medium text-muted-foreground sm:block">Configuración inicial</span>
        <span className="ml-auto text-sm tabular-nums text-muted-foreground">
          Paso {indice + 1} de {total} · {hechos} de {total} hechos
        </span>
        <Button type="button" variant="ghost" size="icon" onClick={cerrar} aria-label="Cerrar la guía" className="shrink-0">
          <XIcon className="size-5" />
        </Button>
      </header>

      {/* Progreso por paso: hecho = acción; el actual, tintado; el resto, neutro. */}
      <div
        className="grid flex-none gap-1 px-5 pt-4 sm:px-8"
        style={{ gridTemplateColumns: `repeat(${total}, minmax(0, 1fr))` }}
        aria-hidden="true"
      >
        {pasos.map((p) => (
          <span
            key={p.paso.id}
            className={`h-1 ${p.completado ? "bg-primary" : p.indice === indice ? "bg-primary/40" : "bg-muted"}`}
          />
        ))}
      </div>

      <main className="flex-1 overflow-auto">
        <div className="mx-auto flex max-w-[620px] flex-col gap-6 px-5 py-12 sm:px-8 sm:py-14">
          <span
            className={`grid size-12 place-items-center ${completado ? "bg-success text-success-foreground" : "bg-muted text-foreground"}`}
          >
            {completado ? <CheckIcon aria-hidden="true" className="size-6" /> : <Icono aria-hidden="true" className="size-6" />}
          </span>
          <div className="flex flex-col gap-2">
            <h1 id={tituloId} ref={tituloRef} tabIndex={-1} className="text-[28px] leading-tight font-bold tracking-[-0.02em] outline-none">
              {paso.titulo}
            </h1>
            {completado && (
              <p className="text-sm font-medium text-success-foreground">✓ Hecho</p>
            )}
            <p className="text-[15px] leading-relaxed text-pretty text-muted-foreground">{paso.cuerpo}</p>
          </div>
          {paso.cta && !completado && (
            <div>
              <Button type="button" variant="outline" className="h-11 gap-2 bg-card px-4" onClick={() => irAPantalla(paso.cta!.to)}>
                {paso.cta.label}
                <ArrowRightIcon aria-hidden="true" className="size-[18px]" />
              </Button>
              {paso.deteccion && (
                <p className="mt-2 text-[13px] text-muted-foreground">
                  Lo marcaremos como hecho en cuanto lo tengas listo; vuelve aquí para continuar.
                </p>
              )}
            </div>
          )}
          {/* Último paso: no se puede terminar sin OVZ (requisito). */}
          {esUltimo && !ovzHecho && (
            <div className="border border-border bg-warning px-4 py-3 text-[13px] text-warning-foreground">
              <p className="font-medium">Falta configurar tus datos de OVZ para terminar.</p>
              <Button
                type="button"
                variant="outline"
                className="mt-2 h-9 gap-2 bg-card px-3"
                onClick={() => irAPantalla("/configuracion")}
              >
                Configurar OVZ ahora
                <ArrowRightIcon aria-hidden="true" className="size-4" />
              </Button>
            </div>
          )}
        </div>
      </main>

      <footer className="flex h-[72px] flex-none items-center gap-3 border-t-2 border-divisor px-5 sm:px-8">
        {!esPrimero && (
          <Button type="button" variant="outline" className="h-11 gap-2 bg-card px-4" onClick={atras}>
            <ArrowLeftIcon aria-hidden="true" className="size-[18px]" />
            Atrás
          </Button>
        )}
        <span className="flex-1" />
        {!esUltimo && (
          <Button type="button" variant="ghost" className="h-11 px-4 text-enlace" onClick={cerrar}>
            Omitir por ahora
          </Button>
        )}
        {esUltimo ? (
          <Button
            type="button"
            onClick={terminar}
            disabled={!ovzHecho}
            className="h-11 min-w-[180px] justify-between px-4 text-[15px] font-medium"
          >
            Empezar
            <ArrowRightIcon aria-hidden="true" className="size-[18px]" />
          </Button>
        ) : (
          <Button
            type="button"
            onClick={siguiente}
            className="h-11 min-w-[180px] justify-between px-4 text-[15px] font-medium"
          >
            Siguiente
            <ArrowRightIcon aria-hidden="true" className="size-[18px]" />
          </Button>
        )}
      </footer>
    </div>
  );
}
