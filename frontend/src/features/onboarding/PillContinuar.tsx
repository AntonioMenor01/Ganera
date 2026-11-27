import { useState } from "react";
import { CompassIcon, XIcon } from "lucide-react";
import { usePrimerosPasos } from "./PrimerosPasosContexto";

/** Clave de sesión para ocultar la píldora hasta cerrar la pestaña (MEJ-06: era intrusiva). */
const CLAVE_OCULTA = "ganera.pill-continuar.oculta";

function leerOculta(): boolean {
  try {
    return sessionStorage.getItem(CLAVE_OCULTA) === "1";
  } catch {
    return false;
  }
}

/**
 * Acceso persistente a la guía mientras el onboarding esté incompleto: una píldora flotante
 * «Continuar configuración · N/Total» que reabre la guía por donde iba. Muestra el MISMO contador de
 * progreso que el Inicio y la guía (pasos hechos / total), para no contradecirse (MEJ-05: antes decía
 * «paso X de N», un número distinto que parecía un error). Se puede descartar durante la sesión con la
 * «×» (MEJ-06); el botón «Primeros pasos» del Inicio sigue reabriendo la guía. Se oculta también
 * cuando la guía está abierta, cuando el onboarding terminó o cuando no está en curso.
 */
export function PillContinuar() {
  const { activo, abierta, terminada, hechos, total, abrir } = usePrimerosPasos();
  const [oculta, setOculta] = useState(leerOculta);

  if (!activo || abierta || terminada || oculta) return null;

  function descartar() {
    try {
      sessionStorage.setItem(CLAVE_OCULTA, "1");
    } catch {
      /* sessionStorage no disponible: al menos se oculta en memoria */
    }
    setOculta(true);
  }

  return (
    <div className="fixed right-4 bottom-4 z-40 flex items-stretch shadow-lg print:hidden">
      <button
        type="button"
        onClick={abrir}
        className="flex items-center gap-2 border border-primary bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground outline-none transition-colors hover:bg-primary-hover focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring"
      >
        <CompassIcon aria-hidden="true" className="size-4" />
        Continuar configuración
        <span className="tabular-nums opacity-90">
          · {hechos}/{total}
        </span>
      </button>
      <button
        type="button"
        onClick={descartar}
        aria-label="Ocultar recordatorio"
        className="flex items-center border border-l-0 border-primary bg-primary px-2 text-primary-foreground outline-none transition-colors hover:bg-primary-hover focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring"
      >
        <XIcon aria-hidden="true" className="size-4" />
      </button>
    </div>
  );
}
