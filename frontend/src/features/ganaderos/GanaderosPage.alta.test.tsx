import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider, useParams } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";
import { apiUrl } from "@/test/apiBaseUrl";
import { server } from "@/test/server";
import { GanaderosPage } from "./GanaderosPage";
import type { GanaderoResumen } from "./types";

function fila(partial: Partial<GanaderoResumen> & Pick<GanaderoResumen, "id" | "nombre">): GanaderoResumen {
  return { nif: "11111111H", numeroExplotaciones: 0, ...partial };
}

function pagina(content: GanaderoResumen[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20 };
}

function DestinoGanadero() {
  const { id } = useParams();
  return <h1>Ficha del ganadero {id}</h1>;
}

function montar() {
  const router = createMemoryRouter(
    [
      { path: "/ganaderos", element: <GanaderosPage /> },
      { path: "/ganaderos/:id", element: <DestinoGanadero /> },
      { path: "/explotaciones", element: <h1>Explotaciones</h1> },
    ],
    { initialEntries: ["/ganaderos"] },
  );
  render(<RouterProvider router={router} />);
  return router;
}

const EXISTENTE = fila({ id: 1, nombre: "Ganadero Existente", nif: "00000001R" });

async function abrirDialogo() {
  montar();
  await screen.findByText("Ganadero Existente");
  await userEvent.click(screen.getByRole("button", { name: "Añadir ganadero" }));
  return screen.getByRole("dialog");
}

