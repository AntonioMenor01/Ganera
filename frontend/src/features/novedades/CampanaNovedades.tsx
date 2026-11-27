import { useEffect, useId, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { BellIcon, CircleAlertIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { CLASE_ENLACE } from "@/shared/ui/enlace";
import { mensajeDeError } from "@/shared/api/errores";
import type { VarianteBadge } from "@/features/tramites/etiquetas";
import { useNovedades } from "./useNovedades";
import { textoContador, type ItemNovedad } from "./novedades";

/** Punto de color de cada novedad según su severidad (mismos tokens que los badges del sistema). */
const COLOR_PUNTO: Record<VarianteBadge, string> = {
  danger: "bg-danger",
  warning: "bg-warning-foreground",
  success: "bg-success-foreground",
  outline: "bg-muted-foreground",
};

/**
 * Campana de novedades: indicador siempre visible (desde cualquier pantalla) de los plazos y avisos
 * urgentes (idea #2). Badge con el número; al abrir, un panel con la lista, cada una enlazando a su
 * trámite o al calendario. Accesible: aria-expanded/haspopup, cierra con Esc o clic fuera, y al
 * abrir refresca para no quedarse obsoleta.
 */
export function CampanaNovedades() {
  const { items, total, cargando, error, recargar } = useNovedades();
  const [abierto, setAbierto] = useState(false);
  const refContenedor = useRef<HTMLDivElement>(null);
  const panelId = useId();
  const contador = textoContador(total);

  useEffect(() => {
    if (!abierto) return;
    recargar();
    function fuera(e: MouseEvent) {
      if (refContenedor.current && !refContenedor.current.contains(e.target as Node)) setAbierto(false);
    }
    function esc(e: KeyboardEvent) {
      if (e.key === "Escape") setAbierto(false);
    }
    document.addEventListener("mousedown", fuera);
    document.addEventListener("keydown", esc);
    return () => {
      document.removeEventListener("mousedown", fuera);
      document.removeEventListener("keydown", esc);
    };
    // recargar es estable (useCallback); solo depende de abrir/cerrar.
  }, [abierto, recargar]);

  return (
    <div ref={refContenedor} className="relative">
      <button
        type="button"
        onClick={() => setAbierto((v) => !v)}
        aria-haspopup="true"
        aria-expanded={abierto}
        aria-controls={panelId}
        aria-label={contador ? `Novedades (${total})` : "Novedades"}
        className="relative grid size-9 place-items-center rounded-sm text-foreground outline-none hover:bg-gris-200 focus-visible:ring-3 focus-visible:ring-ring"
      >
        <BellIcon aria-hidden="true" className="size-[18px]" />
        {contador && (
          <span
            aria-hidden="true"
            className="absolute -top-0.5 -right-0.5 grid min-w-4 place-items-center bg-danger px-1 text-[10px] leading-4 font-bold text-danger-foreground tabular-nums"
          >
            {contador}
          </span>
        )}
      </button>

      {abierto && (
        <div
          id={panelId}
          role="region"
          aria-label="Novedades"
          className="absolute right-0 top-[calc(100%+8px)] z-30 flex max-h-[min(70vh,28rem)] w-[min(92vw,22rem)] flex-col border border-border bg-card shadow-lg lg:right-auto lg:left-0 lg:top-auto lg:bottom-[calc(100%+8px)]"
        >
          <div className="flex items-center justify-between gap-2 border-b border-border px-4 py-3">
            <span className="text-sm font-bold">Novedades</span>
            {!cargando && !error && (
              <span className="text-[13px] text-muted-foreground tabular-nums">
                {total === 0 ? "al día" : `${total} ${total === 1 ? "aviso" : "avisos"}`}
              </span>
            )}
          </div>

          <div className="min-h-0 flex-1 overflow-auto">
            {cargando ? (
              <p className="px-4 py-6 text-sm text-muted-foreground">Cargando…</p>
            ) : error ? (
              <div className="px-4 py-5">
                <p className="flex items-center gap-2 text-sm text-destructive">
                  <CircleAlertIcon className="size-4 shrink-0" />
                  {mensajeDeError(error, "listar-tramites")}
                </p>
                <button type="button" onClick={recargar} className={cn(CLASE_ENLACE, "mt-2 text-[13px] font-medium")}>
                  Reintentar
                </button>
              </div>
            ) : items.length === 0 ? (
              <p className="px-4 py-6 text-sm text-muted-foreground">
                Estás al día. No hay plazos ni trámites atascados ahora mismo.
              </p>
            ) : (
              <ul className="divide-y divide-border">
                {items.map((item) => (
                  <FilaNovedad key={item.id} item={item} onIr={() => setAbierto(false)} />
                ))}
              </ul>
            )}
          </div>

          <div className="flex items-center justify-between gap-3 border-t border-border px-4 py-2.5">
            <Link
              to="/tramites"
              onClick={() => setAbierto(false)}
              className={cn(CLASE_ENLACE, "text-[13px] font-medium")}
            >
              Ver los trámites
            </Link>
            <Link
              to="/calendario"
              onClick={() => setAbierto(false)}
              className={cn(CLASE_ENLACE, "text-[13px] font-medium")}
            >
              Ver el calendario
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}

function FilaNovedad({ item, onIr }: { item: ItemNovedad; onIr: () => void }) {
  return (
    <li>
      <Link
        to={item.to}
        state={item.tramiteId != null ? { abrirTramiteId: item.tramiteId } : undefined}
        onClick={onIr}
        className="flex gap-2.5 px-4 py-3 outline-none transition-colors hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring"
      >
        <span aria-hidden="true" className={cn("mt-1.5 size-2 shrink-0 rounded-full", COLOR_PUNTO[item.variante])} />
        <span className="flex min-w-0 flex-col">
          <span className="text-sm font-medium">{item.titulo}</span>
          <span className="text-[13px] text-muted-foreground">{item.detalle}</span>
        </span>
      </Link>
    </li>
  );
}
