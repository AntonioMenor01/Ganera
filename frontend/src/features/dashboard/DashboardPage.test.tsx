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

/** Detalle mínimo válido para que el modal de revisión (que la cola abre en esta pantalla) cargue. */
function detalle(id: number) {
  return {
    id,
    tipoTramite: "ALTA_NACIMIENTO",
    estado: "PENDIENTE_REVISION",
    motivoError: null,
    explotacionId: 10,
    explotacionCodigoRega: "ES100370000124",
    explotacionNombre: "Dehesa Los Llanos",
    ganaderoNombre: "Ana Martínez",
    ganaderoNif: "12345678Z",
    mensajeOriginal: "alta del 1234",
    crotales: [],
    version: 0,
    origen: "WHATSAPP",
    estadoExtraccion: "COMPLETADA",
    crotalesDescartados: 0,
    hechoEnOvzEn: null,
    hechoEnOvzPorNombre: null,
    whatsapp: null,
    motivoRechazo: null,
    descartable: false,
    historialDescarte: [],
    fechaHecho: null,
    fechaNacimiento: null,
    sexo: null,
    crotalMadreIndicado: null,
    crotalMadre: null,
    crotalMadreResolucion: null,
    numeroMer: null,
    destinoRega: null,
    destinoTexto: null,
    consignatario: null,
    aptitudMovimiento: null,
    fechaIdentificacion: null,
    raza: null,
    sePresentaraAutomaticamente: false,
    presentacionOvz: null,
  };
}

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
      { path: "/ganaderos/:id", element: <h1>Detalle ganadero</h1> },
      { path: "/bandeja", element: <h1>Bandeja (destino)</h1> },
      { path: "/calendario", element: <h1>Calendario (destino)</h1> },
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

/** Handler de /tramites consciente del estado: la cola (PENDIENTE_REVISION, size>1) y los conteos
 * del embudo (size=1) por estado; cualquier otra llamada devuelve algo benigno. */
function tramitesPorEstado(mapa: Partial<Record<string, ReturnType<typeof paginaTramites>>>) {
  server.use(
    http.get(apiUrl("/tramites"), ({ request }) => {
      const estado = new URL(request.url).searchParams.get("estado");
      return HttpResponse.json(mapa[estado ?? "TODOS"] ?? paginaTramites([], 0));
    }),
  );
}

describe("DashboardPage: cabecera y CTAs", () => {
  it("saluda por su nombre, muestra la fecha y los botones de cabecera", async () => {
    conSesion();
    tramitesPorEstado({ PENDIENTE_REVISION: paginaTramites([]) });
    server.use(http.get(apiUrl("/ganaderos"), () => HttpResponse.json(paginaGanaderos(0))));
    montar();

    const titulo = await screen.findByRole("heading", { level: 1 });
    expect(titulo.textContent).toMatch(/^Buen[oa]s (días|tardes|noches), Lucía$/);
    expect(screen.getByText(/\bde \d{4}$/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Añadir ganadero" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Nuevo trámite" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Primeros pasos/ })).toBeInTheDocument();
  });
});

describe("DashboardPage: KPIs del día", () => {
  it("muestra las cifras reales y lleva a su sección", async () => {
    conSesion();
    tramitesPorEstado({ PENDIENTE_REVISION: paginaTramites([tramite({ id: 1 })], 5) });
    server.use(http.get(apiUrl("/ganaderos"), () => HttpResponse.json(paginaGanaderos(42))));
    const router = montar();

    const porRevisar = await screen.findByRole("button", { name: /Trámites por revisar/ });
    expect(within(porRevisar).getByText("5")).toBeInTheDocument();
    const ganaderos = screen.getByRole("button", { name: /^Ganaderos/ });
    expect(within(ganaderos).getByText("42")).toBeInTheDocument();

    await userEvent.click(ganaderos);
    await waitFor(() => expect(router.state.location.pathname).toBe("/ganaderos"));
  });

  it("cuenta los WhatsApp pendientes en «Mensajes nuevos»", async () => {
    conSesion();
    tramitesPorEstado({
      PENDIENTE_REVISION: paginaTramites(
        [
          tramite({ id: 1, origen: "WHATSAPP" }),
          tramite({ id: 2, origen: null }),
          tramite({ id: 3, origen: "WHATSAPP" }),
        ],
        3,
      ),
    });
    server.use(http.get(apiUrl("/ganaderos"), () => HttpResponse.json(paginaGanaderos(0))));
    montar();

    const mensajes = await screen.findByRole("button", { name: /Mensajes nuevos/ });
    expect(within(mensajes).getByText("2")).toBeInTheDocument();
  });

  it("si falla el conteo de ganaderos, su KPI muestra «—» pero la cola sigue cargando", async () => {
    conSesion();
    tramitesPorEstado({ PENDIENTE_REVISION: paginaTramites([tramite({ id: 1 })], 3) });
    server.use(http.get(apiUrl("/ganaderos"), () => new HttpResponse(null, { status: 500 })));
    montar();

    const ganaderos = await screen.findByRole("button", { name: /^Ganaderos/ });
    expect(within(ganaderos).getByText("—")).toBeInTheDocument();
    const porRevisar = screen.getByRole("button", { name: /Trámites por revisar/ });
    expect(within(porRevisar).getByText("3")).toBeInTheDocument();
  });
});

