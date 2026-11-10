import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { AuthProvider } from "@/shared/auth/AuthContext";
import { apiUrl } from "@/test/apiBaseUrl";
import { server } from "@/test/server";
import { InformesPage } from "./InformesPage";

function conSesion() {
  sessionStorage.setItem("ganera.token", "tok");
  server.use(
    http.get(apiUrl("/auth/me"), () =>
      HttpResponse.json({ id: 1, email: "ana@gestoria.es", nombre: "Ana", gestoriaId: 1, activo: true }),
    ),
  );
}

function pagina(totalElements: number) {
  return { content: [], totalElements, totalPages: 1, number: 0, size: 1 };
}

function montar() {
  const router = createMemoryRouter(
    [
      { path: "/", element: <InformesPage /> },
      { path: "/tramites", element: <h1>Trámites</h1> },
      { path: "/ganaderos", element: <h1>Ganaderos</h1> },
      { path: "/explotaciones", element: <h1>Explotaciones</h1> },
    ],
    { initialEntries: ["/"] },
  );
  render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  );
}

describe("InformesPage", () => {
  it("muestra KPIs reales y chrome del mock (export deshabilitado)", async () => {
    conSesion();
    server.use(
      http.get(apiUrl("/tramites"), ({ request }) => {
        const estado = new URL(request.url).searchParams.get("estado");
        const totales: Record<string, number> = {
          PENDIENTE_EXTRACCION: 1,
          PENDIENTE_REVISION: 4,
          APROBADO: 2,
          HECHO_EN_OVZ: 3,
          RECHAZADO: 0,
        };
        return HttpResponse.json(pagina(estado ? (totales[estado] ?? 0) : 10));
      }),
      http.get(apiUrl("/ganaderos"), () => HttpResponse.json(pagina(7))),
      http.get(apiUrl("/explotaciones"), () => HttpResponse.json(pagina(9))),
    );
    montar();

    expect(await screen.findByRole("heading", { name: "Informes" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "PDF" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Excel" })).toBeDisabled();
    expect(await screen.findByText("Marcados por el equipo")).toBeInTheDocument();
    expect(screen.getByText("En tu gestoría")).toBeInTheDocument();
    expect(screen.getByText("Activas en inventario")).toBeInTheDocument();
    // KPIs: 3 hechos, 4 pendientes, 7 ganaderos, 9 explotaciones (también se repiten en barras).
    expect(screen.getAllByText("3").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("4").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("7").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("9").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Pendiente de revisión").length).toBeGreaterThanOrEqual(1);
  });
});
