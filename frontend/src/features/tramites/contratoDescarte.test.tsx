import { render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { MemoryRouter } from "react-router-dom"
import { describe, expect, it, vi } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import contrato from "@/test/contrato/descarte-sin-tramite.json"
import { descartarTramite, deshacerDescarte } from "./api"
import { TramiteReviewDialog } from "./TramiteReviewDialog"
import { TramitesPage } from "./TramitesPage"

/**
 * Contrato SIN_TRAMITE con el backend (2026-10-12). `descarte-sin-tramite.json` son respuestas REALES del
 * backend: `TramiteDescarteEndToEndTest.lasRespuestasRealesCumplenElContratoQueUsaElFrontend` comprueba que el
 * backend sigue devolviendo exactamente esos campos y que las dos rutas existen. Aquí se comprueba el otro lado:
 * que el frontend llama a esas rutas con ese cuerpo y que con esas respuestas, tal cual, la interfaz funciona.
 */

const ID = contrato.itemLista.id

function ruta(plantilla: string): string {
  return apiUrl(plantilla.replace("{id}", String(ID)))
}

describe("contrato SIN_TRAMITE con el backend", () => {
  it("las dos llamadas usan las rutas del contrato y su cuerpo {version}", async () => {
    const cuerpos: Array<[string, unknown]> = []
    server.use(
      http.post(ruta(contrato.rutas.descartar), async ({ request }) => {
        cuerpos.push(["descartar", await request.json()])
        return HttpResponse.json(contrato.detalleDescartado)
      }),
      http.post(ruta(contrato.rutas.deshacerDescarte), async ({ request }) => {
        cuerpos.push(["deshacer", await request.json()])
        return HttpResponse.json(contrato.detalleDeshecho)
      }),
    )

    const descartado = await descartarTramite(ID, contrato.cuerpo.version)
    const deshecho = await deshacerDescarte(ID, descartado.version)

    expect(cuerpos).toEqual([
      ["descartar", { version: contrato.cuerpo.version }],
      ["deshacer", { version: contrato.detalleDescartado.version }],
    ])
    expect(Object.keys(contrato.cuerpo)).toEqual(["version"])
    expect(descartado.motivoRechazo).toBe("SIN_TRAMITE")
    expect(deshecho.historialDescarte.map((e) => e.tipo)).toEqual(["DESCARTADO", "DESCARTE_DESHECHO"])
  })

  it("el tablero, con la tarjeta real de la lista, enseña el aviso y ningún botón de descartar", async () => {
    // El tablero pide una página por estado; esta fila solo sale en el de su estado (PENDIENTE_REVISION).
    server.use(
      http.get(apiUrl("/tramites"), ({ request }) => {
        const estado = new URL(request.url).searchParams.get("estado")
        const content = estado === contrato.itemLista.estado ? [contrato.itemLista] : []
        return HttpResponse.json({ content, totalElements: content.length, totalPages: 1, number: 0, size: 50 })
      }),
    )
    render(
      <MemoryRouter>
        <TramitesPage />
      </MemoryRouter>,
    )

    // La tarjeta es un botón «Revisar trámite #N» que incluye el aviso; descartar es solo del modal.
    const aviso = await screen.findByText("Parece un mensaje sin trámite")
    const tarjeta = aviso.closest("button") as HTMLElement
    expect(tarjeta).not.toBeNull()
    expect(tarjeta).toHaveAttribute("aria-label", `Revisar trámite #${ID}`)
    expect(screen.queryByRole("button", { name: "Descartar" })).not.toBeInTheDocument()
  })

  it("el modal, con los detalles reales: Descartar → banda e historial → Deshacer → vuelve a pendiente", async () => {
    let actual: object = contrato.detallePendiente
    server.use(
      http.get(ruta("/tramites/{id}"), () => HttpResponse.json(actual)),
      http.post(ruta(contrato.rutas.descartar), () => {
        actual = contrato.detalleDescartado
        return HttpResponse.json(contrato.detalleDescartado)
      }),
      http.post(ruta(contrato.rutas.deshacerDescarte), () => {
        actual = contrato.detalleDeshecho
        return HttpResponse.json(contrato.detalleDeshecho)
      }),
    )
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <TramiteReviewDialog tramiteId={ID} onClose={vi.fn()} onCambiado={vi.fn()} />
      </MemoryRouter>,
    )
    const dialogo = await screen.findByRole("dialog")
    await within(dialogo).findByRole("heading", { name: "Mensaje de WhatsApp" })
    expect(within(dialogo).getByText(/^Parece un mensaje sin trámite/)).toBeInTheDocument()

    await user.click(within(dialogo).getByRole("button", { name: "Descartar" }))

    // 2031-03-10T09:00:00Z es 10:00 en Madrid (invierno, UTC+1).
    expect(
      await within(dialogo).findByText("Descartado como mensaje sin trámite por Ana Pérez el 10/03/2031, 10:00."),
    ).toBeInTheDocument()
    expect(within(dialogo).getAllByText("Descartado").length).toBeGreaterThan(0)
    expect(within(dialogo).getByText(contrato.detalleDescartado.whatsapp.motivoNoDisponible)).toBeInTheDocument()
    expect(within(dialogo).queryByRole("button", { name: /^Responder/ })).not.toBeInTheDocument()

    await user.click(within(dialogo).getByRole("button", { name: "Deshacer" }))

    expect(
      await within(dialogo).findByText("Descarte deshecho por Ana Pérez el 10/03/2031, 10:00"),
    ).toBeInTheDocument()
    expect(within(dialogo).getByText("Descartado por Ana Pérez el 10/03/2031, 10:00")).toBeInTheDocument()
    expect(within(dialogo).getByRole("button", { name: "Descartar" })).toBeEnabled()
  })
})
