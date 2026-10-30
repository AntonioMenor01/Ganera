import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { SearchIcon, TagIcon, UsersIcon, WarehouseIcon, type LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { mensajeDeError } from "@/shared/api/errores";
import { useBuscadorGlobal } from "./useBuscadorGlobal";
import { sinResultados } from "./types";

/**
 * Buscador global: un disparador con forma de campo en la barra (y atajo Ctrl/⌘+K) que abre una
 * paleta con los resultados agrupados (ganaderos, explotaciones, crotales). Cada resultado lleva a
 * la ficha del ganadero correspondiente. Autocontenido: se monta una vez en AppLayout; `className`
 * coloca el disparador.
 */
export function BuscadorGlobal({ className }: { className?: string }) {
  const [abierto, setAbierto] = useState(false);
  const [consulta, setConsulta] = useState("");
  const campoRef = useRef<HTMLInputElement>(null);
  const { busqueda, reintentar } = useBuscadorGlobal({ abierto, consulta });

  // Atajo Ctrl/⌘+K para abrir desde cualquier parte de la app.
  useEffect(() => {
    function alPulsar(evento: KeyboardEvent) {
      if ((evento.ctrlKey || evento.metaKey) && evento.key.toLowerCase() === "k") {
        evento.preventDefault();
        setAbierto(true);
      }
    }
    window.addEventListener("keydown", alPulsar);
    return () => window.removeEventListener("keydown", alPulsar);
  }, []);

  function cambiarApertura(siguiente: boolean) {
    setAbierto(siguiente);
    // Al cerrar, se olvida la consulta: reabrir parte de cero.
    if (!siguiente) setConsulta("");
  }

  function cerrarTrasNavegar() {
    cambiarApertura(false);
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setAbierto(true)}
        className={cn(
          "flex items-center gap-2 border border-input bg-card px-3 py-2 text-sm text-muted-foreground outline-none transition-colors hover:border-gris-600 focus-visible:ring-3 focus-visible:ring-ring",
          className,
        )}
      >
        <SearchIcon aria-hidden="true" className="size-4 shrink-0" />
        <span className="min-w-0 flex-1 truncate text-left">Buscar ganadero, crotal, REGA…</span>
        <kbd className="hidden shrink-0 items-center gap-0.5 border border-border px-1.5 py-0.5 font-sans text-[11px] font-medium text-muted-foreground sm:inline-flex">
          Ctrl K
        </kbd>
      </button>

      <Dialog open={abierto} onOpenChange={cambiarApertura}>
        <DialogContent
          showCloseButton={false}
          initialFocus={campoRef}
          className="top-[8dvh] left-1/2 max-h-[80dvh] w-full max-w-lg translate-x-[-50%] translate-y-0 gap-0 p-0 sm:max-w-lg"
        >
          <DialogTitle className="sr-only">Buscar en Ganera</DialogTitle>
          <div className="flex items-center gap-2 border-b-2 border-divisor px-3 py-2">
            <SearchIcon aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
            <Input
              ref={campoRef}
              value={consulta}
              onChange={(e) => setConsulta(e.target.value)}
              aria-label="Buscar ganadero, crotal o código REGA"
              placeholder="Buscar ganadero, crotal, REGA…"
              autoComplete="off"
              className="h-9 border-0 px-0 shadow-none focus-visible:ring-0"
            />
          </div>

          <div className="max-h-[60dvh] overflow-y-auto p-2">
            <Resultados busqueda={busqueda} consulta={consulta.trim()} onIr={cerrarTrasNavegar} onReintentar={reintentar} />
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

function Resultados({
  busqueda,
  consulta,
  onIr,
  onReintentar,
}: {
  busqueda: ReturnType<typeof useBuscadorGlobal>["busqueda"];
  consulta: string;
  onIr: () => void;
  onReintentar: () => void;
}) {
  if (busqueda.estado === "corta" || busqueda.estado === "inactiva") {
    return <p className="px-2 py-6 text-center text-sm text-muted-foreground">Escribe al menos 2 caracteres para buscar.</p>;
  }
  if (busqueda.estado === "buscando") {
    return (
      <p role="status" className="px-2 py-6 text-center text-sm text-muted-foreground">
        Buscando…
      </p>
    );
  }
  if (busqueda.estado === "error") {
    return (
      <div className="flex flex-col items-center gap-2 px-2 py-6 text-center">
        <p className="text-sm text-muted-foreground">{mensajeDeError(busqueda.error)}</p>
        <Button variant="outline" size="sm" onClick={onReintentar}>
          Reintentar
        </Button>
      </div>
    );
  }

  const { resultados } = busqueda;
  if (sinResultados(resultados)) {
    return (
      <p className="px-2 py-6 text-center text-sm text-muted-foreground">
        Sin resultados para «{consulta}».
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <Grupo titulo="Ganaderos" icono={UsersIcon} vacio={resultados.ganaderos.length === 0}>
        {resultados.ganaderos.map((g) => (
          <ItemResultado
            key={`g-${g.id}`}
            to={`/ganaderos/${g.id}`}
            onIr={onIr}
            principal={g.nombre}
            secundario={g.nif ?? "Sin NIF"}
          />
        ))}
      </Grupo>
      <Grupo titulo="Explotaciones" icono={WarehouseIcon} vacio={resultados.explotaciones.length === 0}>
        {resultados.explotaciones.map((e) => (
          <ItemResultado
            key={`e-${e.id}`}
            to={`/ganaderos/${e.ganaderoId}`}
            onIr={onIr}
            principal={e.codigoRega}
            secundario={e.nombre ? `${e.nombre} · ${e.ganaderoNombre}` : e.ganaderoNombre}
          />
        ))}
      </Grupo>
      <Grupo titulo="Crotales" icono={TagIcon} vacio={resultados.animales.length === 0}>
        {resultados.animales.map((a) => (
          <ItemResultado
            key={`a-${a.crotal}`}
            to={`/ganaderos/${a.ganaderoId}`}
            onIr={onIr}
            principal={a.crotal}
            secundario={`${a.explotacionCodigoRega} · ${a.ganaderoNombre}`}
          />
        ))}
      </Grupo>
    </div>
  );
}

function Grupo({
  titulo,
  icono: Icono,
  vacio,
  children,
}: {
  titulo: string;
  icono: LucideIcon;
  vacio: boolean;
  children: ReactNode;
}) {
  if (vacio) return null;
  return (
    <section aria-label={titulo}>
      <h2 className="flex items-center gap-1.5 px-2 pb-1 text-[11px] font-medium tracking-[0.08em] text-muted-foreground uppercase">
        <Icono aria-hidden="true" className="size-3.5" />
        {titulo}
      </h2>
      <ul>{children}</ul>
    </section>
  );
}

function ItemResultado({
  to,
  onIr,
  principal,
  secundario,
}: {
  to: string;
  onIr: () => void;
  principal: string;
  secundario: string;
}) {
  return (
    <li>
      <Link
        to={to}
        onClick={onIr}
        className="flex flex-col gap-0.5 px-2 py-2 outline-none transition-colors hover:bg-muted/60 focus-visible:bg-muted/60 focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring"
      >
        <span className="truncate font-medium tabular-nums">{principal}</span>
        <span className="truncate text-sm text-muted-foreground">{secundario}</span>
      </Link>
    </li>
  );
}
