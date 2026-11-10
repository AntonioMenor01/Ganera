import { createBrowserRouter, Navigate, type RouteObject } from "react-router-dom";
import { AppLayout } from "@/shared/layout/AppLayout";
import { RequireAuth } from "@/shared/auth/RequireAuth";
import { LoginPage } from "@/features/auth/LoginPage";
import { ExplotacionesPage } from "@/features/explotaciones/ExplotacionesPage";
import { TramitesPage } from "@/features/tramites/TramitesPage";
import { FichaOvzPage } from "@/features/tramites/FichaOvzPage";
import { GanaderosPage } from "@/features/ganaderos/GanaderosPage";
import { GanaderoDetallePage } from "@/features/ganaderos/GanaderoDetallePage";
import { DashboardPage } from "@/features/dashboard/DashboardPage";
import { ProximamentePage } from "@/features/proximamente/ProximamentePage";

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
          // Inicio: panel de resumen (mock v2). La cola sigue en /tramites.
          { index: true, element: <DashboardPage /> },
          {
            path: "bandeja",
            element: (
              <ProximamentePage
                titulo="Bandeja"
                descripcion="Aquí llegarán los mensajes de WhatsApp de tus ganaderos, aparte de la cola de trámites. Todavía no hay bandeja en el backend."
              />
            ),
          },
          { path: "tramites", element: <TramitesPage /> },
          { path: "ganaderos", element: <GanaderosPage /> },
          { path: "ganaderos/:id", element: <GanaderoDetallePage /> },
          { path: "explotaciones", element: <ExplotacionesPage /> },
          {
            path: "calendario",
            element: (
              <ProximamentePage
                titulo="Calendario"
                descripcion="Plazos y vencimientos de trámites en un solo sitio. Aún no hay datos de calendario en el backend."
              />
            ),
          },
          {
            path: "informes",
            element: (
              <ProximamentePage
                titulo="Informes"
                descripcion="Resúmenes y exportaciones para la gestoría. Todavía no hay informes en el backend."
              />
            ),
          },
          {
            path: "configuracion",
            element: (
              <ProximamentePage
                titulo="Configuración"
                descripcion="Datos de la gestoría, OVZ.net y preferencias. La pantalla de credenciales OVZ sigue aplazada a escalón 2."
              />
            ),
          },
          // Comodín: rutas desconocidas con sesión van al inicio (mock). Sin sesión, RequireAuth
          // manda antes a /login. /facturacion ya no existe.
          { path: "*", element: <Navigate to="/" replace /> },
        ],
      },
    ],
  },
];

export const router = createBrowserRouter(routes);
