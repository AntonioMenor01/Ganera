import { useLayoutEffect, useRef } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/shared/auth/AuthContext";
import { LogoGanera } from "@/shared/brand/LogoGanera";
import { SuscripcionBanner } from "@/features/facturacion/SuscripcionBanner";
import { useSuscripcionEstado } from "@/features/facturacion/useSuscripcionEstado";
import { scrollLeftParaMostrar } from "./tiraNavegacion";

// Orden de trabajo: la cola (lo diario) y a quién pertenece (Ganaderos → sus explotaciones). El
// pago ya no se gestiona en la app (se cobra desde la landing): no hay enlace de Facturación.
// /ganaderos/:id también marca "Ganaderos" (NavLink sin `end`).
const ENLACES = [
  { to: "/tramites", label: "Trámites" },
  { to: "/ganaderos", label: "Ganaderos" },
  { to: "/explotaciones", label: "Explotaciones" },
];

/** Margen interior de la tira de enlaces en móvil (`px-4`): el activo se trae a la vista con él. */
const MARGEN_TIRA_PX = 16;

/**
 * Barra superior. Un solo <nav> en el DOM, en orden marca → enlaces → usuario/Salir (el orden de
 * tabulación es enlaces → Salir en todos los tamaños). Por debajo de `md` es una rejilla de dos
 * filas: marca y Salir arriba; la nav baja a la segunda fila como tira a sangre con scroll
 * horizontal interno. Desde `md`, una sola fila flex como siempre.
 */
/**
 * Mueve la tira (su scrollLeft, nunca scrollIntoView) lo justo para que `enlace` se vea entero con
 * el margen interior. Si ya se ve, no la toca. En escritorio la tira no desborda: no hace nada.
 */
function mostrarEnTira(tira: HTMLElement, enlace: HTMLElement) {
  const destino = scrollLeftParaMostrar({
    scrollLeft: tira.scrollLeft,
    anchoVisible: tira.clientWidth,
    // offsetLeft es relativo a la tira (es `relative`) y no depende del desplazamiento.
    inicio: enlace.offsetLeft,
    fin: enlace.offsetLeft + enlace.offsetWidth,
    margen: MARGEN_TIRA_PX,
  });
  if (destino !== tira.scrollLeft) tira.scrollLeft = destino;
}

export function AppLayout() {
  const { usuario, logout } = useAuth();
  const suscripcion = useSuscripcionEstado();
  const { pathname } = useLocation();
  const tiraRef = useRef<HTMLElement>(null);

  // Al montar y al cambiar de ruta, el enlace activo tiene que verse entero en la tira (móvil).
  // Se ajusta el scrollLeft de la propia tira, nunca con scrollIntoView: la página no se mueve en
  // vertical. Asignación directa = instantánea (sin animación, sea cual sea reduced-motion).
  // En escritorio la tira no desborda y el cálculo devuelve el mismo scrollLeft: no hace nada.
  // También al cambiar de tamaño la tira o el activo (girar el móvil, estrechar la ventana, una
  // fuente que carga tarde): un ResizeObserver repite el ajuste. Sin ResizeObserver (navegadores
  // muy antiguos, jsdom) solo se ajusta al montar y al navegar.
  useLayoutEffect(() => {
    const tira = tiraRef.current;
    const activo = tira?.querySelector<HTMLElement>('a[aria-current="page"]');
    if (!tira || !activo) return;
    const ajustar = () => mostrarEnTira(tira, activo);
    ajustar();
    if (typeof ResizeObserver === "undefined") return;
    const observador = new ResizeObserver(ajustar);
    observador.observe(tira);
    observador.observe(activo);
    return () => observador.disconnect();
  }, [pathname]);

  return (
    <div className="flex min-h-screen flex-col bg-background text-foreground">
      <header className="border-b border-sidebar-border bg-sidebar px-4 pt-3 pb-1 text-sidebar-foreground md:px-6 md:py-4">
        <div className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-y-2 md:flex md:gap-6">
          <div className="col-start-1 row-start-1 flex items-center gap-2 md:shrink-0">
            <LogoGanera decorativo className="size-5 text-primary" />
            <span className="font-semibold tracking-wide text-primary">GANERA</span>
          </div>
          {/* Móvil: a sangre (-mx-4) con el margen interior de 16 px dentro (px-4) y 4 px arriba y
              abajo (py-1) para que el anillo de foco de 3 px no lo recorte el overflow. */}
          <nav
            ref={tiraRef}
            aria-label="Principal"
            // N1 (smoke de cierre): con la tira desplazada, Tab puede llegar a un enlace cortado por
            // el borde, y el navegador no siempre lo trae entero. Mismo ajuste que para el activo.
            onFocus={(evento) => {
              if (evento.target instanceof HTMLAnchorElement) mostrarEnTira(evento.currentTarget, evento.target);
            }}
            className="relative col-span-2 row-start-2 -mx-4 flex gap-1 overflow-x-auto overscroll-x-contain px-4 py-1 scrollbar-oculta md:mx-0 md:shrink-0 md:overflow-visible md:p-0"
          >
            {ENLACES.map((enlace) => (
              <NavLink
                key={enlace.to}
                to={enlace.to}
                className={({ isActive }) =>
                  cn(
                    "shrink-0 rounded-lg px-2.5 py-2 text-sm font-medium whitespace-nowrap outline-none transition-colors focus-visible:ring-3 focus-visible:ring-ring/50 md:py-1.5",
                    isActive
                      ? "bg-sidebar-primary text-sidebar-primary-foreground"
                      : "text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-foreground",
                  )
                }
              >
                {enlace.label}
              </NavLink>
            ))}
          </nav>
          <div className="col-start-2 row-start-1 flex min-w-0 items-center justify-end gap-3 md:ml-auto">
            {usuario && (
              <span
                title={usuario.email}
                className="hidden min-w-0 truncate text-sm text-muted-foreground md:block md:max-w-28 lg:max-w-xs"
              >
                {usuario.email}
              </span>
            )}
            <Button variant="outline" size="sm" onClick={logout}>
              Salir
            </Button>
          </div>
        </div>
      </header>
      <SuscripcionBanner suscripcion={suscripcion} />
      <main className="flex-1 p-6">
        <Outlet />
      </main>
    </div>
  );
}
