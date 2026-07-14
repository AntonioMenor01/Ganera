import { NavLink, Outlet } from "react-router-dom";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/shared/auth/AuthContext";
import { SuscripcionBanner } from "@/features/facturacion/SuscripcionBanner";
import { useSuscripcionEstado } from "@/features/facturacion/useSuscripcionEstado";

const ENLACES = [
  { to: "/explotaciones", label: "Explotaciones" },
  { to: "/tramites", label: "Trámites" },
  { to: "/facturacion", label: "Facturación" },
];

/** Contexto compartido via <Outlet> para que las paginas hijas (p.ej. Facturacion) reutilicen
 * el mismo fetch de estado de suscripcion en vez de repetirlo. */
export interface AppLayoutContext {
  suscripcion: ReturnType<typeof useSuscripcionEstado>;
}

export function AppLayout() {
  const { usuario, logout } = useAuth();
  const suscripcion = useSuscripcionEstado();

  return (
    <div className="flex min-h-screen flex-col bg-background text-foreground">
      <header className="border-b px-6 py-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-6">
            <span className="font-semibold">Ganera</span>
            <nav className="flex items-center gap-1">
              {ENLACES.map((enlace) => (
                <NavLink
                  key={enlace.to}
                  to={enlace.to}
                  className={({ isActive }) =>
                    cn(
                      "rounded-lg px-2.5 py-1.5 text-sm font-medium transition-colors",
                      isActive
                        ? "bg-muted text-foreground"
                        : "text-muted-foreground hover:bg-muted hover:text-foreground",
                    )
                  }
                >
                  {enlace.label}
                </NavLink>
              ))}
            </nav>
          </div>
          <div className="flex items-center gap-3">
            {usuario && (
              <span className="text-sm text-muted-foreground">{usuario.email}</span>
            )}
            <Button variant="outline" size="sm" onClick={logout}>
              Salir
            </Button>
          </div>
        </div>
      </header>
      <SuscripcionBanner suscripcion={suscripcion} />
      <main className="flex-1 p-6">
        <Outlet context={{ suscripcion } satisfies AppLayoutContext} />
      </main>
    </div>
  );
}
