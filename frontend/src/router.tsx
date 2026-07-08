import { createBrowserRouter, Navigate } from "react-router-dom";
import { AppLayout } from "@/shared/layout/AppLayout";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/tramites" replace /> },
      { path: "tramites", element: <div>Cola de tramites (pendiente)</div> },
      { path: "ganaderos", element: <div>Ganaderos (pendiente)</div> },
      { path: "explotaciones", element: <div>Explotaciones (pendiente)</div> },
      { path: "facturacion", element: <div>Facturacion (pendiente)</div> },
    ],
  },
]);
