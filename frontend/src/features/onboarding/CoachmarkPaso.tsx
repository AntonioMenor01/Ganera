import { CompassIcon, XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { PantallaPaso } from "./primerosPasos";
import { usePrimerosPasos } from "./PrimerosPasosContexto";

/**
 * Aviso contextual breve y descartable para una pantalla, atado al paso del onboarding que toca
 * hacer ahí (del mismo modelo de pasos, no texto suelto). Solo aparece mientras el onboarding esté
 * en curso, el paso no esté hecho y no se haya descartado antes (recordado por usuario+paso). No
 * bloquea: es un banner ligero, no un modal. Si no hay proveedor (pantalla suelta) o no toca, no
 * pinta nada.
 */
export function CoachmarkPaso({ pantalla }: { pantalla: PantallaPaso }) {
  const { coachParaPantalla, descartarCoach } = usePrimerosPasos();
  const paso = coachParaPantalla(pantalla);
  if (!paso || !paso.coach) return null;
  return (
    <aside
      aria-label="Ayuda de primeros pasos"
      className="mb-4 flex items-start gap-3 border border-primary/40 bg-primary/5 px-4 py-3"
    >
      <CompassIcon aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-primary" />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold">{paso.coach.titulo}</p>
        <p className="mt-0.5 text-[13px] text-muted-foreground">{paso.coach.cuerpo}</p>
      </div>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        aria-label="Descartar esta ayuda"
        onClick={() => descartarCoach(paso.id)}
        className="-mr-1 -mt-1 shrink-0"
      >
        <XIcon className="size-4" />
      </Button>
    </aside>
  );
}
