import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { apiUrl } from "@/test/apiBaseUrl";
import { server } from "@/test/server";
import { FichaOvzPage } from "./FichaOvzPage";
import type { TramiteCrotal, TramiteDetalle } from "./types";

function crotal(parcial: Partial<TramiteCrotal> = {}): TramiteCrotal {
  return {
    crotalIndicado: "1234",
    crotal: "ES010000001234",
    animalId: 5,
    enInventario: true,
    resolucion: "EN_INVENTARIO",
    completo: false,
    ...parcial,
  };
}

function detalle(parcial: Partial<TramiteDetalle> = {}): TramiteDetalle {
  return {
    id: 128,
    tipoTramite: "BAJA_MUERTE",
    estado: "APROBADO",
    motivoError: null,
    explotacionId: 3,
    explotacionCodigoRega: "ES061230000012",
    explotacionNombre: "Los Llanos",
    ganaderoNombre: "Juan Pérez Gil",
    ganaderoNif: "12345678Z",
    mensajeOriginal: "Se ha muerto la 1234 esta mañana",
    crotales: [crotal()],
    version: 3,
    origen: "WHATSAPP",
    estadoExtraccion: "COMPLETADA",
    crotalesDescartados: 0,
    ...parcial,
  };
}

function responderCon(respuesta: () => Response) {
  let llamadas = 0;
  server.use(
    http.get(apiUrl("/tramites/:id"), () => {
      llamadas += 1;
      return respuesta();
    }),
  );
  return () => llamadas;
}

function montar(url = "/tramites/128/ovz") {
  const router = createMemoryRouter(
    [
      { path: "/tramites", element: <p>Cola de trámites</p> },
      { path: "/tramites/:id/ovz", element: <FichaOvzPage /> },
    ],
    { initialEntries: [url] },
  );
  render(<RouterProvider router={router} />);
  return router;
}

function portapapelesQueFunciona() {
  const writeText = vi.fn<(texto: string) => Promise<void>>().mockResolvedValue(undefined);
  Object.defineProperty(navigator, "clipboard", { value: { writeText }, configurable: true, writable: true });
  return writeText;
}

/** Fila de un campo por su etiqueta (el `dt`), para mirar dentro su valor y su botón. */
function fila(etiqueta: string): HTMLElement {
  const dt = screen
    .getAllByRole("term")
    .find((t) => t.textContent?.replace(/\*|obligatorio|\(copiado\)/g, "").trim() === etiqueta);
  if (!dt?.parentElement) throw new Error(`Sin fila para ${etiqueta}`);
  return dt.parentElement;
}

afterEach(() => {
  document.title = "";
});

