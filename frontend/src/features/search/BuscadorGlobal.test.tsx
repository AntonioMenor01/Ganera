import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { apiUrl } from "@/test/apiBaseUrl";
import { server } from "@/test/server";
import { BuscadorGlobal } from "./BuscadorGlobal";
import type { ResultadosBusqueda } from "./types";

const VACIO: ResultadosBusqueda = { ganaderos: [], explotaciones: [], animales: [] };

function montar() {
  const router = createMemoryRouter(
    [
      { path: "/", element: <BuscadorGlobal /> },
      { path: "/ganaderos/:id", element: <h1>Ficha del ganadero</h1> },
    ],
    { initialEntries: ["/"] },
  );
  render(<RouterProvider router={router} />);
  return router;
}

function responde(resultados: Partial<ResultadosBusqueda>) {
  server.use(http.get(apiUrl("/buscar"), () => HttpResponse.json({ ...VACIO, ...resultados })));
}

describe("BuscadorGlobal", () => {
  it("abre con el botón, busca (con debounce) y agrupa los resultados", async () => {
    responde({
      ganaderos: [{ id: 5, nombre: "Juan Pérez", nif: "12345678Z" }],
      explotaciones: [
        { id: 9, codigoRega: "ES940000000001", nombre: "Finca El Roble", ganaderoId: 5, ganaderoNombre: "Juan Pérez" },
      ],
      animales: [
        { crotal: "ES040012345678", explotacionCodigoRega: "ES940000000001", ganaderoId: 5, ganaderoNombre: "Juan Pérez" },
      ],
    });
    montar();

    await userEvent.click(screen.getByRole("button", { name: /Buscar ganadero/ }));
    const dialogo = await screen.findByRole("dialog");
    await userEvent.type(within(dialogo).getByRole("textbox"), "perez");

    expect(await within(dialogo).findByText("Juan Pérez")).toBeInTheDocument();
    expect(within(dialogo).getByRole("region", { name: "Ganaderos" })).toBeInTheDocument();
    expect(within(dialogo).getByText("ES940000000001")).toBeInTheDocument();
    expect(within(dialogo).getByText("ES040012345678")).toBeInTheDocument();
  });

  it("al elegir un resultado navega a la ficha y cierra el buscador", async () => {
    responde({ ganaderos: [{ id: 7, nombre: "María López", nif: null }] });
    const router = montar();

    await userEvent.click(screen.getByRole("button", { name: /Buscar ganadero/ }));
    const dialogo = await screen.findByRole("dialog");
    await userEvent.type(within(dialogo).getByRole("textbox"), "maria");
    await userEvent.click(await within(dialogo).findByRole("link", { name: /María López/ }));

    await waitFor(() => expect(router.state.location.pathname).toBe("/ganaderos/7"));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("se abre con Ctrl+K", async () => {
    responde({});
    montar();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await userEvent.keyboard("{Control>}k{/Control}");
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
  });

  it("con menos de 2 caracteres no busca: invita a escribir", async () => {
    let huboPeticion = false;
    server.use(
      http.get(apiUrl("/buscar"), () => {
        huboPeticion = true;
        return HttpResponse.json(VACIO);
      }),
    );
    montar();
    await userEvent.click(screen.getByRole("button", { name: /Buscar ganadero/ }));
    const dialogo = await screen.findByRole("dialog");
    await userEvent.type(within(dialogo).getByRole("textbox"), "a");

    expect(within(dialogo).getByText(/Escribe al menos 2 caracteres/)).toBeInTheDocument();
    expect(huboPeticion).toBe(false);
  });

  it("sin resultados lo dice con la consulta", async () => {
    responde({});
    montar();
    await userEvent.click(screen.getByRole("button", { name: /Buscar ganadero/ }));
    const dialogo = await screen.findByRole("dialog");
    await userEvent.type(within(dialogo).getByRole("textbox"), "zzz");

    expect(await within(dialogo).findByText(/Sin resultados para «zzz»/)).toBeInTheDocument();
  });

  it("si la búsqueda falla, lo dice y Reintentar vuelve a pedir", async () => {
    let intentos = 0;
    server.use(
      http.get(apiUrl("/buscar"), () => {
        intentos += 1;
        return intentos === 1
          ? new HttpResponse(null, { status: 500 })
          : HttpResponse.json({ ...VACIO, ganaderos: [{ id: 1, nombre: "Encontrado", nif: null }] });
      }),
    );
    montar();
    await userEvent.click(screen.getByRole("button", { name: /Buscar ganadero/ }));
    const dialogo = await screen.findByRole("dialog");
    await userEvent.type(within(dialogo).getByRole("textbox"), "algo");

    await userEvent.click(await within(dialogo).findByRole("button", { name: "Reintentar" }));
    expect(await within(dialogo).findByText("Encontrado")).toBeInTheDocument();
    expect(intentos).toBe(2);
  });
});
