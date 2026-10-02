import { createBrowserRouter, Navigate, type RouteObject } from "react-router-dom";
import { AppLayout } from "@/shared/layout/AppLayout";
import { RequireAuth } from "@/shared/auth/RequireAuth";
import { LoginPage } from "@/features/auth/LoginPage";
import { RegistroPage } from "@/features/auth/RegistroPage";
import { ExplotacionesPage } from "@/features/explotaciones/ExplotacionesPage";
import { TramitesPage } from "@/features/tramites/TramitesPage";
import { FacturacionPage } from "@/features/facturacion/FacturacionPage";
import { GanaderosPage } from "@/features/ganaderos/GanaderosPage";
import { GanaderoDetallePage } from "@/features/ganaderos/GanaderoDetallePage";

/** Exportadas para que los tests monten las rutas reales en un createMemoryRouter. */
export const routes: RouteObject[] = [
  { path: "/login", element: <LoginPage /> },
  { path: "/registro", element: <RegistroPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        path: "/",
        element: <AppLayout />,
        children: [
          { index: true, element: <Navigate to="/tramites" replace /> },
          { path: "tramites", element: <TramitesPage /> },
          { path: "ganaderos", element: <GanaderosPage /> },
          { path: "ganaderos/:id", element: <GanaderoDetallePage /> },
          { path: "explotaciones", element: <ExplotacionesPage /> },
          { path: "facturacion", element: <FacturacionPage /> },
        ],
      },
    ],
  },
];

export const router = createBrowserRouter(routes);
