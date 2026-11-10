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
  it("muestra conteos reales de trámites, ganaderos y explotaciones", async () => {
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
    expect(await screen.findByText("10")).toBeInTheDocument();
    expect(screen.getByText("7")).toBeInTheDocument();
    expect(screen.getByText("9")).toBeInTheDocument();
    expect(screen.getByText("Pendiente de revisión")).toBeInTheDocument();
    expect(screen.getByText("4")).toBeInTheDocument();
  });
});
