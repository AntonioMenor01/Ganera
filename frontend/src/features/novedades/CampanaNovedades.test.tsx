import { render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom"
import { describe, expect, it } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { CampanaNovedades } from "./CampanaNovedades"

function alerta(id: string, extra: Record<string, unknown> = {}) {
  return {
    id,
    tipo: "BAJA_PLAZO_7D",
    titulo: "Plazo de baja por muerte",
    detalle: "Vence el 2026-12-01.",
    venceEl: "2026-12-01",
    tramiteId: 7,
    severidad: "warning",
    ...extra,
  }
}

function montar() {
  // La página de trámites se sustituye por un stub que muestra qué se le pidió abrir (router state).
  function TramitesStub() {
    const estado = useLocation().state as { abrirTramiteId?: number } | null
    return <h1>TRAMITES {estado?.abrirTramiteId ?? ""}</h1>
  }
  return render(
    <MemoryRouter>
      <CampanaNovedades />
      <Routes>
        <Route path="/" element={<p>inicio</p>} />
        <Route path="/tramites" element={<TramitesStub />} />
        <Route path="/calendario" element={<h1>CALENDARIO</h1>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe("CampanaNovedades", () => {
  it("sin alertas: muestra la campana sin badge y, al abrir, el estado 'al día'", async () => {
    server.use(http.get(apiUrl("/alertas"), () => HttpResponse.json([])))
    montar()
    const campana = await screen.findByRole("button", { name: "Novedades" })
    await userEvent.click(campana)
    expect(await screen.findByText(/Estás al día/)).toBeInTheDocument()
  })

  it("con alertas: el badge lleva el número y el panel las lista", async () => {
    server.use(
      http.get(apiUrl("/alertas"), () =>
        HttpResponse.json([alerta("a1"), alerta("a2", { titulo: "Trámite sin revisar", tramiteId: null })]),
      ),
    )
    montar()
    // El nombre accesible del botón incluye el total.
    const campana = await screen.findByRole("button", { name: "Novedades (2)" })
    await userEvent.click(campana)
    const panel = await screen.findByRole("region", { name: "Novedades" })
    expect(within(panel).getByText("Plazo de baja por muerte")).toBeInTheDocument()
    expect(within(panel).getByText("Trámite sin revisar")).toBeInTheDocument()
  })

  it("una novedad con trámite abre su revisión (router state); sin trámite va al calendario", async () => {
    server.use(
      http.get(apiUrl("/alertas"), () =>
        HttpResponse.json([alerta("con", { tramiteId: 7 }), alerta("sin", { titulo: "General", tramiteId: null })]),
      ),
    )
    montar()
    await userEvent.click(await screen.findByRole("button", { name: /Novedades/ }))
    await userEvent.click(await screen.findByText("Plazo de baja por muerte"))
    expect(await screen.findByRole("heading", { name: /TRAMITES 7/ })).toBeInTheDocument()
  })

  it("si /alertas falla, el panel ofrece reintentar", async () => {
    server.use(http.get(apiUrl("/alertas"), () => new HttpResponse(null, { status: 500 })))
    montar()
    await userEvent.click(await screen.findByRole("button", { name: "Novedades" }))
    expect(await screen.findByRole("button", { name: "Reintentar" })).toBeInTheDocument()
  })

  it("se cierra con Escape", async () => {
    server.use(http.get(apiUrl("/alertas"), () => HttpResponse.json([alerta("a1")])))
    montar()
    await userEvent.click(await screen.findByRole("button", { name: /Novedades/ }))
    expect(await screen.findByRole("region", { name: "Novedades" })).toBeInTheDocument()
    await userEvent.keyboard("{Escape}")
    await waitFor(() => expect(screen.queryByRole("region", { name: "Novedades" })).not.toBeInTheDocument())
  })
})