describe("GanaderosPage: alta manual", () => {
  beforeEach(() => {
    server.use(http.get(apiUrl("/ganaderos"), () => HttpResponse.json(pagina([EXISTENTE]))));
  });

  it("crea un ganadero, confirma con enlace a su ficha y recarga el listado", async () => {
    const creado = fila({ id: 42, nombre: "Finca Nueva SL", nif: "12345678Z" });
    let creadoYa = false;
    server.use(
      http.get(apiUrl("/ganaderos"), () =>
        HttpResponse.json(pagina(creadoYa ? [EXISTENTE, creado] : [EXISTENTE])),
      ),
      http.post(apiUrl("/ganaderos"), async ({ request }) => {
        const cuerpo = (await request.json()) as { nombre: string; nif: string };
        expect(cuerpo).toEqual({ nombre: "Finca Nueva SL", nif: "12345678Z" });
        creadoYa = true;
        return HttpResponse.json(creado, { status: 201 });
      }),
    );

    const dialogo = await abrirDialogo();
    await userEvent.type(within(dialogo).getByLabelText("Nombre"), "Finca Nueva SL");
    await userEvent.type(within(dialogo).getByLabelText("NIF / CIF"), "12345678Z");
    await userEvent.click(within(dialogo).getByRole("button", { name: "Añadir ganadero" }));

    // El diálogo se cierra y aparece el aviso con enlace a la ficha.
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(await screen.findByText(/«Finca Nueva SL» añadido/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ver ficha" })).toHaveAttribute("href", "/ganaderos/42");
    // La lista se recargó con el nuevo ganadero (texto exacto en la celda de la tabla).
    expect(await screen.findByText("Finca Nueva SL")).toBeInTheDocument();
  });

  it("NIF inválido: muestra el motivo del backend y no cierra el diálogo", async () => {
    server.use(
      http.post(apiUrl("/ganaderos"), () =>
        HttpResponse.json({ motivo: "El NIF/CIF no es válido. Revísalo e inténtalo de nuevo." }, { status: 400 }),
      ),
    );

    const dialogo = await abrirDialogo();
    await userEvent.type(within(dialogo).getByLabelText("Nombre"), "Finca X");
    await userEvent.type(within(dialogo).getByLabelText("NIF / CIF"), "12345678A");
    await userEvent.click(within(dialogo).getByRole("button", { name: "Añadir ganadero" }));

    expect(await within(dialogo).findByText(/El NIF\/CIF no es válido/)).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("NIF duplicado: muestra el 409 del backend", async () => {
    server.use(
      http.post(apiUrl("/ganaderos"), () =>
        HttpResponse.json({ motivo: "Ya tienes un ganadero con ese NIF." }, { status: 409 }),
      ),
    );

    const dialogo = await abrirDialogo();
    await userEvent.type(within(dialogo).getByLabelText("Nombre"), "Repetido");
    await userEvent.type(within(dialogo).getByLabelText("NIF / CIF"), "00000001R");
    await userEvent.click(within(dialogo).getByRole("button", { name: "Añadir ganadero" }));

    expect(await within(dialogo).findByText("Ya tienes un ganadero con ese NIF.")).toBeInTheDocument();
  });

  it("el campo Teléfono es opcional (sin marca *) y muestra la ayuda D7 literal", async () => {
    const dialogo = await abrirDialogo();
    const campo = within(dialogo).getByLabelText(/Teléfono/);
    // Opcional: su etiqueta no lleva el asterisco que sí llevan Nombre y NIF.
    expect(within(dialogo).getByText("Teléfono (opcional)")).toBeInTheDocument();
    const ayuda = within(dialogo).getByText(
      "Para que Ganera reconozca sus WhatsApp, añade este número como contacto de una explotación",
    );
    expect(campo).toHaveAttribute("aria-describedby", ayuda.id);
  });

  it("alta con teléfono: el POST incluye el teléfono tecleado", async () => {
    const creado = fila({ id: 43, nombre: "Finca Tel SL", nif: "12345678Z" });
    let cuerpoRecibido: unknown = null;
    server.use(
      http.post(apiUrl("/ganaderos"), async ({ request }) => {
        cuerpoRecibido = await request.json();
        return HttpResponse.json(creado, { status: 201 });
      }),
    );

    const dialogo = await abrirDialogo();
    await userEvent.type(within(dialogo).getByLabelText("Nombre"), "Finca Tel SL");
    await userEvent.type(within(dialogo).getByLabelText("NIF / CIF"), "12345678Z");
    await userEvent.type(within(dialogo).getByLabelText(/Teléfono/), "612 345 678");
    await userEvent.click(within(dialogo).getByRole("button", { name: "Añadir ganadero" }));

    await waitFor(() =>
      expect(cuerpoRecibido).toEqual({ nombre: "Finca Tel SL", nif: "12345678Z", telefono: "612 345 678" }),
    );
  });

  it("alta sin teléfono: el POST no manda la clave telefono", async () => {
    const creado = fila({ id: 44, nombre: "Sin Tel SL", nif: "12345678Z" });
    let cuerpoRecibido: unknown = null;
    server.use(
      http.post(apiUrl("/ganaderos"), async ({ request }) => {
        cuerpoRecibido = await request.json();
        return HttpResponse.json(creado, { status: 201 });
      }),
    );

    const dialogo = await abrirDialogo();
    await userEvent.type(within(dialogo).getByLabelText("Nombre"), "Sin Tel SL");
    await userEvent.type(within(dialogo).getByLabelText("NIF / CIF"), "12345678Z");
    await userEvent.click(within(dialogo).getByRole("button", { name: "Añadir ganadero" }));

    await waitFor(() => expect(cuerpoRecibido).toEqual({ nombre: "Sin Tel SL", nif: "12345678Z" }));
  });

  it("400 de teléfono: marca el campo, muestra el motivo y conserva lo escrito", async () => {
    server.use(
      http.post(apiUrl("/ganaderos"), () =>
        HttpResponse.json(
          { motivo: "El teléfono no es válido. Revísalo e inténtalo de nuevo." },
          { status: 400 },
        ),
      ),
    );

    const dialogo = await abrirDialogo();
    await userEvent.type(within(dialogo).getByLabelText("Nombre"), "Finca X");
    await userEvent.type(within(dialogo).getByLabelText("NIF / CIF"), "12345678Z");
    await userEvent.type(within(dialogo).getByLabelText(/Teléfono/), "abc");
    await userEvent.click(within(dialogo).getByRole("button", { name: "Añadir ganadero" }));

    expect(await within(dialogo).findByText(/El teléfono no es válido/)).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    const campo = within(dialogo).getByLabelText(/Teléfono/);
    expect(campo).toHaveValue("abc");
    expect(campo).toHaveAttribute("aria-invalid", "true");
  });

  it("valida en el cliente que el nombre no esté vacío (no llega al backend)", async () => {
    let huboPost = false;
    server.use(
      http.post(apiUrl("/ganaderos"), () => {
        huboPost = true;
        return HttpResponse.json(fila({ id: 2, nombre: "x" }), { status: 201 });
      }),
    );

    const dialogo = await abrirDialogo();
    // Sin nombre, con NIF.
    await userEvent.type(within(dialogo).getByLabelText("NIF / CIF"), "12345678Z");
    await userEvent.click(within(dialogo).getByRole("button", { name: "Añadir ganadero" }));

    expect(await within(dialogo).findByText("El nombre del ganadero es obligatorio.")).toBeInTheDocument();
    expect(huboPost).toBe(false);
  });
});
