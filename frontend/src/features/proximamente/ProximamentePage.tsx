import { Link } from "react-router-dom";
import { CLASE_ENLACE } from "@/shared/ui/enlace";

type Props = {
  titulo: string;
  descripcion: string;
};

/**
 * Pantallas del mock aún sin backend (bandeja WhatsApp, calendario, informes, ajustes).
 * Honestidad: no inventa datos; explica qué falta y enlaza al trabajo real de hoy.
 */
export function ProximamentePage({ titulo, descripcion }: Props) {
  return (
    <div className="mx-auto flex max-w-xl flex-col gap-4">
      <header className="flex flex-col gap-1">
        <h1 className="text-xl font-bold tracking-tight">{titulo}</h1>
        <p className="text-sm text-muted-foreground">{descripcion}</p>
      </header>
      <p className="rounded-sm border border-border bg-card px-4 py-3 text-sm">
        Esta sección está en el diseño y llegará cuando el backend esté listo. Mientras tanto, el
        trabajo diario sigue en{" "}
        <Link to="/" className={CLASE_ENLACE}>
          Inicio
        </Link>
        ,{" "}
        <Link to="/tramites" className={CLASE_ENLACE}>
          Trámites
        </Link>{" "}
        y{" "}
        <Link to="/ganaderos" className={CLASE_ENLACE}>
          Ganaderos
        </Link>
        .
      </p>
    </div>
  );
}
