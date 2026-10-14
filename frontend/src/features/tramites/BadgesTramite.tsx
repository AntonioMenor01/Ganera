import { Badge } from "@/components/ui/badge";
import { IconoTramite } from "./IconoTramite";
import {
  iconoEstadoTramite,
  presentacionEstadoTramite,
  presentacionCrotal,
  presentacionRolContacto,
  type Presentacion,
} from "./etiquetas";

/** Siempre con texto, nunca solo color; el par de color sale de `etiquetas.ts`. */
function BadgePresentacion({ presentacion }: { presentacion: Presentacion }) {
  return <Badge variant={presentacion.variante}>{presentacion.etiqueta}</Badge>;
}

/** `motivoRechazo` (SIN_TRAMITE): un `RECHAZADO` descartado se ve «Descartado». Pásalo siempre que se
 * tenga (cola, modal y ficha), para que los tres digan lo mismo. */
export function BadgeEstadoTramite({ estado, motivoRechazo }: { estado: string; motivoRechazo?: string | null }) {
  const presentacion = presentacionEstadoTramite({ estado, motivoRechazo });
  // Icono a la izquierda, decorativo: el nombre accesible del badge sigue siendo solo la etiqueta.
  const icono = iconoEstadoTramite({ estado, motivoRechazo });
  return (
    <Badge variant={presentacion.variante}>
      {icono && <IconoTramite nombre={icono} />}
      {presentacion.etiqueta}
    </Badge>
  );
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