describe("DashboardPage: trámites por revisar (trabajo en la misma pantalla)", () => {
  it("lista los pendientes y, al abrir uno, la revisión se abre aquí (sin navegar)", async () => {
    conSesion();
    tramitesPorEstado({
      PENDIENTE_REVISION: paginaTramites(
        [
          tramite({ id: 7, tipoTramite: "ALTA_NACIMIENTO" }),
          tramite({ id: 8, tipoTramite: "BAJA_MUERTE" }),
        ],
        2,
      ),
    });
    server.use(
      http.get(apiUrl("/ganaderos"), () => HttpResponse.json(paginaGanaderos(42))),
      http.get(apiUrl("/tramites/:id"), ({ params }) =>
        HttpResponse.json(detalle(Number(params.id))),
      ),
    );
    const router = montar();

    const region = await screen.findByRole("region", { name: "Trámites por revisar" });
    expect(within(region).getByText("Alta por nacimiento")).toBeInTheDocument();
    expect(within(region).getByText("Baja por muerte")).toBeInTheDocument();

    await userEvent.click(within(region).getByRole("button", { name: "Revisar trámite #7" }));

    // El modal se abre EN el inicio (no se navega a /tramites).
    const dialogo = await screen.findByRole("dialog");
    expect(within(dialogo).getByText("Trámite #7")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
  });

  it("sin pendientes, estado vacío honesto («Todo revisado»)", async () => {
    conSesion();
    tramitesPorEstado({ PENDIENTE_REVISION: paginaTramites([], 0) });
    server.use(http.get(apiUrl("/ganaderos"), () => HttpResponse.json(paginaGanaderos(42))));
    montar();

    const region = await screen.findByRole("region", { name: "Trámites por revisar" });
    expect(within(region).getByText("Todo revisado")).toBeInTheDocument();
    expect(within(region).getByText(/No hay trámites pendientes de revisión/)).toBeInTheDocument();
  });

  it("si falla la cola, lo dice y Reintentar vuelve a pedirla", async () => {
    conSesion();
    let intentos = 0;
    server.use(
      http.get(apiUrl("/ganaderos"), () => HttpResponse.json(paginaGanaderos(42))),
      http.get(apiUrl("/tramites"), ({ request }) => {
        const estado = new URL(request.url).searchParams.get("estado");
        if (estado !== "PENDIENTE_REVISION") return HttpResponse.json(paginaTramites([], 0));
        intentos += 1;
        return intentos === 1
          ? new HttpResponse(null, { status: 500 })
          : HttpResponse.json(paginaTramites([tramite({ id: 1 })], 1));
      }),
    );
    montar();

    expect(await screen.findByText("No se ha podido cargar la cola")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Reintentar" }));

    const region = await screen.findByRole("region", { name: "Trámites por revisar" });
    expect(within(region).getByText("1 por revisar")).toBeInTheDocument();
    expect(intentos).toBe(2);
  });
});

describe("DashboardPage: estado de los trámites (embudo, antigua «Control»)", () => {
  it("muestra los conteos por estado y entra en Trámites ya filtrado", async () => {
    conSesion();
    tramitesPorEstado({
      PENDIENTE_REVISION: paginaTramites([tramite({ id: 1 })], 4),
      APROBADO: paginaTramites([], 6),
      HECHO_EN_OVZ: paginaTramites([], 9),
    });
    server.use(http.get(apiUrl("/ganaderos"), () => HttpResponse.json(paginaGanaderos(2))));
    const router = montar();

    const region = await screen.findByRole("region", { name: "Estado de los trámites" });
    const aprobado = within(region).getByRole("button", { name: /Aprobado · falta OVZ/ });
    expect(within(aprobado).getByText("6")).toBeInTheDocument();
    const hecho = within(region).getByRole("button", { name: /Hecho en OVZ/ });
    expect(within(hecho).getByText("9")).toBeInTheDocument();

    await userEvent.click(aprobado);
    await waitFor(() => expect(router.state.location.pathname).toBe("/tramites"));
    expect((router.state.location.state as { filtroEstado?: string }).filtroEstado).toBe("APROBADO");
  });
});

describe("DashboardPage: alertas y plazos", () => {
  it("lista las alertas y una ligada a un trámite se abre en esta pantalla", async () => {
    conSesion();
    tramitesPorEstado({ PENDIENTE_REVISION: paginaTramites([], 0) });
    server.use(
      http.get(apiUrl("/ganaderos"), () => HttpResponse.json(paginaGanaderos(2))),
      http.get(apiUrl("/alertas"), () =>
        HttpResponse.json([
          {
            id: "a1",
            tipo: "plazo",
            titulo: "Baja sin presentar en OVZ",
            detalle: "Vence en 2 días",
            venceEl: "2026-12-01",
            tramiteId: 42,
            severidad: "critical",
          },
        ]),
      ),
      http.get(apiUrl("/tramites/:id"), ({ params }) => HttpResponse.json(detalle(Number(params.id)))),
    );
    montar();

    const region = await screen.findByRole("region", { name: "Alertas y plazos" });
    const alerta = within(region).getByRole("button", { name: /Baja sin presentar en OVZ/ });
    await userEvent.click(alerta);

    const dialogo = await screen.findByRole("dialog");
    expect(within(dialogo).getByText("Trámite #42")).toBeInTheDocument();
  });
});
