import { Badge } from "@/components/ui/badge";
import {
  presentacionEstado,
  presentacionCrotal,
  presentacionRolContacto,
  type Presentacion,
} from "./etiquetas";

/** Siempre con texto, nunca solo color; el par de color sale de `etiquetas.ts`. */
function BadgePresentacion({ presentacion }: { presentacion: Presentacion }) {
  return <Badge variant={presentacion.variante}>{presentacion.etiqueta}</Badge>;
}

export function BadgeEstadoTramite({ estado }: { estado: string }) {
  return <BadgePresentacion presentacion={presentacionEstado(estado)} />;
}

/** Recibe el crotal (no solo la resolución) porque `completo` matiza `NO_ENCONTRADO`. */
export function BadgeResolucionCrotal({
  crotal,
}: {
  crotal: { resolucion: string; completo?: boolean };
}) {
  return <BadgePresentacion presentacion={presentacionCrotal(crotal)} />;
}

export function BadgeRolContacto({ rol }: { rol: string }) {
  return <BadgePresentacion presentacion={presentacionRolContacto(rol)} />;
}
