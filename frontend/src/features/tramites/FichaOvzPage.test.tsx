import { act, render, screen, waitFor, within } from "@testing-library/react";
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
    hechoEnOvzEn: null,
    hechoEnOvzPorNombre: null,
    whatsapp: null,
    motivoRechazo: null,
    descartable: false,
    historialDescarte: [],
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
    expect(within(cabecera).getByText("Aprobado · falta OVZ")).toBeInTheDocument();
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

  it("un descartado (SIN_TRAMITE) lleva el mismo badge que la cola y el modal: «Descartado», neutro", async () => {
    responderCon(() => HttpResponse.json(detalle({ estado: "RECHAZADO", motivoRechazo: "SIN_TRAMITE" })));
    montar();

    const badge = await screen.findByText("Descartado");
    expect(badge).toHaveClass("bg-neutral", "text-neutral-foreground");
    expect(badge).not.toHaveClass("bg-danger");
    expect(screen.queryByText("Rechazado")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Copiar/ })).not.toBeInTheDocument();
  });

  it("un descartado (SIN_TRAMITE) dice «Descartado como mensaje sin trámite», no «Trámite rechazado»", async () => {
    responderCon(() => HttpResponse.json(detalle({ estado: "RECHAZADO", motivoRechazo: "SIN_TRAMITE" })));
    montar();

    expect(
      await screen.findByRole("heading", { level: 1, name: "Descartado como mensaje sin trámite" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Un mensaje descartado no se pasa a OVZ.")).toBeInTheDocument();
    expect(screen.queryByText("Trámite rechazado")).not.toBeInTheDocument();
    expect(screen.queryByText("Un trámite rechazado no se pasa a OVZ.")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Volver a trámites" })).toHaveAttribute("href", "/tramites");
  });

  it("un rechazo normal sigue diciendo «Trámite rechazado»", async () => {
    responderCon(() => HttpResponse.json(detalle({ estado: "RECHAZADO", motivoRechazo: null })));
    montar();

    expect(await screen.findByRole("heading", { level: 1, name: "Trámite rechazado" })).toBeInTheDocument();
    expect(screen.queryByText("Descartado como mensaje sin trámite")).not.toBeInTheDocument();
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

// ---------------------------------------------------------------------------------------------
// Hecho en OVZ (2026-10-07): el gestor deja constancia de que él ya lo metió en OVZ.

const HECHO = {
  estado: "HECHO_EN_OVZ" as const,
  hechoEnOvzEn: "2026-10-07T10:30:00Z",
  hechoEnOvzPorNombre: "Ana Pérez",
};

/** Lo que devuelven marcar/desmarcar: el DTO del listado (`TramiteResponse`). */
function respuestaLista(d: TramiteDetalle) {
  return {
    id: d.id,
    explotacionId: d.explotacionId,
    explotacionCodigoRega: d.explotacionCodigoRega,
    explotacionNombre: d.explotacionNombre,
    tipoTramite: d.tipoTramite,
    estado: d.estado,
    motivoError: d.motivoError,
    crotales: d.crotales,
    version: d.version,
    origen: d.origen,
    estadoExtraccion: d.estadoExtraccion,
    crotalesDescartados: d.crotalesDescartados,
    hechoEnOvzEn: d.hechoEnOvzEn,
    hechoEnOvzPorNombre: d.hechoEnOvzPorNombre,
    motivoRechazo: d.motivoRechazo ?? null,
    descartable: d.descartable ?? false,
  };
}

/** POST a `ruta` que guarda los cuerpos y responde lo dado (en orden; el último se repite). */
function postEnOrden(ruta: string, ...respuestas: (() => Response | Promise<Response>)[]) {
  const cuerpos: unknown[] = [];
  server.use(
    http.post(apiUrl(ruta), async ({ request }) => {
      cuerpos.push(await request.json());
      return respuestas[Math.min(cuerpos.length - 1, respuestas.length - 1)]();
    }),
  );
  return cuerpos;
}

function franja(): HTMLElement {
  return screen.getByText(/^Hecho en OVZ (por|el)/);
}

describe("FichaOvzPage: marcar como hecho en OVZ (APROBADO)", () => {
  it("al final del formulario, «Marcar como hecho en OVZ» con su pista, sin franja", async () => {
    responderCon(() => HttpResponse.json(detalle()));
    montar();
    await screen.findByRole("heading", { level: 1, name: "Baja de Bovino" });

    const boton = screen.getByRole("button", { name: "Marcar como hecho en OVZ" });
    expect(boton).toHaveAccessibleDescription("Púlsalo cuando lo hayas terminado tú en OVZ. Ganera no envía nada.");
    // Va después de la última sección del formulario.
    const secciones = screen.getAllByRole("region");
    expect(secciones[secciones.length - 1].compareDocumentPosition(boton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.queryByText(/^Hecho en OVZ (por|el)/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Quitar la marca" })).not.toBeInTheDocument();
  });

  it("sin confirmación: envía {version} y la ficha pasa a consulta con la franja y los botones de copiar", async () => {
    const user = userEvent.setup();
    const llamadasGet = responderCon(() => HttpResponse.json(detalle()));
    const cuerpos = postEnOrden("/tramites/128/marcar-hecho-ovz", () =>
      HttpResponse.json(respuestaLista(detalle({ ...HECHO, version: 4 }))),
    );
    montar();
    await user.click(await screen.findByRole("button", { name: "Marcar como hecho en OVZ" }));

    expect(await screen.findByText("Hecho en OVZ por Ana Pérez el 07/10/2026, 12:30")).toBeInTheDocument();
    expect(cuerpos).toEqual([{ version: 3 }]);
    expect(llamadasGet()).toBe(1);
    const cabecera = screen.getAllByRole("banner")[0];
    expect(within(cabecera).getByText("Hecho en OVZ")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Marcar como hecho en OVZ" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Quitar la marca" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Copiar Crotal" })).toBeInTheDocument();
  });

  it("una petición a la vez: el botón se desactiva mientras tanto y un segundo clic no envía nada", async () => {
    const user = userEvent.setup();
    let abrir!: () => void;
    const espera = new Promise<void>((r) => {
      abrir = r;
    });
    responderCon(() => HttpResponse.json(detalle()));
    const cuerpos = postEnOrden("/tramites/128/marcar-hecho-ovz", async () => {
      await espera;
      return HttpResponse.json(respuestaLista(detalle({ ...HECHO, version: 4 })));
    });
    montar();
    await user.click(await screen.findByRole("button", { name: "Marcar como hecho en OVZ" }));

    const enCurso = await screen.findByRole("button", { name: "Marcando…" });
    expect(enCurso).toBeDisabled();
    await user.click(enCurso);
    abrir();
    await screen.findByText(/^Hecho en OVZ por/);
    expect(cuerpos).toHaveLength(1);
  });

  it("409: recarga el detalle y enseña el motivo tal cual", async () => {
    const user = userEvent.setup();
    let get = 0;
    server.use(
      http.get(apiUrl("/tramites/:id"), () => {
        get += 1;
        return HttpResponse.json(
          get === 1 ? detalle() : detalle({ ...HECHO, hechoEnOvzPorNombre: "Luis Gómez", version: 4 }),
        );
      }),
    );
    postEnOrden("/tramites/128/marcar-hecho-ovz", () =>
      HttpResponse.json({ motivo: "Este trámite ya está marcado como hecho en OVZ." }, { status: 409 }),
    );
    montar();
    await user.click(await screen.findByRole("button", { name: "Marcar como hecho en OVZ" }));

    expect(await screen.findByText("Hecho en OVZ por Luis Gómez el 07/10/2026, 12:30")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("Este trámite ya está marcado como hecho en OVZ.");
    expect(get).toBe(2);
  });

  it("404: «Trámite no encontrado»", async () => {
    const user = userEvent.setup();
    responderCon(() => HttpResponse.json(detalle()));
    postEnOrden("/tramites/128/marcar-hecho-ovz", () => new HttpResponse(null, { status: 404 }));
    montar();
    await user.click(await screen.findByRole("button", { name: "Marcar como hecho en OVZ" }));

    expect(await screen.findByRole("heading", { level: 1, name: "Trámite no encontrado" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Marcar como hecho en OVZ" })).not.toBeInTheDocument();
  });

  it.each([
    [
      "5xx",
      () => new HttpResponse(null, { status: 500 }),
      "Ha fallado algo en el servidor. Inténtalo de nuevo; si se repite, avísanos.",
    ],
    ["red", () => HttpResponse.error(), "No se ha podido conectar con Ganera. Inténtalo de nuevo en unos segundos."],
  ])("%s: el mensaje, y el botón sigue ahí para reintentar", async (_nombre, respuesta, mensaje) => {
    const user = userEvent.setup();
    responderCon(() => HttpResponse.json(detalle()));
    const cuerpos = postEnOrden("/tramites/128/marcar-hecho-ovz", respuesta, () =>
      HttpResponse.json(respuestaLista(detalle({ ...HECHO, version: 4 }))),
    );
    montar();
    await user.click(await screen.findByRole("button", { name: "Marcar como hecho en OVZ" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(mensaje);
    const boton = screen.getByRole("button", { name: "Marcar como hecho en OVZ" });
    expect(boton).toBeEnabled();
    await user.click(boton);
    expect(await screen.findByText(/^Hecho en OVZ por/)).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(cuerpos).toHaveLength(2);
  });
});

describe("FichaOvzPage: hecho en OVZ (consulta)", () => {
  it("misma ficha con botones de copiar y la franja arriba, antes del formulario, sin rojo", async () => {
    responderCon(() => HttpResponse.json(detalle(HECHO)));
    montar();

    expect(await screen.findByText("Hecho en OVZ por Ana Pérez el 07/10/2026, 12:30")).toBeInTheDocument();
    const titulo = screen.getByRole("heading", { level: 1, name: "Baja de Bovino" });
    expect(franja().compareDocumentPosition(titulo) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.getByRole("button", { name: "Copiar Crotal" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Copiar Explotación" })).toBeInTheDocument();
    expect(screen.queryByText("Vista previa: aprueba el trámite antes de pasarlo a OVZ.")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Marcar como hecho en OVZ" })).not.toBeInTheDocument();
    const contenedor = franja().closest("[data-franja-hecho-en-ovz]") as HTMLElement;
    expect(contenedor).not.toBeNull();
    expect(contenedor.className).not.toMatch(/destructive|danger|primary/);
    expect(contenedor.className).toMatch(/bg-success/);
  });

  it("sin nombre: «Hecho en OVZ el …»", async () => {
    responderCon(() => HttpResponse.json(detalle({ ...HECHO, hechoEnOvzPorNombre: null })));
    montar();

    expect(await screen.findByText("Hecho en OVZ el 07/10/2026, 12:30")).toBeInTheDocument();
  });

  it("«Quitar la marca» pide confirmación en línea; Cancelar no envía nada y devuelve el foco", async () => {
    const user = userEvent.setup();
    responderCon(() => HttpResponse.json(detalle(HECHO)));
    const cuerpos = postEnOrden("/tramites/128/desmarcar-hecho-ovz", () =>
      HttpResponse.json(respuestaLista(detalle({ version: 4 }))),
    );
    montar();
    await user.click(await screen.findByRole("button", { name: "Quitar la marca" }));

    const grupo = screen.getByRole("group", { name: "¿Quitar la marca de hecho en OVZ? En OVZ no cambia nada." });
    expect(within(grupo).getByRole("button", { name: "Cancelar" })).toHaveFocus();
    await user.click(within(grupo).getByRole("button", { name: "Cancelar" }));

    expect(screen.queryByRole("group")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Quitar la marca" })).toHaveFocus();
    expect(cuerpos).toHaveLength(0);
  });

  it("Esc cancela la confirmación", async () => {
    const user = userEvent.setup();
    responderCon(() => HttpResponse.json(detalle(HECHO)));
    montar();
    await user.click(await screen.findByRole("button", { name: "Quitar la marca" }));
    expect(screen.getByRole("group")).toBeInTheDocument();

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("group")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Quitar la marca" })).toHaveFocus();
  });

  it("«Sí, quitar la marca» envía {version} y la ficha vuelve a copiable con el botón de marcar", async () => {
    const user = userEvent.setup();
    responderCon(() => HttpResponse.json(detalle({ ...HECHO, version: 5 })));
    const cuerpos = postEnOrden("/tramites/128/desmarcar-hecho-ovz", () =>
      HttpResponse.json(respuestaLista(detalle({ version: 6 }))),
    );
    montar();
    await user.click(await screen.findByRole("button", { name: "Quitar la marca" }));
    await user.click(screen.getByRole("button", { name: "Sí, quitar la marca" }));

    expect(await screen.findByRole("button", { name: "Marcar como hecho en OVZ" })).toBeInTheDocument();
    expect(cuerpos).toEqual([{ version: 5 }]);
    expect(screen.queryByText(/^Hecho en OVZ (por|el)/)).not.toBeInTheDocument();
    expect(within(screen.getAllByRole("banner")[0]).getByText("Aprobado · falta OVZ")).toBeInTheDocument();
  });

  it("409 al quitar la marca: recarga y enseña el motivo", async () => {
    const user = userEvent.setup();
    let get = 0;
    server.use(
      http.get(apiUrl("/tramites/:id"), () => {
        get += 1;
        return HttpResponse.json(get === 1 ? detalle(HECHO) : detalle({ version: 9 }));
      }),
    );
    postEnOrden("/tramites/128/desmarcar-hecho-ovz", () =>
      HttpResponse.json(
        { motivo: "Solo se puede quitar la marca de un trámite marcado como hecho en OVZ." },
        { status: 409 },
      ),
    );
    montar();
    await user.click(await screen.findByRole("button", { name: "Quitar la marca" }));
    await user.click(screen.getByRole("button", { name: "Sí, quitar la marca" }));

    expect(await screen.findByRole("button", { name: "Marcar como hecho en OVZ" })).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Solo se puede quitar la marca de un trámite marcado como hecho en OVZ.",
    );
    expect(get).toBe(2);
  });
});

describe("FichaOvzPage: honestidad (nada dice que Ganera haya hecho algo en OVZ)", () => {
  const PROHIBIDAS = /enviad|ejecutad|registrad|en curso en OVZ|tramitad|completad|procesad|presentad/i;

  /** Textos de la página que mencionan OVZ (nodos de texto), sin repetir y ordenados. */
  function mencionesOvz(): string[] {
    const textos = new Set<string>();
    const recorrer = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    for (let nodo = recorrer.nextNode(); nodo; nodo = recorrer.nextNode()) {
      const texto = nodo.textContent?.trim() ?? "";
      if (/OVZ/.test(texto)) textos.add(texto);
    }
    return [...textos].sort();
  }

  // Lo que la ficha ya decía antes de esta tarea (es una ficha «para OVZ»): nada habla de lo hecho.
  // Las tres últimas son avisos y pistas del formulario de baja (fichaOvz.ts).
  const FIJAS = [
    "En OVZ:",
    "Entrar en OVZ",
    "Ficha para OVZ",
    "Para copiar en OVZ. Ganera no envía nada.",
    "Entrega la copia del ejemplar MER en la OVZ en 15 días",
    "Falta · Lo rellenas tú en OVZ",
    "OVZ rellena solo los datos del animal (raza, sexo, fecha de nacimiento…)",
  ];

  it.each([
    ["PENDIENTE_REVISION", {}, ["Pendiente de revisión", "Vista previa: aprueba el trámite antes de pasarlo a OVZ."]],
    [
      "APROBADO",
      {},
      [
        "Aprobado · falta OVZ",
        "Marcar como hecho en OVZ",
        "Púlsalo cuando lo hayas terminado tú en OVZ. Ganera no envía nada.",
      ],
    ],
    ["HECHO_EN_OVZ", HECHO, ["Hecho en OVZ", "Hecho en OVZ por Ana Pérez el 07/10/2026, 12:30"]],
  ] as const)("%s: ninguna palabra prohibida y solo las menciones de OVZ permitidas", async (estado, extra, nuevas) => {
    responderCon(() => HttpResponse.json(detalle({ ...extra, estado })));
    montar();
    await screen.findByRole("heading", { level: 1, name: "Baja de Bovino" });

    expect(document.body).not.toHaveTextContent(PROHIBIDAS);
    expect(mencionesOvz()).toEqual([...FIJAS, ...nuevas].filter((t) => /OVZ/.test(t)).sort());
  });

  // Revisión de T3 (hallazgo 3): también los estados sin ficha, rechazado y descartado.
  it.each([
    ["RECHAZADO sin motivo", null, "Trámite rechazado", "Un trámite rechazado no se pasa a OVZ."],
    ["RECHAZADO descartado", "SIN_TRAMITE", "Descartado como mensaje sin trámite", "Un mensaje descartado no se pasa a OVZ."],
  ] as const)("%s: ninguna palabra prohibida; OVZ solo en la cabecera y en su frase", async (_caso, motivoRechazo, titulo, frase) => {
    responderCon(() => HttpResponse.json(detalle({ estado: "RECHAZADO", motivoRechazo })));
    montar();
    await screen.findByRole("heading", { level: 1, name: titulo });

    expect(document.body).not.toHaveTextContent(PROHIBIDAS);
    expect(mencionesOvz()).toEqual(["Ficha para OVZ", frase].sort());
  });

  it("con la confirmación de quitar la marca abierta, tampoco", async () => {
    const user = userEvent.setup();
    responderCon(() => HttpResponse.json(detalle(HECHO)));
    montar();
    await user.click(await screen.findByRole("button", { name: "Quitar la marca" }));

    expect(document.body).not.toHaveTextContent(PROHIBIDAS);
    expect(mencionesOvz()).toEqual(
      [
        ...FIJAS,
        "Hecho en OVZ",
        "Hecho en OVZ por Ana Pérez el 07/10/2026, 12:30",
        "¿Quitar la marca de hecho en OVZ? En OVZ no cambia nada.",
      ].sort(),
    );
  });
});

// ---------------------------------------------------------------------------------------------
// Correcciones tras la revisión de la T2 (I1, M1, M2).

const ERROR_SERVIDOR = "Ha fallado algo en el servidor. Inténtalo de nuevo; si se repite, avísanos.";
const PROHIBIDAS_FICHA = /enviad|ejecutad|registrad|en curso en OVZ|tramitad|completad|procesad|presentad/i;

function regionEstado(): HTMLElement {
  const region = document.querySelector("[data-anuncio-hecho-en-ovz]");
  if (!(region instanceof HTMLElement)) throw new Error("Sin región de anuncio");
  return region;
}

describe("FichaOvzPage: foco y anuncio tras marcar o quitar la marca (I1)", () => {
  it("tras marcar (200): el foco va a la franja y se anuncia «Marca guardada.» en una región educada", async () => {
    const user = userEvent.setup();
    responderCon(() => HttpResponse.json(detalle()));
    postEnOrden("/tramites/128/marcar-hecho-ovz", () =>
      HttpResponse.json(respuestaLista(detalle({ ...HECHO, version: 4 }))),
    );
    montar();
    expect(regionEstado()).toHaveAttribute("role", "status");
    await user.click(await screen.findByRole("button", { name: "Marcar como hecho en OVZ" }));

    await screen.findByText(/^Hecho en OVZ por/);
    await waitFor(() => expect(franja().closest("[data-franja-hecho-en-ovz]")).toHaveFocus());
    expect(regionEstado()).toHaveTextContent("Marca guardada.");
  });

  it("M5: la petición de foco se atiende una vez: tras marcar, pulsar un «Copiar» deja el foco en ese botón", async () => {
    const user = userEvent.setup();
    responderCon(() => HttpResponse.json(detalle()));
    postEnOrden("/tramites/128/marcar-hecho-ovz", () =>
      HttpResponse.json(respuestaLista(detalle({ ...HECHO, version: 4 }))),
    );
    montar();
    await user.click(await screen.findByRole("button", { name: "Marcar como hecho en OVZ" }));
    await waitFor(() => expect(franja().closest("[data-franja-hecho-en-ovz]")).toHaveFocus());
    const writeText = portapapelesQueFunciona();

    const copiar = screen.getByRole("button", { name: "Copiar Crotal" });
    await user.click(copiar);

    expect(writeText).toHaveBeenCalledWith("ES010000001234");
    // Copiar vuelve a pintar la ficha (la fila queda marcada): el foco no vuelve a la franja.
    await within(fila("Crotal")).findByText("(copiado)");
    expect(copiar).toHaveFocus();
  });

  it("al pulsar «Sí, quitar la marca» el foco va a la franja mientras se envía; tras el 200, a «Marcar como hecho en OVZ»", async () => {
    const user = userEvent.setup();
    let abrir!: () => void;
    const espera = new Promise<void>((r) => {
      abrir = r;
    });
    responderCon(() => HttpResponse.json(detalle({ ...HECHO, version: 5 })));
    postEnOrden("/tramites/128/desmarcar-hecho-ovz", async () => {
      await espera;
      return HttpResponse.json(respuestaLista(detalle({ version: 6 })));
    });
    montar();
    await user.click(await screen.findByRole("button", { name: "Quitar la marca" }));
    await user.click(screen.getByRole("button", { name: "Sí, quitar la marca" }));

    await waitFor(() => expect(franja().closest("[data-franja-hecho-en-ovz]")).toHaveFocus());
    expect(document.activeElement).not.toBe(document.body);
    abrir();
    const marcar = await screen.findByRole("button", { name: "Marcar como hecho en OVZ" });
    await waitFor(() => expect(marcar).toHaveFocus());
    expect(regionEstado()).toHaveTextContent("Marca quitada.");
  });

  it.each([
    ["5xx", () => new HttpResponse(null, { status: 500 })],
    ["409", () => HttpResponse.json({ motivo: "El trámite ha cambiado desde que lo abriste." }, { status: 409 })],
  ])("marcar con %s: el foco va al aviso de error", async (_nombre, respuesta) => {
    const user = userEvent.setup();
    responderCon(() => HttpResponse.json(detalle()));
    postEnOrden("/tramites/128/marcar-hecho-ovz", respuesta);
    montar();
    await user.click(await screen.findByRole("button", { name: "Marcar como hecho en OVZ" }));

    const aviso = await screen.findByRole("alert");
    expect(aviso).toHaveTextContent("No se ha podido marcar el trámite");
    await waitFor(() => expect(aviso).toHaveFocus());
    expect(regionEstado()).toHaveTextContent("");
  });

  it("quitar la marca con 5xx: el foco va al aviso de error, con su título", async () => {
    const user = userEvent.setup();
    responderCon(() => HttpResponse.json(detalle(HECHO)));
    postEnOrden("/tramites/128/desmarcar-hecho-ovz", () => new HttpResponse(null, { status: 500 }));
    montar();
    await user.click(await screen.findByRole("button", { name: "Quitar la marca" }));
    await user.click(screen.getByRole("button", { name: "Sí, quitar la marca" }));

    const aviso = await screen.findByRole("alert");
    expect(aviso).toHaveTextContent("No se ha podido quitar la marca");
    expect(aviso).toHaveTextContent(ERROR_SERVIDOR);
    await waitFor(() => expect(aviso).toHaveFocus());
    expect(screen.getByRole("button", { name: "Quitar la marca" })).toBeEnabled();
  });
});

describe("FichaOvzPage: red o 5xx recargan el detalle (M2)", () => {
  it("si la recarga muestra la marca puesta, no hay error: la franja, el anuncio y el foco como en un 200", async () => {
    const user = userEvent.setup();
    let get = 0;
    server.use(
      http.get(apiUrl("/tramites/:id"), () => {
        get += 1;
        return HttpResponse.json(get === 1 ? detalle() : detalle({ ...HECHO, version: 4 }));
      }),
    );
    postEnOrden("/tramites/128/marcar-hecho-ovz", () => HttpResponse.error());
    montar();
    await user.click(await screen.findByRole("button", { name: "Marcar como hecho en OVZ" }));

    expect(await screen.findByText("Hecho en OVZ por Ana Pérez el 07/10/2026, 12:30")).toBeInTheDocument();
    expect(get).toBe(2);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(regionEstado()).toHaveTextContent("Marca guardada.");
    await waitFor(() => expect(franja().closest("[data-franja-hecho-en-ovz]")).toHaveFocus());
  });

  it("si la recarga tras el 5xx también falla, se conserva la ficha con el error y el botón", async () => {
    const user = userEvent.setup();
    let get = 0;
    server.use(
      http.get(apiUrl("/tramites/:id"), () => {
        get += 1;
        return get === 1 ? HttpResponse.json(detalle()) : new HttpResponse(null, { status: 503 });
      }),
    );
    postEnOrden("/tramites/128/marcar-hecho-ovz", () => new HttpResponse(null, { status: 500 }));
    montar();
    await user.click(await screen.findByRole("button", { name: "Marcar como hecho en OVZ" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(ERROR_SERVIDOR);
    expect(get).toBe(2);
    expect(screen.getByRole("heading", { level: 1, name: "Baja de Bovino" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Marcar como hecho en OVZ" })).toBeEnabled();
  });
});

describe("FichaOvzPage: honestidad tras las acciones y con errores (M1)", () => {
  it("tras marcar y tras quitar la marca: ninguna palabra prohibida", async () => {
    const user = userEvent.setup();
    let get = 0;
    server.use(
      http.get(apiUrl("/tramites/:id"), () => {
        get += 1;
        return HttpResponse.json(detalle());
      }),
    );
    postEnOrden("/tramites/128/marcar-hecho-ovz", () =>
      HttpResponse.json(respuestaLista(detalle({ ...HECHO, version: 4 }))),
    );
    postEnOrden("/tramites/128/desmarcar-hecho-ovz", () => HttpResponse.json(respuestaLista(detalle({ version: 5 }))));
    montar();
    await user.click(await screen.findByRole("button", { name: "Marcar como hecho en OVZ" }));
    await screen.findByText(/^Hecho en OVZ por/);
    expect(document.body).not.toHaveTextContent(PROHIBIDAS_FICHA);

    await user.click(screen.getByRole("button", { name: "Quitar la marca" }));
    await user.click(screen.getByRole("button", { name: "Sí, quitar la marca" }));
    await screen.findByRole("button", { name: "Marcar como hecho en OVZ" });
    expect(document.body).not.toHaveTextContent(PROHIBIDAS_FICHA);
    expect(get).toBe(1);
  });

  it.each([
    ["marcar", "409", detalle(), "/tramites/128/marcar-hecho-ovz", "No se ha podido marcar el trámite"],
    ["marcar", "5xx", detalle(), "/tramites/128/marcar-hecho-ovz", "No se ha podido marcar el trámite"],
    ["quitar la marca", "409", detalle(HECHO), "/tramites/128/desmarcar-hecho-ovz", "No se ha podido quitar la marca"],
    ["quitar la marca", "5xx", detalle(HECHO), "/tramites/128/desmarcar-hecho-ovz", "No se ha podido quitar la marca"],
  ] as const)("%s con %s: el aviso de error, con su título, no dice nada prohibido", async (accion, error, inicial, ruta, titulo) => {
    const user = userEvent.setup();
    responderCon(() => HttpResponse.json(inicial));
    postEnOrden(ruta, () =>
      error === "409"
        ? HttpResponse.json({ status: 409 }, { status: 409 })
        : new HttpResponse(null, { status: 500 }),
    );
    montar();
    if (accion === "marcar") {
      await user.click(await screen.findByRole("button", { name: "Marcar como hecho en OVZ" }));
    } else {
      await user.click(await screen.findByRole("button", { name: "Quitar la marca" }));
      await user.click(screen.getByRole("button", { name: "Sí, quitar la marca" }));
    }

    const aviso = await screen.findByRole("alert");
    expect(within(aviso).getByText(titulo)).toBeInTheDocument();
    expect(document.body).not.toHaveTextContent(PROHIBIDAS_FICHA);
  });
});

describe("FichaOvzPage: piel (diseño fase 1)", () => {
  it("cabecera con el divisor de 2 px, secciones como tarjetas rectas con borde y la cita sobre Fondo", async () => {
    responderCon(() => HttpResponse.json(detalle()));
    montar();
    await screen.findByRole("heading", { level: 1 });

    const cabecera = screen.getAllByRole("banner")[0];
    expect(cabecera).toHaveClass("sticky", "bg-sidebar", "border-b-2", "border-divisor");
    const seccion = screen.getByRole("region", { name: "Entrar en OVZ" });
    expect(seccion).toHaveClass("bg-card", "border", "border-border");
    expect(seccion.className).not.toMatch(/ring-foreground\/10|rounded/);
    // «Copiar», secundario de 30 px.
    expect(screen.getByRole("button", { name: "Copiar NIF del titular" })).toHaveClass("border-input", "h-[30px]");
    const cita = screen.getByText("Se ha muerto la 1234 esta mañana").closest("blockquote");
    expect(cita).toHaveClass("bg-background", "border", "border-border");
    expect(cita).not.toHaveClass("bg-muted");
  });

  it("un descartado va en el par neutro", async () => {
    responderCon(() => HttpResponse.json(detalle({ estado: "RECHAZADO", motivoRechazo: "SIN_TRAMITE" })));
    montar();

    const titulo = await screen.findByRole("heading", { level: 1, name: "Descartado como mensaje sin trámite" });
    expect(titulo.parentElement).toHaveClass("bg-neutral", "text-neutral-foreground");
    const pista = screen.getByText("Un mensaje descartado no se pasa a OVZ.");
    expect(pista).not.toHaveClass("text-muted-foreground");
    // Hereda el texto del par neutro de la banda (Gris 800 sobre Gris 200), no un gris propio.
    expect(pista.closest(".bg-neutral")).toBe(titulo.parentElement);
  });
});
