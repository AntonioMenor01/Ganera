import { useLayoutEffect, useRef } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { ClipboardListIcon, UsersIcon, WarehouseIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/shared/auth/AuthContext";
import { LogoGanera } from "@/shared/brand/LogoGanera";
import { BuscadorGlobal } from "@/features/search/BuscadorGlobal";
import { SuscripcionBanner } from "@/features/facturacion/SuscripcionBanner";
import { useSuscripcionEstado } from "@/features/facturacion/useSuscripcionEstado";
import { iniciales } from "./iniciales";
import { scrollLeftParaMostrar } from "./tiraNavegacion";

// Orden de trabajo: la cola (lo diario) y a quién pertenece (Ganaderos → sus explotaciones). El
// pago ya no se gestiona en la app (se cobra desde la landing): no hay enlace de Facturación.
// /ganaderos/:id también marca "Ganaderos" (NavLink sin `end`).
// El icono es decorativo (aria-hidden): el nombre accesible del enlace es solo su texto.
const ENLACES = [
  { to: "/tramites", label: "Trámites", Icono: ClipboardListIcon },
  { to: "/ganaderos", label: "Ganaderos", Icono: UsersIcon },
  { to: "/explotaciones", label: "Explotaciones", Icono: WarehouseIcon },
];

/** Margen interior de la tira de enlaces en móvil (`px-4`): el activo se trae a la vista con él. */
const MARGEN_TIRA_PX = 16;

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

/**
 * Barra de la app. Un solo <header> y un solo <nav> en el DOM, en orden marca → enlaces →
 * usuario/«Cerrar sesión» (el orden de tabulación es enlaces → «Cerrar sesión» en todos los
 * tamaños). Solo cambian las clases por punto de corte:
 * - por debajo de `md`, rejilla de dos filas: marca y usuario arriba; la nav baja a la segunda
 *   fila como tira a sangre con scroll horizontal interno;
 * - de `md` a `lg`, una sola fila flex;
 * - desde `lg`, el mismo <header> es la barra lateral fija de 240 px: marca arriba, enlaces en
 *   columna y la tarjeta de usuario abajo. El contenido deja sitio con `lg:pl-60`.
 */
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
    <div className="flex min-h-screen flex-col bg-background text-foreground lg:pl-60">
      <header className="border-b-2 border-divisor bg-sidebar px-4 pt-3 pb-1 text-sidebar-foreground md:px-6 md:py-4 lg:fixed lg:inset-y-0 lg:left-0 lg:flex lg:w-60 lg:flex-col lg:border-r-2 lg:border-b-0 lg:p-0">
        <div className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 gap-y-2 md:flex md:gap-6 lg:min-h-0 lg:flex-1 lg:flex-col lg:items-stretch lg:gap-0">
          <div className="col-start-1 row-start-1 md:shrink-0 lg:h-16 lg:border-b-2 lg:border-divisor lg:px-5">
            {/* La marca lleva al inicio (el panel de resumen). Enlace con nombre accesible propio
                («Ir al inicio»): «GANERA» como texto de enlace se leería raro. Vive fuera del <nav>
                principal, así que no cuenta como un enlace más de la tira de navegación. */}
            <Link
              to="/"
              aria-label="Ir al inicio"
              className="flex h-full items-center gap-2 rounded-sm outline-none focus-visible:ring-3 focus-visible:ring-ring lg:gap-2.5"
            >
              <LogoGanera decorativo className="size-5 text-marca lg:size-[22px]" />
              <span className="font-bold tracking-wide text-foreground lg:text-base">GANERA</span>
            </Link>
          </div>
          {/* Buscador global: en móvil, fila propia a todo el ancho bajo la nav; de md a lg, un
              campo en la barra; desde lg, en la columna de la barra lateral, bajo la marca. El
              atajo Ctrl/⌘+K lo abre desde cualquier parte. */}
          <BuscadorGlobal className="col-span-2 row-start-3 w-full md:w-56 lg:mx-3 lg:mt-3 lg:w-auto" />
          {/* Móvil: a sangre (-mx-4) con el margen interior de 16 px dentro (px-4) y 4 px arriba y
              abajo (py-1) para que el anillo de foco de 3 px no lo recorte el overflow. Desde `lg`,
              columna con scroll vertical propio y 12 px de margen (p-3), que también deja sitio al
              anillo. */}
          <nav
            ref={tiraRef}
            aria-label="Principal"
            // N1 (smoke de cierre): con la tira desplazada, Tab puede llegar a un enlace cortado por
            // el borde, y el navegador no siempre lo trae entero. Mismo ajuste que para el activo.
            onFocus={(evento) => {
              if (evento.target instanceof HTMLAnchorElement) mostrarEnTira(evento.currentTarget, evento.target);
            }}
            className="relative col-span-2 row-start-2 -mx-4 flex gap-1 overflow-x-auto overscroll-x-contain px-4 py-1 scrollbar-oculta md:mx-0 md:shrink-0 md:overflow-visible md:p-0 lg:min-h-0 lg:flex-1 lg:shrink lg:flex-col lg:gap-0.5 lg:overflow-x-visible lg:overflow-y-auto lg:p-3"
          >
            {ENLACES.map(({ to, label, Icono }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  cn(
                    "flex shrink-0 items-center gap-2 px-2.5 py-2 text-sm font-medium whitespace-nowrap outline-none transition-colors focus-visible:ring-3 focus-visible:ring-ring md:py-1.5 lg:h-10 lg:w-full lg:gap-3 lg:py-0",
                    isActive
                      ? "bg-sidebar-primary text-sidebar-primary-foreground"
                      : "text-sidebar-foreground hover:bg-sidebar-accent",
                  )
                }
              >
                <Icono aria-hidden="true" className="size-4 shrink-0 lg:size-[18px]" />
                {label}
              </NavLink>
            ))}
          </nav>
          <div className="col-start-2 row-start-1 flex min-w-0 items-center justify-end md:ml-auto lg:mt-auto lg:ml-0 lg:block lg:border-t-2 lg:border-divisor lg:p-3">
            <div className="flex min-w-0 items-center gap-3 lg:flex-col lg:items-stretch lg:gap-2.5 lg:border lg:border-border lg:bg-card lg:p-2.5">
              {usuario && (
                <div className="flex min-w-0 items-center gap-2 lg:gap-2.5">
                  <span
                    aria-hidden="true"
                    className="grid size-8 shrink-0 place-items-center bg-rojo-100 text-xs font-bold text-rojo-800"
                  >
                    {iniciales(usuario.nombre, usuario.email)}
                  </span>
                  <span className="flex min-w-0 flex-col">
                    <span
                      title={usuario.email}
                      className="max-w-24 min-w-0 truncate text-sm font-medium md:max-w-40 lg:max-w-none lg:text-[13px]"
                    >
                      {usuario.nombre}
                    </span>
                    <span
                      title={usuario.email}
                      className="hidden max-w-40 min-w-0 truncate text-xs text-muted-foreground md:block lg:max-w-none"
                    >
                      {usuario.email}
                    </span>
                  </span>
                </div>
              )}
              <Button variant="outline" size="sm" onClick={logout} className="lg:w-full">
                Cerrar sesión
              </Button>
            </div>
          </div>
        </div>
      </header>
      <SuscripcionBanner suscripcion={suscripcion} />
      <main className="flex-1 p-6 lg:p-8">
        <Outlet />
      </main>
    </div>
  );
}
