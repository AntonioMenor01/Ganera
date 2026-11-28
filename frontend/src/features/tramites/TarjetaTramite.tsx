import { ChevronRightIcon, TriangleAlertIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { avisosExtraccion, etiquetaTipoTramite, iconoTipoTramite } from "./etiquetas";
import { BadgeEstadoTramite } from "./BadgesTramite";
import { IconoTramite } from "./IconoTramite";
import type { Tramite } from "./types";

/**
 * Tarjeta de un trámite en una columna de tablero (Inicio y Control). Es un botón entero: abre la
 * revisión en la misma pantalla (sin navegar). Muestra el tipo con su icono, el GANADERO dueño
 * (MEJ-09: el gestor piensa en clientes, no en códigos REGA), la explotación (REGA · nombre) y el nº
 * de crotales, y el badge del estado real. Una sola fuente para que el Inicio y el Control se vean
 * igual. Si el backend no envía `ganaderoNombre` (anterior a MEJ-09), la tarjeta se ve como antes.
 */
export function TarjetaTramite({
  tramite,
  onRevisar,
}: {
  tramite: Tramite;
  onRevisar: (id: number) => void;
}) {
  const icono = iconoTipoTramite(tramite.tipoTramite);
  const ganadero = tramite.ganaderoNombre?.trim() || undefined;
  const nombreExplotacion = tramite.explotacionNombre?.trim() || undefined;
  const crotales = tramite.crotales?.length ?? 0;
  // Mismo criterio que la cola anterior: mientras el trámite está pendiente, una línea de 12px dice
  // el estado de la extracción (p. ej. «Parece un mensaje sin trámite», «No se ha podido extraer»).
  const { extraccion } = avisosExtraccion(tramite);
  return (
    <button
      type="button"
      aria-label={`Revisar trámite #${tramite.id}`}
      onClick={() => onRevisar(tramite.id)}
      className="flex w-full items-center gap-3 px-5 py-3.5 text-left outline-none transition-colors hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring"
    >
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="flex items-center gap-2 font-medium">
          {icono && <IconoTramite nombre={icono} className="size-4 shrink-0 text-muted-foreground" />}
          <span className="truncate">
            {tramite.tipoTramite ? etiquetaTipoTramite(tramite.tipoTramite) : "Sin determinar"}
          </span>
        </span>
        {ganadero && (
          <span className="min-w-0 truncate text-sm font-medium text-foreground" title={ganadero}>
            {ganadero}
          </span>
        )}
        <span className="min-w-0 truncate text-sm text-muted-foreground">
          {tramite.explotacionCodigoRega ? (
            <>
              <span className="tabular-nums">{tramite.explotacionCodigoRega}</span>
              {nombreExplotacion && <span> · {nombreExplotacion}</span>}
            </>
          ) : (
            "Sin explotación asignada"
          )}
          {crotales > 0 && (
            <>
              {" · "}
              <span className="tabular-nums">{crotales}</span> {crotales === 1 ? "crotal" : "crotales"}
            </>
          )}
        </span>
        <span className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1">
          <BadgeEstadoTramite estado={tramite.estado} motivoRechazo={tramite.motivoRechazo} />
          {extraccion && (
            <span
              className={cn(
                "inline-flex items-center gap-1 text-xs",
                extraccion.tono === "aviso" ? "text-warning-foreground" : "text-muted-foreground",
              )}
            >
              {extraccion.tono === "aviso" && (
                <TriangleAlertIcon aria-hidden="true" className="size-3.5 shrink-0" />
              )}
              {extraccion.cola}
            </span>
          )}
        </span>
      </span>
      <ChevronRightIcon aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
    </button>
  );
}
