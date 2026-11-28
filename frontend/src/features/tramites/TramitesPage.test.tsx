import { render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { MemoryRouter } from "react-router-dom"
import { describe, expect, it } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { TramitesPage } from "./TramitesPage"

/**
 * Control es un tablero de mando por fases (rediseño 2026-11-27): ni filtro de pestañas ni tabla.
 * Cuatro columnas que cubren los ocho estados; cada columna pide su página por estado. Cada tarjeta
 * abre la revisión en la misma pantalla. El Inicio es el tablero del DÍA; Control, el flujo completo.
 */

function tramite(id: number, extra: Record<string, unknown> = {}) {
  return {
    id,
    explotacionId: null,
    explotacionCodigoRega: null,
    explotacionNombre: null,
    tipoTramite: "ALTA_NACIMIENTO",
    estado: "PENDIENTE_REVISION",
    motivoError: null,
    crotales: [],
    version: 0,
    origen: null,
    estadoExtraccion: null,
    crotalesDescartados: 0,
    hechoEnOvzEn: null,
    hechoEnOvzPorNombre: null,
    motivoRechazo: null,
    descartable: false,
    ...extra,
  }
}

function detalle(id: number, extra: Record<string, unknown> = {}) {
  return {
    ...tramite(id, extra),
    mensajeOriginal: null,
    whatsapp: null,
    historialDescarte: [],
  }
}

type Tramite = ReturnType<typeof tramite>

/** Respuestas de /tramites por estado. El tablero hace una petición por estado; esta función reparte
 * cada trámite en la respuesta de SU estado (como el backend real), con el total que se le pida. */
function porEstado(
  tramites: Tramite[],
  totales: Partial<Record<string, number>> = {},
) {
  return http.get(apiUrl("/tramites"), ({ request }) => {
    const estado = new URL(request.url).searchParams.get("estado") ?? ""
    const content = tramites.filter((t) => t.estado === estado)
    const totalElements = totales[estado] ?? content.length
    return HttpResponse.json({ content, totalElements, totalPages: 1, number: 0, size: 50 })
  })
}

function montar(entrada: { pathname: string; state?: unknown } | string = "/tramites") {
  return render(
    <MemoryRouter initialEntries={[entrada]}>
      <TramitesPage />
    </MemoryRouter>,
  )
}

function columna(nombre: RegExp | string) {
  return screen.getByRole("region", { name: nombre })
}

describe("TramitesPage (Control): tablero de mando", () => {
  it("la cabecera dice «Control», el total y tiene «Nuevo trámite»", async () => {
    server.use(
      porEstado([
        tramite(1, { estado: "PENDIENTE_REVISION" }),
        tramite(2, { estado: "APROBADO" }),
        tramite(3, { estado: "RECHAZADO" }),
      ]),
    )
    montar()

    expect(await screen.findByRole("heading", { level: 1, name: "Control" })).toBeInTheDocument()
    expect(await screen.findByText("3 trámites en total")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Nuevo trámite" })).toBeInTheDocument()
    // Sin filtro de pestañas ni tabla (lo que el cliente pidió quitar).
    expect(screen.queryByRole("tablist")).not.toBeInTheDocument()
    expect(screen.queryByRole("table")).not.toBeInTheDocument()
  })

  it("hay cuatro columnas por fase, con sus títulos", async () => {
    server.use(porEstado([]))
    montar()

    expect(await screen.findByRole("heading", { level: 2, name: /Por revisar/ })).toBeInTheDocument()
    expect(screen.getByRole("heading", { level: 2, name: /Aprobado · falta OVZ/ })).toBeInTheDocument()
    expect(screen.getByRole("heading", { level: 2, name: /Hecho en OVZ/ })).toBeInTheDocument()
    expect(screen.getByRole("heading", { level: 2, name: /Rechazados y descartados/ })).toBeInTheDocument()
  })

  it("cada columna agrupa sus estados y suma el recuento", async () => {
    server.use(
      porEstado([
        tramite(10, { estado: "PENDIENTE_EXTRACCION" }),
        tramite(11, { estado: "PENDIENTE_REVISION" }),
        tramite(20, { estado: "APROBADO" }),
        tramite(21, { estado: "EN_PROCESO" }),
        tramite(22, { estado: "ERROR_OVZ" }),
        tramite(30, { estado: "HECHO_EN_OVZ" }),
        tramite(31, { estado: "EJECUTADO_OVZ" }),
        tramite(40, { estado: "RECHAZADO" }),
      ]),
    )
    montar()

    // «Por revisar» = PENDIENTE_EXTRACCION + PENDIENTE_REVISION (2).
    const porRevisar = columna(/Por revisar/)
    expect(await within(porRevisar).findByText("2")).toBeInTheDocument()
    // «Aprobado · falta OVZ» = APROBADO + EN_PROCESO + ERROR_OVZ (3).
    expect(within(columna(/Aprobado · falta OVZ/)).getByText("3")).toBeInTheDocument()
    // «Hecho en OVZ» = HECHO_EN_OVZ + EJECUTADO_OVZ (2).
    expect(within(columna(/Hecho en OVZ/)).getByText("2")).toBeInTheDocument()
    // «Rechazados y descartados» = RECHAZADO (1).
    expect(within(columna(/Rechazados y descartados/)).getByText("1")).toBeInTheDocument()
  })

  it("una tarjeta muestra el tipo, el ganadero y el badge, y abre la revisión en la misma pantalla", async () => {
    server.use(
      porEstado([
        tramite(7, {
          estado: "PENDIENTE_REVISION",
          tipoTramite: "SOLICITUD_MOVIMIENTO",
          explotacionCodigoRega: "ES280790000123",
          explotacionNombre: "Finca La Dehesa",
          ganaderoNombre: "Ganadería Hermanos Prieto SC",
        }),
      ]),
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle(7, { tipoTramite: "SOLICITUD_MOVIMIENTO" }))),
    )
    const user = userEvent.setup()
    montar()

    const tarjeta = await screen.findByRole("button", { name: "Revisar trámite #7" })
    expect(within(tarjeta).getByText("Solicitud de movimiento")).toBeInTheDocument()
    // MEJ-09: el ganadero dueño, para ver de quién es el trámite.
    expect(within(tarjeta).getByText("Ganadería Hermanos Prieto SC")).toBeInTheDocument()
    expect(within(tarjeta).getByText("Pendiente de revisión")).toBeInTheDocument()
    expect(within(tarjeta).getByText(/ES280790000123/)).toBeInTheDocument()
    // Nunca el enum crudo.
    expect(screen.queryByText("SOLICITUD_MOVIMIENTO")).not.toBeInTheDocument()

    await user.click(tarjeta)
    expect(await screen.findByRole("dialog")).toBeInTheDocument()
  })

  it("si el backend no envía ganaderoNombre (anterior a MEJ-09), la tarjeta se ve sin él", async () => {
    server.use(
      porEstado([
        tramite(8, {
          estado: "PENDIENTE_REVISION",
          tipoTramite: "ALTA_NACIMIENTO",
          explotacionCodigoRega: "ES280790000999",
          explotacionNombre: "El Encinar",
          // ganaderoNombre ausente a propósito (undefined)
        }),
      ]),
    )
    montar()

    const tarjeta = await screen.findByRole("button", { name: "Revisar trámite #8" })
    expect(within(tarjeta).getByText(/ES280790000999/)).toBeInTheDocument()
    // Degrada: sin ganadero, nada se rompe y no aparece un nombre inventado.
    expect(within(tarjeta).queryByText("undefined")).not.toBeInTheDocument()
  })

  it("una columna vacía enseña su frase propia", async () => {
    server.use(porEstado([]))
    montar()

    const hecho = columna(/Hecho en OVZ/)
    expect(await within(hecho).findByText("Todavía no hay trámites hechos en OVZ.")).toBeInTheDocument()
  })

  it("si una columna falla, lo dice y «Reintentar» vuelve a pedir", async () => {
    let intentos = 0
    server.use(
      http.get(apiUrl("/tramites"), ({ request }) => {
        const estado = new URL(request.url).searchParams.get("estado") ?? ""
        // Solo RECHAZADO falla la primera vez.
        if (estado === "RECHAZADO") {
          intentos += 1
          if (intentos === 1) return new HttpResponse(null, { status: 500 })
          return HttpResponse.json({ content: [tramite(40, { estado: "RECHAZADO" })], totalElements: 1, totalPages: 1, number: 0, size: 50 })
        }
        return HttpResponse.json({ content: [], totalElements: 0, totalPages: 1, number: 0, size: 50 })
      }),
    )
    const user = userEvent.setup()
    montar()

    const rechazados = columna(/Rechazados y descartados/)
    expect(await within(rechazados).findByText("No se ha podido cargar")).toBeInTheDocument()
    await user.click(within(rechazados).getByRole("button", { name: "Reintentar" }))

    expect(await within(columna(/Rechazados y descartados/)).findByRole("button", { name: "Revisar trámite #40" })).toBeInTheDocument()
  })

  it("al entrar con abrirTramiteId (campana/alta), abre directamente ese trámite", async () => {
    server.use(
      porEstado([tramite(99, { estado: "PENDIENTE_REVISION" })]),
      http.get(apiUrl("/tramites/99"), () => HttpResponse.json(detalle(99))),
    )
    montar({ pathname: "/tramites", state: { abrirTramiteId: 99 } })

    expect(await screen.findByRole("dialog")).toBeInTheDocument()
  })

  it("«Nuevo trámite» abre el diálogo de alta", async () => {
    server.use(porEstado([]), http.get(apiUrl("/explotaciones"), () =>
      HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
    ))
    const user = userEvent.setup()
    montar()

    await screen.findByRole("heading", { level: 1, name: "Control" })
    await user.click(screen.getByRole("button", { name: "Nuevo trámite" }))
    expect(await screen.findByRole("dialog")).toBeInTheDocument()
  })

  it("si hay más de los cargados, lo avisa sin inventar", async () => {
    server.use(
      porEstado([tramite(1, { estado: "PENDIENTE_REVISION" })], { PENDIENTE_REVISION: 73 }),
    )
    montar()

    const porRevisar = columna(/Por revisar/)
    expect(await within(porRevisar).findByText("73")).toBeInTheDocument()
    expect(within(porRevisar).getByText(/Se muestran los 1 más recientes de 73\./)).toBeInTheDocument()
  })

  it("en «Por revisar», un mensaje sin trámite se avisa en la tarjeta (descartar es del modal)", async () => {
    server.use(
      porEstado([
        tramite(5, {
          estado: "PENDIENTE_REVISION",
          tipoTramite: null,
          estadoExtraccion: "SIN_TRAMITE",
          descartable: true,
        }),
      ]),
    )
    montar()

    const tarjeta = await screen.findByRole("button", { name: "Revisar trámite #5" })
    expect(within(tarjeta).getByText("Parece un mensaje sin trámite")).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Descartar" })).not.toBeInTheDocument()
  })
})
