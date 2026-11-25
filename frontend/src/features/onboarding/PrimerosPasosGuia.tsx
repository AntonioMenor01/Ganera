import { useEffect, useId, useRef, useState } from "react";
import { ArrowLeftIcon, ArrowRightIcon, XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { LogoGanera } from "@/shared/brand/LogoGanera";
import { PASOS_GUIA } from "./primerosPasos";

interface Props {
  open: boolean;
  /** Cerrar la guía (botón X, «Omitir» o «Empezar»). El contenedor marca «vista» aquí. */
  onClose: () => void;
  /** Ir a una pantalla real (una CTA de un paso). El contenedor navega y cierra. */
  onIr: (to: string) => void;
}

/**
 * Guía visual de primeros pasos (overlay a pantalla completa), inspirada en el onboarding del mock
 * «Ganera App v2». Es presentacional: no sabe de auth, almacenamiento ni routing; el contenedor
 * ({@link PrimerosPasosRoot}) decide cuándo abrirla, la marca como vista y navega. Pasos en
 * {@link PASOS_GUIA}. Accesible: role dialog, Esc cierra, el foco entra en el título al abrir.
 */
export function PrimerosPasosGuia({ open, onClose, onIr }: Props) {
  const [indice, setIndice] = useState(0);
  const tituloId = useId();
  const tituloRef = useRef<HTMLHeadingElement>(null);

  // Al abrir, siempre desde el primer paso, y el foco al título (lector de pantalla lo anuncia).
  useEffect(() => {
    if (open) {
      setIndice(0);
    }
  }, [open]);

  useEffect(() => {
    if (open) tituloRef.current?.focus();
  }, [open, indice]);

  if (!open) return null;

  const total = PASOS_GUIA.length;
  const paso = PASOS_GUIA[indice];
  const esPrimero = indice === 0;
  const esUltimo = indice === total - 1;
  const Icono = paso.Icono;

  function atras() {
    setIndice((i) => Math.max(0, i - 1));
  }
  function siguiente() {
    if (esUltimo) {
      onClose();
    } else {
      setIndice((i) => Math.min(total - 1, i + 1));
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={tituloId}
      onKeyDown={(e) => {
        if (e.key === "Escape") onClose();
      }}
      className="fixed inset-0 z-50 flex flex-col bg-background"
    >
      <header className="flex h-16 flex-none items-center gap-4 border-b-2 border-divisor px-5 sm:px-8">
        <LogoGanera decorativo className="size-[22px] text-marca" />
        <span className="font-bold tracking-wide">GANERA</span>
        <span aria-hidden="true" className="hidden h-5 w-px bg-border sm:block" />
        <span className="hidden text-sm font-medium text-muted-foreground sm:block">
          Configuración inicial
        </span>
        <span className="ml-auto text-sm tabular-nums text-muted-foreground">
          Paso {indice + 1} de {total}
        </span>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={onClose}
          aria-label="Cerrar la guía"
          className="shrink-0"
        >
          <XIcon className="size-5" />
        </Button>
      </header>

      {/* Barra de progreso: un segmento por paso, los ya pasados y el actual en color de acción. */}
      <div
        className="grid flex-none gap-1 px-5 pt-4 sm:px-8"
        style={{ gridTemplateColumns: `repeat(${total}, minmax(0, 1fr))` }}
        aria-hidden="true"
      >
        {PASOS_GUIA.map((p, i) => (
          <span
            key={p.id}
            className={`h-1 ${i <= indice ? "bg-primary" : "bg-muted"}`}
          />
        ))}
      </div>

      <main className="flex-1 overflow-auto">
        <div className="mx-auto flex max-w-[620px] flex-col gap-6 px-5 py-12 sm:px-8 sm:py-14">
          <span className="grid size-12 place-items-center bg-muted text-foreground">
            <Icono aria-hidden="true" className="size-6" />
          </span>
          <div className="flex flex-col gap-2">
            <h1
              id={tituloId}
              ref={tituloRef}
              tabIndex={-1}
              className="text-[28px] leading-tight font-bold tracking-[-0.02em] outline-none"
            >
              {paso.titulo}
            </h1>
            <p className="text-[15px] leading-relaxed text-pretty text-muted-foreground">
              {paso.cuerpo}
            </p>
          </div>
          {paso.cta && (
            <div>
              <Button
                type="button"
                variant="outline"
                className="h-11 gap-2 bg-card px-4"
                onClick={() => onIr(paso.cta!.to)}
              >
                {paso.cta.label}
                <ArrowRightIcon aria-hidden="true" className="size-[18px]" />
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
          <Button type="button" variant="ghost" className="h-11 px-4 text-enlace" onClick={onClose}>
            Omitir por ahora
          </Button>
        )}
        <Button
          type="button"
          onClick={siguiente}
          className="h-11 min-w-[180px] justify-between px-4 text-[15px] font-medium"
        >
          {esUltimo ? "Empezar" : "Siguiente"}
          <ArrowRightIcon aria-hidden="true" className="size-[18px]" />
        </Button>
      </footer>
    </div>
  );
}
