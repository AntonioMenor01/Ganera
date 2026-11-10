import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { AuthProvider } from "@/shared/auth/AuthContext";
import type { Tramite, TramiteDetalle } from "@/features/tramites/types";
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

function detalleDe(t: Tramite, extra: Partial<TramiteDetalle> = {}): TramiteDetalle {
  return {
    id: t.id,
    tipoTramite: t.tipoTramite,
    estado: t.estado,
    motivoError: null,
    explotacionId: t.explotacionId,
    explotacionCodigoRega: t.explotacionCodigoRega,
    explotacionNombre: t.explotacionNombre,
    ganaderoNombre: "Pedro Sanz",
    ganaderoNif: "12345678Z",
    mensajeOriginal: "Ha nacido un ternero, crotal 1234",
    crotales: [],
    version: t.version,
    origen: "WHATSAPP",
    estadoExtraccion: "COMPLETADA",
    crotalesDescartados: 0,
    hechoEnOvzEn: null,
    hechoEnOvzPorNombre: null,
    whatsapp: {
      destinatarioNombre: "Pedro Sanz",
      telefonoEnmascarado: "+34******122",
      contactoActivo: true,
      ventanaAbiertaHasta: "2099-01-01T00:00:00Z",
      firmaGestoria: "Gestoría Demo",
      motivoNoDisponible: null,
      respuestas: [
        {
          id: 9,
          texto: "Recibido, lo miramos.",
          estado: "ENTREGADO",
          enviadoPorNombre: "Ana",
          enviadoEn: "2026-10-07T10:35:00Z",
          codigoError: null,
          motivoError: null,
        },
      ],
    },
    motivoRechazo: null,
    descartable: false,
    historialDescarte: [],
    fechaHecho: null,
    fechaNacimiento: null,
    sexo: null,
    crotalMadreIndicado: null,
    numeroMer: null,
    destinoRega: null,
    destinoTexto: null,
    consignatario: null,
    aptitudMovimiento: null,
    fechaIdentificacion: null,
    ...extra,
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
  const router = createMemoryRouter([{ path: "/", element: <BandejaPage /> }], {
    initialEntries: ["/"],
  });
  render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  );
}

describe("BandejaPage (mock v2)", () => {
  it("lista solo WhatsApp, carga el detalle y muestra mensaje + respuesta", async () => {
    conSesion();
    const baja = tramite({ id: 1, origen: "WHATSAPP", tipoTramite: "BAJA_MUERTE", explotacionNombre: "Dehesa Norte" });
    server.use(
      http.get(apiUrl("/tramites"), () =>
        HttpResponse.json({
          content: [
            baja,
            tramite({ id: 2, origen: null, tipoTramite: "DECLARACION_CENSO", explotacionNombre: "Otro" }),
          ],
          totalElements: 2,
          totalPages: 1,
          number: 0,
          size: 50,
        }),
      ),
      http.get(apiUrl("/tramites/1"), () => HttpResponse.json(detalleDe(baja))),
    );
    montar();

    expect(await screen.findByRole("heading", { name: "Bandeja" })).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: /Dehesa Norte/ })).toBeInTheDocument();
    expect(screen.queryByText(/Declaración de censo|Censo/)).not.toBeInTheDocument();

    expect(await screen.findByText("Ha nacido un ternero, crotal 1234")).toBeInTheDocument();
    expect(screen.getByText("Recibido, lo miramos.")).toBeInTheDocument();
    expect(screen.getByText(/\+34\*{6}122/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Revisar y responder" })).toBeInTheDocument();
  });

  it("el filtro Pendientes oculta los hechos en OVZ", async () => {
    conSesion();
    const pendiente = tramite({ id: 1, explotacionNombre: "Finca A" });
    const hecho = tramite({
      id: 2,
      explotacionNombre: "Finca B",
      estado: "HECHO_EN_OVZ",
      hechoEnOvzEn: "2026-10-07T10:30:00Z",
      hechoEnOvzPorNombre: "Ana",
    });
    server.use(
      http.get(apiUrl("/tramites"), () =>
        HttpResponse.json({
          content: [pendiente, hecho],
          totalElements: 2,
          totalPages: 1,
          number: 0,
          size: 50,
        }),
      ),
      http.get(apiUrl("/tramites/:id"), ({ params }) => {
        const id = Number(params.id);
        const t = id === 1 ? pendiente : hecho;
        return HttpResponse.json(detalleDe(t));
      }),
    );
    montar();

    expect(await screen.findByRole("button", { name: /Finca A/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Finca B/ })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "Pendientes" }));
    expect(screen.getByRole("button", { name: /Finca A/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Finca B/ })).not.toBeInTheDocument();
  });

  it("abre el diálogo de revisión desde Revisar y responder", async () => {
    conSesion();
    const t = tramite({ id: 7, explotacionNombre: "Los Llanos" });
    server.use(
      http.get(apiUrl("/tramites"), () =>
        HttpResponse.json({
          content: [t],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 50,
        }),
      ),
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalleDe(t))),
    );
    montar();

    await screen.findByText("Ha nacido un ternero, crotal 1234");
    await userEvent.click(screen.getByRole("button", { name: "Revisar y responder" }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("heading", { name: /Trámite #7/ })).toBeInTheDocument();
  });
});
