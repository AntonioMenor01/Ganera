import { createBrowserRouter, Navigate } from "react-router-dom";
import { AppLayout } from "@/shared/layout/AppLayout";
import { RequireAuth } from "@/shared/auth/RequireAuth";
import { LoginPage } from "@/features/auth/LoginPage";
import { RegistroPage } from "@/features/auth/RegistroPage";
import { ExplotacionesPage } from "@/features/explotaciones/ExplotacionesPage";
import { TramitesPage } from "@/features/tramites/TramitesPage";
import { FacturacionPage } from "@/features/facturacion/FacturacionPage";

export const router = createBrowserRouter([
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
          { path: "ganaderos", element: <div>Ganaderos (pendiente)</div> },
          { path: "explotaciones", element: <ExplotacionesPage /> },
          { path: "facturacion", element: <FacturacionPage /> },
        ],
      },
    ],
  },
]);
