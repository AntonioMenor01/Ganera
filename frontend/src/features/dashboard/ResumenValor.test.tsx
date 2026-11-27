import { render, screen, within } from "@testing-library/react"
import { http, HttpResponse } from "msw"
import { describe, expect, it } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { ResumenValor } from "./ResumenValor"

/** /tramites?estado=HECHO_EN_OVZ → total; /tramites (sin estado) → total; /ganaderos → total. */
function mock({ hechos, total, ganaderos }: { hechos: number; total: number; ganaderos: number }) {
  server.use(
    http.get(apiUrl("/tramites"), ({ request }) => {
      const estado = new URL(request.url).searchParams.get("estado")
      const n = estado === "HECHO_EN_OVZ" ? hechos : total
      return HttpResponse.json({ content: [], totalElements: n, totalPages: 1, number: 0, size: 1 })
    }),
    http.get(apiUrl("/ganaderos"), () =>
      HttpResponse.json({ content: [], totalElements: ganaderos, totalPages: 1, number: 0, size: 1 }),
    ),
  )
}

describe("ResumenValor", () => {
  it("muestra datos reales: hechos en OVZ, trámites gestionados y ganaderos", async () => {
    mock({ hechos: 12, total: 40, ganaderos: 7 })
    render(<ResumenValor />)
    const hechos = await screen.findByText("Hechos en OVZ")
    expect(within(hechos.closest("div")!).getByText("12")).toBeInTheDocument()
    const gestionados = screen.getByText("Trámites gestionados")
    expect(within(gestionados.closest("div")!).getByText("40")).toBeInTheDocument()
    const ganaderos = screen.getByText("Ganaderos")
    expect(within(ganaderos.closest("div")!).getByText("7")).toBeInTheDocument()
  })

  it("un conteo que falla se muestra como «—», sin romper el resto", async () => {
    server.use(
      http.get(apiUrl("/tramites"), ({ request }) => {
        const estado = new URL(request.url).searchParams.get("estado")
        if (estado === "HECHO_EN_OVZ") return new HttpResponse(null, { status: 500 })
        return HttpResponse.json({ content: [], totalElements: 40, totalPages: 1, number: 0, size: 1 })
      }),
      http.get(apiUrl("/ganaderos"), () =>
        HttpResponse.json({ content: [], totalElements: 7, totalPages: 1, number: 0, size: 1 }),
      ),
    )
    render(<ResumenValor />)
    expect(await screen.findByText("—")).toBeInTheDocument()
    const gestionados = screen.getByText("Trámites gestionados")
    expect(within(gestionados.closest("div")!).getByText("40")).toBeInTheDocument()
  })
})
