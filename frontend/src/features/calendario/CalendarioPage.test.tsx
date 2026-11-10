import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { AuthProvider } from "@/shared/auth/AuthContext";
import type { Tramite } from "@/features/tramites/types";
import { apiUrl } from "@/test/apiBaseUrl";
import { server } from "@/test/server";
import { CalendarioPage } from "./CalendarioPage";

function tramite(partial: Partial<Tramite> & Pick<Tramite, "id">): Tramite {
  return {
    explotacionId: 1,
    explotacionCodigoRega: "ES100370000124",
    explotacionNombre: "Los Llanos",
    tipoTramite: "SOLICITUD_MOVIMIENTO",
    estado: "HECHO_EN_OVZ",
    motivoError: null,
    crotales: [],
    version: 1,
    origen: "WHATSAPP",
    estadoExtraccion: "COMPLETADA",
    crotalesDescartados: 0,
    hechoEnOvzEn: "2026-10-07T10:30:00Z",
    hechoEnOvzPorNombre: "Ana Pérez",
    motivoRechazo: null,
    descartable: false,
    ...partial,
  };
}

function conSesion() {
  sessionStorage.setItem("ganera.token", "tok");
  server.use(
    http.get(apiUrl("/auth/me"), () =>
      HttpResponse.json({ id: 1, email: "ana@gestoria.es", nombre: "Ana", gestoriaId: 1, activo: true }),
    ),
  );
}

function montar() {
  const router = createMemoryRouter(
    [
      { path: "/", element: <CalendarioPage /> },
      { path: "/tramites", element: <h1>Trámites</h1> },
    ],
    { initialEntries: ["/"] },
  );
  render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  );
}

describe("CalendarioPage", () => {
  it("muestra rejilla mock y lista con hechos en OVZ reales", async () => {
    const user = userEvent.setup();
    conSesion();
    server.use(
      http.get(apiUrl("/tramites"), ({ request }) => {
        const estado = new URL(request.url).searchParams.get("estado");
        if (estado === "HECHO_EN_OVZ") {
          return HttpResponse.json({
            content: [tramite({ id: 9 })],
            totalElements: 1,
            totalPages: 1,
            number: 0,
            size: 100,
          });
        }
        return new HttpResponse(null, { status: 500 });
      }),
    );
    montar();

    expect(await screen.findByRole("heading", { name: "Calendario" })).toBeInTheDocument();
    expect(screen.getByText("Carencias, vencimientos, bajas y guías")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Calendario" })).toHaveAttribute("aria-selected", "true");

    await user.click(screen.getByRole("tab", { name: "Lista" }));
    expect(await screen.findByText(/Solicitud de movimiento/)).toBeInTheDocument();
    expect(screen.getByText(/Ana Pérez/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Solicitud de movimiento/ }));
    expect(await screen.findByRole("link", { name: /Abrir trámite #9/ })).toBeInTheDocument();
  });
});
