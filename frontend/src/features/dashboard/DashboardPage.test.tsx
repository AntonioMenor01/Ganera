import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { apiUrl } from "@/test/apiBaseUrl";
import { server } from "@/test/server";
import { AuthProvider } from "@/shared/auth/AuthContext";
import type { EstadoTramite, Tramite } from "@/features/tramites/types";
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
  return { content, totalElements, totalPages: 1, number: 0, size: 8 };
}

/** Detalle mínimo válido para que el modal de revisión (que el tablero abre en el Inicio) cargue. */
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

/** /tramites consciente del estado: el tablero pide una página por estado (PENDIENTE_REVISION,
 * APROBADO, RECHAZADO). Cualquier otro estado devuelve una página vacía. */
function tramitesPorEstado(mapa: Partial<Record<EstadoTramite, ReturnType<typeof paginaTramites>>>) {
  server.use(
    http.get(apiUrl("/tramites"), ({ request }) => {
      const estado = new URL(request.url).searchParams.get("estado") as EstadoTramite | null;
      return HttpResponse.json((estado && mapa[estado]) || paginaTramites([], 0));
    }),
  );
}

function montar() {
  const router = createMemoryRouter(
    [
      { path: "/", element: <DashboardPage /> },
      { path: "/tramites", element: <h1>Control (destino)</h1> },
      { path: "/bandeja", element: <h1>Bandeja (destino)</h1> },
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

describe("DashboardPage: cabecera", () => {
  it("saluda, muestra la fecha y los CTAs, sin métricas de vanidad", async () => {
    conSesion();
    tramitesPorEstado({ PENDIENTE_REVISION: paginaTramites([]) });
    montar();

    const titulo = await screen.findByRole("heading", { level: 1 });
    expect(titulo.textContent).toMatch(/^Buen[oa]s (días|tardes|noches), Lucía$/);
    expect(screen.getByText(/\bde \d{4}$/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Nuevo trámite" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Primeros pasos/ })).toBeInTheDocument();
    // Fuera del Inicio: ni «Tu actividad en Ganera», ni alertas, ni ganaderos.
    expect(screen.queryByText(/Tu actividad en Ganera/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Alertas activas/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Añadir ganadero" })).not.toBeInTheDocument();
  });
});

describe("DashboardPage: tablero del día", () => {
  it("muestra las tres columnas con sus conteos y trámites reales", async () => {
    conSesion();
    tramitesPorEstado({
      PENDIENTE_REVISION: paginaTramites([tramite({ id: 1 }), tramite({ id: 2, tipoTramite: "BAJA_MUERTE" })], 3),
      APROBADO: paginaTramites([tramite({ id: 5, estado: "APROBADO" })], 1),
      RECHAZADO: paginaTramites([tramite({ id: 9, estado: "RECHAZADO" })], 1),
    });
    montar();

    const porRevisar = await screen.findByRole("region", { name: /Por revisar/ });
    expect(within(porRevisar).getByRole("heading", { level: 2 }).textContent).toContain("3");
    expect(within(porRevisar).getByText("Alta por nacimiento")).toBeInTheDocument();
    expect(within(porRevisar).getByText("Baja por muerte")).toBeInTheDocument();

    const faltaOvz = screen.getByRole("region", { name: /Aprobado · falta OVZ/ });
    expect(within(faltaOvz).getByRole("heading", { level: 2 }).textContent).toContain("1");

    const rechazados = screen.getByRole("region", { name: /Rechazados y descartados/ });
    expect(within(rechazados).getByRole("heading", { level: 2 }).textContent).toContain("1");
  });

  it("al abrir un trámite, la revisión se abre en el Inicio (sin navegar)", async () => {
    conSesion();
    tramitesPorEstado({ PENDIENTE_REVISION: paginaTramites([tramite({ id: 7 })], 1) });
    server.use(http.get(apiUrl("/tramites/:id"), ({ params }) => HttpResponse.json(detalle(Number(params.id)))));
    const router = montar();

    const porRevisar = await screen.findByRole("region", { name: /Por revisar/ });
    await userEvent.click(within(porRevisar).getByRole("button", { name: "Revisar trámite #7" }));

    const dialogo = await screen.findByRole("dialog");
    expect(within(dialogo).getByText("Trámite #7")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
  });

  it("una columna vacía muestra su texto honesto", async () => {
    conSesion();
    tramitesPorEstado({ APROBADO: paginaTramites([], 0) });
    montar();

    const faltaOvz = await screen.findByRole("region", { name: /Aprobado · falta OVZ/ });
    expect(within(faltaOvz).getByText("Nada pendiente de pasar a OVZ.")).toBeInTheDocument();
  });

  it("«Ver en Control» entra en Control ya filtrado por ese estado", async () => {
    conSesion();
    tramitesPorEstado({ APROBADO: paginaTramites([tramite({ id: 5, estado: "APROBADO" })], 4) });
    const router = montar();

    const faltaOvz = await screen.findByRole("region", { name: /Aprobado · falta OVZ/ });
    await userEvent.click(within(faltaOvz).getByRole("button", { name: /Ver los 4 en Control/ }));
    await waitFor(() => expect(router.state.location.pathname).toBe("/tramites"));
    expect((router.state.location.state as { filtroEstado?: string }).filtroEstado).toBe("APROBADO");
  });

  it("si una columna falla, lo dice y Reintentar vuelve a pedir el tablero", async () => {
    conSesion();
    let intentos = 0;
    server.use(
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

    const porRevisar = await screen.findByRole("region", { name: /Por revisar/ });
    expect(await within(porRevisar).findByText("No se ha podido cargar")).toBeInTheDocument();
    await userEvent.click(within(porRevisar).getByRole("button", { name: "Reintentar" }));

    expect(await within(porRevisar).findByRole("button", { name: "Revisar trámite #1" })).toBeInTheDocument();
    expect(intentos).toBe(2);
  });
});
