import { render, screen, within } from "@testing-library/react"
import { http, HttpResponse } from "msw"
import { MemoryRouter } from "react-router-dom"
import { beforeEach, describe, expect, it } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { ControlTramitesPage } from "./ControlTramitesPage"

function tramite(id: number, extra: Record<string, unknown> = {}) {
  return {
    id,
    explotacionId: 3,
    explotacionCodigoRega: "ES280790000123",
    explotacionNombre: "Finca La Dehesa",
    tipoTramite: "ALTA_NACIMIENTO",
    estado: "PENDIENTE_REVISION",
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

/** Datos por estado: total (para el contador) + filas de muestra. */
const POR_ESTADO: Record<string, { total: number; filas: ReturnType<typeof tramite>[] }> = {
  PENDIENTE_EXTRACCION: { total: 2, filas: [tramite(101, { estado: "PENDIENTE_EXTRACCION" }), tramite(102, { estado: "PENDIENTE_EXTRACCION" })] },
  PENDIENTE_REVISION: { total: 3, filas: [tramite(201), tramite(202), tramite(203)] },
  APROBADO: { total: 1, filas: [tramite(301, { estado: "APROBADO" })] },
  EN_PROCESO: { total: 1, filas: [tramite(401, { estado: "EN_PROCESO" })] },
  EJECUTADO_OVZ: { total: 1, filas: [tramite(402, { estado: "EJECUTADO_OVZ" })] },
  HECHO_EN_OVZ: { total: 0, filas: [] },
  ERROR_OVZ: { total: 0, filas: [] },
  RECHAZADO: { total: 1, filas: [tramite(601, { estado: "RECHAZADO", motivoRechazo: "SIN_TRAMITE" })] },
}

function handlerPorEstado(overrides: Record<string, number> = {}) {
  // overrides mapea estado -> status HTTP para forzar errores por estado.
  return http.get(apiUrl("/tramites"), ({ request }) => {
    const estado = new URL(request.url).searchParams.get("estado") ?? ""
    if (overrides[estado]) return new HttpResponse(null, { status: overrides[estado] })
    const datos = POR_ESTADO[estado] ?? { total: 0, filas: [] }
    return HttpResponse.json({
      content: datos.filas,
      totalElements: datos.total,
      totalPages: datos.total === 0 ? 0 : 1,
      number: 0,
      size: 5,
    })
  })
}

function montar() {
  return render(
    <MemoryRouter>
      <ControlTramitesPage />
    </MemoryRouter>,
  )
}

function seccion(nombre: RegExp): HTMLElement {
  const heading = screen.getByRole("heading", { name: nombre })
  const sec = heading.closest("section")
  if (!sec) throw new Error("sección no encontrada para " + nombre)
  return sec as HTMLElement
}

describe("ControlTramitesPage", () => {
  beforeEach(() => {
    server.use(handlerPorEstado())
  })

  it("muestra los seis grupos con sus estados reales", async () => {
    montar()
    expect(await screen.findByRole("heading", { name: /Entrando/ })).toBeInTheDocument()
    expect(screen.getByRole("heading", { name: /Por revisar/ })).toBeInTheDocument()
    expect(screen.getByRole("heading", { name: /solo falta OVZ/ })).toBeInTheDocument()
    expect(screen.getByRole("heading", { name: /Presentándose/ })).toBeInTheDocument()
    expect(screen.getByRole("heading", { name: /Hecho/ })).toBeInTheDocument()
    expect(screen.getByRole("heading", { name: /Con incidencia/ })).toBeInTheDocument()
  })

  it("cuenta por grupo con el totalElements real y suma el total general", async () => {
    montar()
    // "Por revisar" = PENDIENTE_REVISION = 3
    const porRevisar = seccion(/Por revisar/)
    expect(await within(porRevisar).findByText("3")).toBeInTheDocument()
    // "Presentándose / presentado" = EN_PROCESO (1) + EJECUTADO_OVZ (1) = 2
    expect(within(seccion(/Presentándose/)).getByText("2")).toBeInTheDocument()
    // Total general = 2+3+1+1+1+0+0+1 = 9
    expect(await screen.findByText("9 trámites en total")).toBeInTheDocument()
  })

  it("un grupo sin trámites muestra un vacío honesto, no un placeholder", async () => {
    montar()
    const hecho = seccion(/^Hecho/)
    expect(await within(hecho).findByText("No hay trámites en este grupo.")).toBeInTheDocument()
  })

  it("respeta el estado 'Descartado' (RECHAZADO + SIN_TRAMITE) en el badge", async () => {
    montar()
    const incidencias = seccion(/Con incidencia/)
    expect(await within(incidencias).findByText("Descartado")).toBeInTheDocument()
    // Y ofrece abrir la revisión existente de ese trámite.
    expect(within(incidencias).getByRole("button", { name: "Revisar trámite #601" })).toBeInTheDocument()
  })

  it("cada fila ofrece abrir la revisión existente (#id)", async () => {
    montar()
    const porRevisar = seccion(/Por revisar/)
    expect(await within(porRevisar).findByRole("button", { name: "Revisar trámite #201" })).toBeInTheDocument()
  })

  it("si falla la carga de un estado, su grupo ofrece reintentar", async () => {
    server.use(handlerPorEstado({ PENDIENTE_REVISION: 500 }))
    montar()
    const porRevisar = seccion(/Por revisar/)
    expect(await within(porRevisar).findByRole("button", { name: "Reintentar" })).toBeInTheDocument()
  })
})
