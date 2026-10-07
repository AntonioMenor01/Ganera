import { createBrowserRouter, Navigate, type RouteObject } from "react-router-dom";
import { AppLayout } from "@/shared/layout/AppLayout";
import { RequireAuth } from "@/shared/auth/RequireAuth";
import { LoginPage } from "@/features/auth/LoginPage";
import { ExplotacionesPage } from "@/features/explotaciones/ExplotacionesPage";
import { TramitesPage } from "@/features/tramites/TramitesPage";
import { FichaOvzPage } from "@/features/tramites/FichaOvzPage";
import { GanaderosPage } from "@/features/ganaderos/GanaderosPage";
import { GanaderoDetallePage } from "@/features/ganaderos/GanaderoDetallePage";

/** Exportadas para que los tests monten las rutas reales en un createMemoryRouter. */
export const routes: RouteObject[] = [
  { path: "/login", element: <LoginPage /> },
  // El alta pública está cerrada (POST /gestorias/registro responde 404 salvo con
  // ganera.registro.abierto) y pasará a la landing en el Prompt C: /registro ya no es una pantalla.
  // Se queda la ruta para que un enlace o marcador antiguo lleve al login y no a un error.
  { path: "/registro", element: <Navigate to="/login" replace /> },
  {
    element: <RequireAuth />,
    children: [
      // Ficha para OVZ: ventana auxiliar junto a OVZ, sin la barra de la app (fuera de AppLayout).
      // Sin sesión (abierta sin heredarla), RequireAuth manda al login y este vuelve aquí (D3).
      { path: "/tramites/:id/ovz", element: <FichaOvzPage /> },
      {
        path: "/",
        element: <AppLayout />,
        children: [
          { index: true, element: <Navigate to="/tramites" replace /> },
          { path: "tramites", element: <TramitesPage /> },
          { path: "ganaderos", element: <GanaderosPage /> },
          { path: "ganaderos/:id", element: <GanaderoDetallePage /> },
          { path: "explotaciones", element: <ExplotacionesPage /> },
          // Comodín (D5): /facturacion (ya no existe) y cualquier ruta desconocida con sesión van a
          // la cola. Sin sesión, RequireAuth manda antes a /login.
          { path: "*", element: <Navigate to="/tramites" replace /> },
        ],
      },
    ],
  },
];

export const router = createBrowserRouter(routes);
