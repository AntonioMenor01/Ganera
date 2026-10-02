import { Badge } from "@/components/ui/badge";
import {
  presentacionEstado,
  presentacionResolucion,
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

export function BadgeResolucionCrotal({ resolucion }: { resolucion: string }) {
  return <BadgePresentacion presentacion={presentacionResolucion(resolucion)} />;
}

export function BadgeRolContacto({ rol }: { rol: string }) {
  return <BadgePresentacion presentacion={presentacionRolContacto(rol)} />;
}
