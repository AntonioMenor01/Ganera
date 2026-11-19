import { render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import * as api from "./api"
import { GanaderoDetallePage } from "./GanaderoDetallePage"

function ganadero(extra: Record<string, unknown> = {}) {
  return {
    id: 7,
    nombre: "Ana Ruiz Gómez",
    nif: "12345678Z",
    telefono: null,
    tieneCredencialesOvz: true,
    usaCertificadoOvz: false,
    explotaciones: [],
    ...extra,
  }
}

function doc(extra: Record<string, unknown> = {}) {
  return {
    id: 5,
    tramiteId: 11,
    tipo: "BAJA_ANIMAL",
    nombre: "Baja Animal.pdf",
    tamano: 46080,
    contentType: "application/pdf",
    sha256: "abc",
    creadoEn: "2026-10-07T10:30:00Z",
    ...extra,
  }
}

function montar() {
  const router = createMemoryRouter(
    [
      { path: "/ganaderos", element: <p>Listado de ganaderos</p> },
      { path: "/ganaderos/:id", element: <GanaderoDetallePage /> },
    ],
    { initialEntries: ["/ganaderos/7"] },
  )
  render(<RouterProvider router={router} />)
  return router
}

async function seccionDocumentos() {
  return await screen.findByRole("region", { name: "Documentos de OVZ" })
}

beforeEach(() => {
  server.use(http.get(apiUrl("/ganaderos/7"), () => HttpResponse.json(ganadero())))
})

describe("GanaderoDetallePage: Documentos de OVZ", () => {
  it("lista los justificantes con tipo, fecha (Madrid) y tamaño", async () => {
    server.use(http.get(apiUrl("/ganaderos/7/documentos"), () => HttpResponse.json([doc()])))
    montar()

    const seccion = await seccionDocumentos()
    expect(within(seccion).getByText("Baja de animal")).toBeInTheDocument()
    // 10:30 UTC = 12:30 en Madrid; 46080 B ≈ 45 KB.
    expect(within(seccion).getByText(/07\/10\/2026, 12:30 · 45 KB/)).toBeInTheDocument()
    expect(within(seccion).getByRole("button", { name: "Descargar" })).toBeInTheDocument()
  })

  it("sin documentos, la sección no aparece", async () => {
    server.use(http.get(apiUrl("/ganaderos/7/documentos"), () => HttpResponse.json([])))
    montar()
    // La sección de credenciales sí está (confirma que la ficha cargó) y la de documentos no.
    await screen.findByRole("region", { name: "Acceso a OVZ.net" })
    expect(screen.queryByRole("region", { name: "Documentos de OVZ" })).not.toBeInTheDocument()
  })

  it("descargar pide el contenido y abre el fichero (blob)", async () => {
    // jsdom no trae createObjectURL/revokeObjectURL: se parchean SOLO esos métodos (no todo `URL`, que
    // axios usa con `new URL`) y se restauran al terminar. La lectura del blob por axios no es fiable en
    // jsdom (`object.stream`), así que se simula la descarga con un Blob directo; el 404/errores van en
    // sus propios tests, y que la petición use el JWT lo garantiza httpClient (probado aparte).
    const crear = vi.fn(() => "blob:mock")
    const revocar = vi.fn()
    const origCrear = URL.createObjectURL
    const origRevocar = URL.revokeObjectURL
    URL.createObjectURL = crear as unknown as typeof URL.createObjectURL
    URL.revokeObjectURL = revocar as unknown as typeof URL.revokeObjectURL
    const clics = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})
    const descarga = vi
      .spyOn(api, "descargarDocumentoOvz")
      .mockResolvedValue(new Blob(["%PDF-1.4"], { type: "application/pdf" }))

    server.use(http.get(apiUrl("/ganaderos/7/documentos"), () => HttpResponse.json([doc()])))
    const user = userEvent.setup()
    montar()
    const seccion = await seccionDocumentos()
    await user.click(within(seccion).getByRole("button", { name: "Descargar" }))

    await waitFor(() => expect(descarga).toHaveBeenCalledWith(5))
    await waitFor(() => expect(crear).toHaveBeenCalledTimes(1))
    expect(clics).toHaveBeenCalledTimes(1)
    expect(revocar).toHaveBeenCalledTimes(1)

    clics.mockRestore()
    descarga.mockRestore()
    URL.createObjectURL = origCrear
    URL.revokeObjectURL = origRevocar
  })

  it("si falla la carga, muestra el error con «Reintentar» y luego carga bien", async () => {
    let intento = 0
    server.use(
      http.get(apiUrl("/ganaderos/7/documentos"), () => {
        intento += 1
        return intento === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([doc()])
      }),
    )
    const user = userEvent.setup()
    montar()

    const seccion = await seccionDocumentos()
    expect(within(seccion).getByText("No se han podido cargar los documentos de OVZ.")).toBeInTheDocument()
    await user.click(within(seccion).getByRole("button", { name: "Reintentar" }))

    await waitFor(() =>
      expect(within(screen.getByRole("region", { name: "Documentos de OVZ" })).getByText("Baja de animal")).toBeInTheDocument(),
    )
  })

  it("un ganadero ajeno (404 en documentos) cae en el error de la sección, sin romper la ficha", async () => {
    server.use(http.get(apiUrl("/ganaderos/7/documentos"), () => new HttpResponse(null, { status: 404 })))
    montar()
    const seccion = await seccionDocumentos()
    expect(within(seccion).getByText("No se han podido cargar los documentos de OVZ.")).toBeInTheDocument()
  })
})

afterEach(() => {
  vi.restoreAllMocks()
})
