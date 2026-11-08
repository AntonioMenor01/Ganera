import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { apiUrl } from "@/test/apiBaseUrl";
import { server } from "@/test/server";
import { AuthProvider } from "@/shared/auth/AuthContext";
import type { Tramite } from "@/features/tramites/types";
import { DashboardPage } from "./DashboardPage";

const USUARIO = { id: 1, email: "lucia@gestoria.es", nombre: "Lucía Martín", gestoriaId: 1, activo: true };

/** Un trámite completo de la cola, con los valores por defecto que no afectan a lo que se prueba. */
function tramite(partial: Partial<Tramite> & Pick<Tramite, "id">): Tramite {
  return {
    explotacionId: 10,
    explotacionCodigoRega: "ES100370000124",
    explotacionNombre: "Dehesa Los Llanos",
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

function paginaTramites(content: Tramite[], totalElements = content.length) {
  return { content, totalElements, totalPages: 1, number: 0, size: 5 };
}

function paginaGanaderos(totalElements: number) {
  return { content: [], totalElements, totalPages: 1, number: 0, size: 1 };
}

/** Con sesión: /auth/me devuelve a Lucía. Cada test añade sus /tramites y /ganaderos. */
function conSesion() {
  sessionStorage.setItem("ganera.token", "tok");
  server.use(http.get(apiUrl("/auth/me"), () => HttpResponse.json(USUARIO)));
}

function montar() {
  const router = createMemoryRouter(
    [
      { path: "/", element: <DashboardPage /> },
      { path: "/tramites", element: <h1>Cola de trámites (destino)</h1> },
      { path: "/ganaderos", element: <h1>Ganaderos (destino)</h1> },
    ],
    { initialEntries: ["/"] },
  );
  render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  );
  return router;
}

describe("DashboardPage: saludo y fecha", () => {
  it("saluda por su nombre de pila y muestra la fecha larga", async () => {
    conSesion();
    server.use(
      http.get(apiUrl("/tramites"), () => HttpResponse.json(paginaTramites([]))),
      http.get(apiUrl("/ganaderos"), () => HttpResponse.json(paginaGanaderos(0))),
    );
    montar();

    const titulo = await screen.findByRole("heading", { level: 1 });
    expect(titulo.textContent).toMatch(/^Buen[oa]s (días|tardes|noches), Lucía$/);
    // La fecha larga lleva «de <año>» (p. ej. «… de 2026»).
    expect(screen.getByText(/\bde \d{4}$/)).toBeInTheDocument();
  });
});

describe("DashboardPage: KPIs", () => {
  it("muestra las cifras reales y enlaza cada una a su sección", async () => {
    conSesion();
    server.use(
      http.get(apiUrl("/tramites"), () => HttpResponse.json(paginaTramites([tramite({ id: 1 })], 5))),
      http.get(apiUrl("/ganaderos"), () => HttpResponse.json(paginaGanaderos(42))),
    );
    montar();

    const pendientes = await screen.findByRole("link", { name: /Trámites pendientes/ });
    expect(within(pendientes).getByText("5")).toBeInTheDocument();
    expect(pendientes).toHaveAttribute("href", "/tramites");

    const ganaderos = screen.getByRole("link", { name: /Ganaderos/ });
    expect(within(ganaderos).getByText("42")).toBeInTheDocument();
    expect(ganaderos).toHaveAttribute("href", "/ganaderos");
  });

  it("no inventa Mensajes ni Alertas: los marca como «Próximamente», sin número ni enlace", async () => {
    conSesion();
    server.use(
      http.get(apiUrl("/tramites"), () => HttpResponse.json(paginaTramites([]))),
      http.get(apiUrl("/ganaderos"), () => HttpResponse.json(paginaGanaderos(0))),
    );
    montar();

    await screen.findByRole("link", { name: /Trámites pendientes/ });
    expect(screen.getByText("Mensajes nuevos")).toBeInTheDocument();
    expect(screen.getByText("Alertas activas")).toBeInTheDocument();
    expect(screen.getAllByText("Próximamente")).toHaveLength(2);
    // No son enlaces (todavía no llevan a ninguna sección real).
    expect(screen.queryByRole("link", { name: /Mensajes nuevos/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Alertas activas/ })).not.toBeInTheDocument();
  });

  it("si falla el conteo de ganaderos, su KPI muestra «—» pero la cola sigue cargando", async () => {
    conSesion();
    server.use(
      http.get(apiUrl("/tramites"), () => HttpResponse.json(paginaTramites([tramite({ id: 1 })], 3))),
      http.get(apiUrl("/ganaderos"), () => new HttpResponse(null, { status: 500 })),
    );
    montar();

    const ganaderos = await screen.findByRole("link", { name: /Ganaderos/ });
    expect(within(ganaderos).getByText("—")).toBeInTheDocument();
    // La cola, independiente, sí trae su cifra.
    const pendientes = screen.getByRole("link", { name: /Trámites pendientes/ });
    expect(within(pendientes).getByText("3")).toBeInTheDocument();
  });
});

describe("DashboardPage: cola de trabajo", () => {
  it("lista los pendientes recientes con su tipo y enlaza a la cola", async () => {
    conSesion();
    server.use(
      http.get(apiUrl("/tramites"), () =>
        HttpResponse.json(
          paginaTramites(
            [
              tramite({ id: 7, tipoTramite: "ALTA_NACIMIENTO", explotacionCodigoRega: "ES101480000087" }),
              tramite({ id: 8, tipoTramite: "BAJA_MUERTE", explotacionCodigoRega: "ES100370000124" }),
            ],
            5,
          ),
        ),
      ),
      http.get(apiUrl("/ganaderos"), () => HttpResponse.json(paginaGanaderos(42))),
    );
    const router = montar();

    const region = await screen.findByRole("region", { name: "Cola de trabajo" });
    expect(within(region).getByText("5 por revisar")).toBeInTheDocument();
    expect(within(region).getByText("Alta por nacimiento")).toBeInTheDocument();
    expect(within(region).getByText("Baja por muerte")).toBeInTheDocument();

    const filas = within(region).getAllByRole("link");
    // Primera fila (y «Ver todos») llevan a la cola real.
    await userEvent.click(filas[0]);
    await waitFor(() => expect(router.state.location.pathname).toBe("/tramites"));
  });

  it("sin pendientes, estado vacío honesto en vez de una lista o un cero suelto", async () => {
    conSesion();
    server.use(
      http.get(apiUrl("/tramites"), () => HttpResponse.json(paginaTramites([], 0))),
      http.get(apiUrl("/ganaderos"), () => HttpResponse.json(paginaGanaderos(42))),
    );
    montar();

    const region = await screen.findByRole("region", { name: "Cola de trabajo" });
    expect(within(region).getByText("sin pendientes")).toBeInTheDocument();
    expect(within(region).getByText(/No hay trámites pendientes de revisión/)).toBeInTheDocument();
  });

  it("si falla la cola, lo dice y Reintentar vuelve a pedirla", async () => {
    conSesion();
    let intentos = 0;
    server.use(
      http.get(apiUrl("/ganaderos"), () => HttpResponse.json(paginaGanaderos(42))),
      http.get(apiUrl("/tramites"), () => {
        intentos += 1;
        return intentos === 1
          ? new HttpResponse(null, { status: 500 })
          : HttpResponse.json(paginaTramites([tramite({ id: 1 })], 1));
      }),
    );
    montar();

    expect(await screen.findByText("No se ha podido cargar la cola")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Reintentar" }));

    const region = await screen.findByRole("region", { name: "Cola de trabajo" });
    expect(within(region).getByText("1 por revisar")).toBeInTheDocument();
    expect(intentos).toBe(2);
  });
});
