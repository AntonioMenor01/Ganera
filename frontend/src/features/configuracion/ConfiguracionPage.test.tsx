import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { AuthProvider } from "@/shared/auth/AuthContext";
import { apiUrl } from "@/test/apiBaseUrl";
import { server } from "@/test/server";
import { ConfiguracionPage } from "./ConfiguracionPage";

function conSesion() {
  sessionStorage.setItem("ganera.token", "tok");
  server.use(
    http.get(apiUrl("/auth/me"), () =>
      HttpResponse.json({ id: 1, email: "ana@gestoria.es", nombre: "Ana López", gestoriaId: 1, activo: true }),
    ),
    http.get(apiUrl("/facturacion/suscripcion"), () =>
      HttpResponse.json({ estado: "ACTIVA", puedeAprobarTramites: true }),
    ),
    http.get(apiUrl("/contactos"), () =>
      HttpResponse.json({
        content: [
          {
            id: 3,
            telefono: "+34600111222",
            nombre: "Pedro Ganadero",
            activo: true,
            explotaciones: [
              { explotacionId: 1, codigoRega: "ES100370000124", nombre: "Los Llanos", rol: "TITULAR" },
            ],
          },
        ],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 50,
      }),
    ),
  );
}

function montar() {
  const router = createMemoryRouter(
    [
      { path: "/", element: <ConfiguracionPage /> },
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

describe("ConfiguracionPage", () => {
  it("navega las pestañas del mock y permite crear un contacto en WhatsApp", async () => {
    conSesion();
    let creados = 0;
    server.use(
      http.post(apiUrl("/contactos"), async ({ request }) => {
        creados += 1;
        const body = (await request.json()) as { telefono: string; nombre: string };
        return HttpResponse.json(
          {
            id: 99,
            telefono: body.telefono,
            nombre: body.nombre,
            activo: true,
            explotaciones: [],
          },
          { status: 201 },
        );
      }),
    );
    montar();

    const user = userEvent.setup();
    expect(await screen.findByRole("heading", { name: "Configuración" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Gestoría" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Datos de la gestoría" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Suscripción" }));
    expect(await screen.findByText("Activa")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Cuenta" }));
    expect(await screen.findByText("Ana López")).toBeInTheDocument();
    expect(screen.getByText("ana@gestoria.es")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "WhatsApp" }));
    expect(await screen.findByText("Pedro Ganadero")).toBeInTheDocument();
    expect(screen.getByText(/\+34600111222/)).toBeInTheDocument();

    await user.type(screen.getByLabelText("Nombre"), "María");
    await user.type(screen.getByLabelText("Teléfono"), "+34600999888");
    await user.click(screen.getByRole("button", { name: "Añadir" }));

    expect(creados).toBe(1);
  });
});
