import { CompassIcon } from "lucide-react";
import { usePrimerosPasos } from "./PrimerosPasosContexto";

/**
 * Acceso persistente a la guía mientras el onboarding esté incompleto: una píldora flotante
 * «Continuar configuración · paso X de N» que reabre la guía por donde iba. Se oculta cuando la
 * guía está abierta, cuando el onboarding ya terminó o cuando no está en curso.
 */
export function PillContinuar() {
  const { activo, abierta, terminada, indiceActivo, total, abrir } = usePrimerosPasos();
  if (!activo || abierta || terminada) return null;
  return (
    <div className="fixed right-4 bottom-4 z-40 print:hidden">
      <button
        type="button"
        onClick={abrir}
        className="flex items-center gap-2 border border-primary bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground shadow-lg outline-none transition-colors hover:bg-primary-hover focus-visible:ring-3 focus-visible:ring-ring"
      >
        <CompassIcon aria-hidden="true" className="size-4" />
        Continuar configuración
        <span className="tabular-nums opacity-90">
          · paso {indiceActivo + 1} de {total}
        </span>
      </button>
    </div>
  );
}