describe("FichaOvzPage: ficha copiable (APROBADO)", () => {
  it("cabecera: formulario, ruta en OVZ y la línea honesta; trámite y estado arriba", async () => {
    responderCon(() => HttpResponse.json(detalle()));
    montar();

    expect(await screen.findByRole("heading", { level: 1, name: "Baja de Bovino" })).toBeInTheDocument();
    expect(screen.getByText("Trámites › Bóvidos › Baja")).toBeInTheDocument();
    expect(screen.getByText("Para copiar en OVZ. Ganera no envía nada.")).toBeInTheDocument();
    // La cabecera de la ventana es la primera de la página (las secciones llevan la suya).
    const cabecera = screen.getAllByRole("banner")[0];
    expect(within(cabecera).getByText("Ficha para OVZ")).toBeInTheDocument();
    expect(within(cabecera).getByText("#128")).toBeInTheDocument();
    expect(within(cabecera).getByText("Aprobado")).toBeInTheDocument();
  });

  it("no lleva la barra de navegación de la app", async () => {
    responderCon(() => HttpResponse.json(detalle()));
    montar();
    await screen.findByRole("heading", { level: 1 });

    expect(screen.queryByRole("navigation", { name: "Principal" })).not.toBeInTheDocument();
  });

  it("cuenta y explotación antes del formulario, copiables", async () => {
    responderCon(() => HttpResponse.json(detalle()));
    montar();
    await screen.findByRole("heading", { level: 1 });

    expect(screen.getByText("Juan Pérez Gil")).toBeInTheDocument();
    expect(within(fila("NIF del titular")).getByText("12345678Z")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Copiar NIF del titular" })).toBeInTheDocument();
    expect(within(fila("Explotación")).getByText("ES061230000012")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Copiar Explotación" })).toBeInTheDocument();
  });

  it("los campos van en el orden de OVZ, con la marca de obligatorio y lo que falta en gris con su pista", async () => {
    responderCon(() => HttpResponse.json(detalle()));
    montar();
    const seccion = await screen.findByRole("region", { name: "Baja" });

    const etiquetas = within(seccion)
      .getAllByRole("term")
      .map((t) => t.textContent?.replace(/\*|obligatorio|\(copiado\)/g, "").trim());
    expect(etiquetas).toEqual([
      "Crotal",
      "Fecha de muerte",
      "Nº Documento MER",
      "Oficina para entregar la copia del ejemplar MER",
    ]);
    expect(within(fila("Crotal")).getByText("ES010000001234")).toBeInTheDocument();
    expect(within(fila("Fecha de muerte")).getByText("Falta · Está en el mensaje")).toBeInTheDocument();
    expect(within(fila("Fecha de muerte")).getByText("obligatorio")).toHaveClass("sr-only");
    // Lo que falta no lleva botón de copiar.
    expect(screen.queryByRole("button", { name: "Copiar Fecha de muerte" })).not.toBeInTheDocument();
  });

  it("un crotal resuelto por sus últimos dígitos enseña también lo escrito", async () => {
    responderCon(() => HttpResponse.json(detalle()));
    montar();
    await screen.findByRole("heading", { level: 1 });

    expect(within(fila("Crotal")).getByText("escrito: 1234")).toBeInTheDocument();
  });

  it("un crotal que falta dice qué se escribió", async () => {
    responderCon(() =>
      HttpResponse.json(
        detalle({ crotales: [crotal({ crotal: "1234", animalId: null, enInventario: false, resolucion: "AMBIGUO" })] }),
      ),
    );
    montar();
    await screen.findByRole("heading", { level: 1 });

    expect(within(fila("Crotal")).getByText("Falta el crotal completo · escrito: 1234")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Copiar Crotal" })).not.toBeInTheDocument();
  });

  it("varias bajas: una sección por crotal", async () => {
    responderCon(() =>
      HttpResponse.json(detalle({ crotales: [crotal(), crotal({ crotalIndicado: "5678", crotal: "ES010000005678" })] })),
    );
    montar();

    expect(await screen.findByRole("region", { name: "Baja 1 de 2" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Baja 2 de 2" })).toBeInTheDocument();
  });

  it("los avisos del formulario van en una lista", async () => {
    responderCon(() => HttpResponse.json(detalle()));
    montar();
    const avisos = await screen.findByRole("list", { name: "Avisos" });

    expect(within(avisos).getByText("Plazo: 7 días desde la muerte")).toBeInTheDocument();
  });

  it("copiar un campo deja la fila marcada como copiada", async () => {
    const user = userEvent.setup();
    responderCon(() => HttpResponse.json(detalle()));
    montar();
    await screen.findByRole("heading", { level: 1 });
    const writeText = portapapelesQueFunciona();

    expect(within(fila("Crotal")).queryByText("(copiado)")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Copiar Crotal" }));

    expect(writeText).toHaveBeenCalledWith("ES010000001234");
    expect(within(fila("Crotal")).getByText("(copiado)")).toBeInTheDocument();
    expect(within(fila("Explotación")).queryByText("(copiado)")).not.toBeInTheDocument();
  });

  it("el mensaje de WhatsApp se ve abierto y se puede plegar", async () => {
    const user = userEvent.setup();
    responderCon(() => HttpResponse.json(detalle()));
    montar();
    const boton = await screen.findByRole("button", { name: "Mensaje de WhatsApp" });

    expect(boton).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("Se ha muerto la 1234 esta mañana")).toBeVisible();
    await user.click(boton);
    expect(boton).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("Se ha muerto la 1234 esta mañana")).not.toBeInTheDocument();
  });

  it("sin mensaje no pinta la sección del mensaje", async () => {
    responderCon(() => HttpResponse.json(detalle({ mensajeOriginal: null })));
    montar();
    await screen.findByRole("heading", { level: 1 });

    expect(screen.queryByRole("button", { name: "Mensaje de WhatsApp" })).not.toBeInTheDocument();
  });

  it("corta el vínculo con la ventana que la abrió y pone un título de ventana reconocible", async () => {
    const opener = {};
    Object.defineProperty(window, "opener", { value: opener, configurable: true, writable: true });
    responderCon(() => HttpResponse.json(detalle()));
    montar();
    await screen.findByRole("heading", { level: 1 });

    expect(window.opener).toBeNull();
    expect(document.title).toBe("Ficha OVZ · #128");
  });

  it("al salir de la ficha devuelve el título de ventana que había", async () => {
    document.title = "Ganera";
    responderCon(() => HttpResponse.json(detalle()));
    const router = montar();
    await screen.findByRole("heading", { level: 1 });
    expect(document.title).toBe("Ficha OVZ · #128");

    await act(() => router.navigate("/tramites"));

    expect(document.title).toBe("Ganera");
  });
});

describe("FichaOvzPage: modos según el estado (D4)", () => {
  it("PENDIENTE_REVISION: vista previa con los valores y sin ningún botón de copiar", async () => {
    responderCon(() => HttpResponse.json(detalle({ estado: "PENDIENTE_REVISION" })));
    montar();

    expect(await screen.findByText("Vista previa: aprueba el trámite antes de pasarlo a OVZ.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "Baja de Bovino" })).toBeInTheDocument();
    expect(within(fila("Crotal")).getByText("ES010000001234")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Copiar/ })).not.toBeInTheDocument();
  });

  it("PENDIENTE_REVISION con varios bloques (solicitud): ningún bloque lleva botones de copiar", async () => {
    responderCon(() =>
      HttpResponse.json(
        detalle({
          estado: "PENDIENTE_REVISION",
          tipoTramite: "SOLICITUD_MOVIMIENTO",
          crotales: [crotal(), crotal({ crotalIndicado: "5678", crotal: "ES010000005678" })],
        }),
      ),
    );
    montar();

    expect(await screen.findByRole("region", { name: "Animales" })).toBeInTheDocument();
    expect(screen.getAllByRole("region").length).toBeGreaterThan(3);
    expect(screen.queryByRole("button", { name: /^Copiar/ })).not.toBeInTheDocument();
  });

  it("un estado que el frontend no conoce no deja copiar", async () => {
    responderCon(() => HttpResponse.json(detalle({ estado: "ESTADO_DEL_FUTURO" as TramiteDetalle["estado"] })));
    montar();

    expect(await screen.findByRole("heading", { level: 1, name: "Ficha no disponible" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Copiar/ })).not.toBeInTheDocument();
  });

  it.each([
    ["RECHAZADO", "Trámite rechazado"],
    ["PENDIENTE_EXTRACCION", "Extracción en curso"],
    ["EN_PROCESO", "Ficha no disponible"],
  ] as const)("%s: sin ficha, con título, una frase y la vuelta a la cola", async (estado, titulo) => {
    responderCon(() => HttpResponse.json(detalle({ estado })));
    montar();

    expect(await screen.findByRole("heading", { level: 1, name: titulo })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Copiar/ })).not.toBeInTheDocument();
    expect(screen.queryByText("Baja de Bovino")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Volver a trámites" })).toHaveAttribute("href", "/tramites");
  });

  it("sin tipo: pide asignarlo en la revisión", async () => {
    responderCon(() => HttpResponse.json(detalle({ tipoTramite: null })));
    montar();

    expect(await screen.findByRole("heading", { level: 1, name: "Falta el tipo de trámite" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Copiar/ })).not.toBeInTheDocument();
  });

  it("censo: Ganera aún no prepara esta ficha", async () => {
    responderCon(() => HttpResponse.json(detalle({ tipoTramite: "DECLARACION_CENSO" })));
    montar();

    expect(await screen.findByRole("heading", { level: 1, name: "Declaración de Censo" })).toBeInTheDocument();
    expect(screen.getByText(/Ganera aún no prepara esta ficha/)).toBeInTheDocument();
  });

  it("un tipo que el frontend no conoce no rompe la página", async () => {
    responderCon(() => HttpResponse.json(detalle({ tipoTramite: "TIPO_DEL_FUTURO" })));
    montar();

    expect(await screen.findByRole("heading", { level: 1, name: "Ficha no disponible" })).toBeInTheDocument();
    expect(screen.getByText(/TIPO_DEL_FUTURO/)).toBeInTheDocument();
  });
});

describe("FichaOvzPage: carga", () => {
  it("404 (otra gestoría o inexistente): «Trámite no encontrado»", async () => {
    responderCon(() => new HttpResponse(null, { status: 404 }));
    montar();

    expect(await screen.findByRole("heading", { level: 1, name: "Trámite no encontrado" })).toBeInTheDocument();
    expect(screen.getByText("Este trámite ya no existe o no es de tu gestoría.")).toBeInTheDocument();
  });

  it("un id que no es un número no llega a preguntar al backend", async () => {
    const llamadas = responderCon(() => HttpResponse.json(detalle()));
    montar("/tramites/abc/ovz");

    expect(await screen.findByRole("heading", { level: 1, name: "Trámite no encontrado" })).toBeInTheDocument();
    expect(llamadas()).toBe(0);
  });

  it("un error del servidor se enseña con Reintentar, que vuelve a pedirlo", async () => {
    const user = userEvent.setup();
    let fallar = true;
    const llamadas = responderCon(() =>
      fallar ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(detalle()),
    );
    montar();

    expect(await screen.findByText("No se ha podido cargar la ficha")).toBeInTheDocument();
    fallar = false;
    await user.click(screen.getByRole("button", { name: "Reintentar" }));

    expect(await screen.findByRole("heading", { level: 1, name: "Baja de Bovino" })).toBeInTheDocument();
    expect(llamadas()).toBe(2);
  });
});
