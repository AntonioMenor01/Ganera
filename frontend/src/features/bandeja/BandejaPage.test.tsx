import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { AuthProvider } from "@/shared/auth/AuthContext";
import type { Tramite } from "@/features/tramites/types";
import { apiUrl } from "@/test/apiBaseUrl";
import { server } from "@/test/server";
import { BandejaPage } from "./BandejaPage";

function tramite(partial: Partial<Tramite> & Pick<Tramite, "id">): Tramite {
  return {
    explotacionId: 1,
    explotacionCodigoRega: "ES100370000124",
    explotacionNombre: "Los Llanos",
    tipoTramite: "ALTA_NACIMIENTO",
    estado: "PENDIENTE_REVISION",
    motivoError: null,
    crotales: [],
    version: 0,
    origen: "WHATSAPP",
    estadoExtraccion: "COMPLETADA",
    crotalesDescartados: 0,
    hechoEnOvzEn: null,
    hechoEnOvzPorNombre: null,
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
      { path: "/", element: <BandejaPage /> },
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

describe("BandejaPage", () => {
  it("lista solo trámites con origen WHATSAPP", async () => {
    conSesion();
    server.use(
      http.get(apiUrl("/tramites"), () =>
        HttpResponse.json({
          content: [
            tramite({ id: 1, origen: "WHATSAPP", tipoTramite: "BAJA_MUERTE" }),
            tramite({ id: 2, origen: null, tipoTramite: "DECLARACION_CENSO" }),
          ],
          totalElements: 2,
          totalPages: 1,
          number: 0,
          size: 50,
        }),
      ),
    );
    montar();

    expect(await screen.findByRole("heading", { name: "Bandeja" })).toBeInTheDocument();
    expect(screen.getByText(/Baja por muerte/)).toBeInTheDocument();
    expect(screen.queryByText(/Declaración de censo|Censo/)).not.toBeInTheDocument();
  });
});
