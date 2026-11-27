import { NavLink } from "react-router-dom";
import { UsersIcon, WarehouseIcon, type LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * Subnavegación de la cartera: Ganaderos y Explotaciones son la misma cosa vista de dos maneras (un
 * ganadero es dueño de sus explotaciones), así que comparten una sola entrada en la barra
 * («Ganaderos») y se cambia entre las dos vistas con este control, sin salir de la sección. Son
 * enlaces reales (cambian de ruta), con `aria-current` en el activo; `/ganaderos/:id` mantiene
 * activo «Ganaderos» y `/explotaciones/:id`, «Explotaciones» (NavLink sin `end`).
 */
const PESTANAS: { to: string; label: string; Icono: LucideIcon }[] = [
  { to: "/ganaderos", label: "Ganaderos", Icono: UsersIcon },
  { to: "/explotaciones", label: "Explotaciones", Icono: WarehouseIcon },
];

export function SubnavCartera() {
  return (
    <nav aria-label="Cartera" className="flex w-fit max-w-full border border-input">
      {PESTANAS.map(({ to, label, Icono }, i) => (
        <NavLink
          key={to}
          to={to}
          className={({ isActive }) =>
            cn(
              "flex h-9 items-center gap-2 px-4 text-sm font-medium whitespace-nowrap outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring",
              i > 0 && "border-l border-input",
              isActive
                ? "bg-primary text-primary-foreground"
                : "bg-card text-foreground hover:bg-gris-200",
            )
          }
        >
          <Icono aria-hidden="true" className="size-4 shrink-0" />
          {label}
        </NavLink>
      ))}
    </nav>
  );
}
