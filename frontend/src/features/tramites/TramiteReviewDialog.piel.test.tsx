import { render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { MemoryRouter } from "react-router-dom"
import { describe, expect, it, vi } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import type { EventoDescarte, TramiteCrotal, WhatsappTramite } from "./types"
import { TramiteReviewDialog } from "./TramiteReviewDialog"

/**
 * Piel del modal (diseño de Álvaro, fase 1, T5): solo clases. Comprueba lo que la piel no puede
 * romper: que solo «Aprobar» sea rojo sólido, que «Rechazar» y «Sí, rechazar» sean de peligro (blanco
 * con borde y texto Rojo 800), que el título del mensaje vaya en mayúsculas solo por CSS (el texto del
 * DOM no cambia) y que los enfocables del cuerpo con scroll no queden pegados al pie.
 */

const DETALLE = {
  id: 7,
  tipoTramite: "ALTA_NACIMIENTO",
  estado: "PENDIENTE_REVISION",
  motivoError: null,
  explotacionId: 3,
  explotacionCodigoRega: "ES123",
  explotacionNombre: "La Dehesa",
  mensajeOriginal: "alta del 1234" as string | null,
  crotales: [] as TramiteCrotal[],
  version: 0,
  origen: null as string | null,
  estadoExtraccion: null as string | null,
  crotalesDescartados: 0,
  hechoEnOvzEn: null as string | null,
  hechoEnOvzPorNombre: null as string | null,
  whatsapp: null as WhatsappTramite | null,
  motivoRechazo: null as string | null,
  descartable: false,
  historialDescarte: [] as EventoDescarte[],
}

function renderDialog() {
  render(
    <MemoryRouter>
      <TramiteReviewDialog tramiteId={7} onClose={vi.fn()} onCambiado={vi.fn()} />
    </MemoryRouter>,
  )
}

async function dialogoListo() {
  const dialogo = await screen.findByRole("dialog")
  await within(dialogo).findByRole("heading", { name: "Mensaje de WhatsApp" })
  return dialogo
}

/** Clases de la variante `destructive` de Button: blanco, borde y texto Rojo 800. */
const CLASES_PELIGRO = ["border-destructive", "bg-card", "text-destructive"]

describe("TramiteReviewDialog: piel (fase 1)", () => {
  it("«Rechazar» y «Sí, rechazar» son de peligro, nunca rojo sólido; solo «Aprobar» lleva bg-primary", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()

    const rechazar = within(dialogo).getByRole("button", { name: "Rechazar" })
    expect(rechazar).toHaveClass(...CLASES_PELIGRO)
    expect(rechazar).not.toHaveClass("bg-primary")
    const conPrimario = within(dialogo)
      .getAllByRole("button")
      .filter((b) => b.classList.contains("bg-primary"))
      .map((b) => b.textContent?.trim())
    expect(conPrimario).toEqual(["Aprobar"])

    await user.click(rechazar)
    const confirmar = within(dialogo).getByRole("button", { name: "Sí, rechazar" })
    expect(confirmar).toHaveClass(...CLASES_PELIGRO)
    expect(confirmar).not.toHaveClass("bg-primary")
  })

  it("«Guardar» es el secundario blanco con borde Gris 600 y «Descartar» se queda neutro", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json({ ...DETALLE, descartable: true })))
    renderDialog()
    const dialogo = await dialogoListo()
    expect(within(dialogo).getByRole("button", { name: "Guardar" })).toHaveClass("border-input", "bg-card")
    const descartar = within(dialogo).getByRole("button", { name: "Descartar" })
    expect(descartar).toHaveClass("border-input")
    expect(descartar).not.toHaveClass("text-destructive")
  })

  it("el título del mensaje conserva su texto en el DOM y va en mayúsculas solo por CSS", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    renderDialog()
    const dialogo = await dialogoListo()
    const titulo = within(dialogo).getByRole("heading", { name: "Mensaje de WhatsApp" })
    expect(titulo.textContent).toBe("Mensaje de WhatsApp")
    expect(titulo).toHaveClass("uppercase", "text-muted-foreground")
    const cita = dialogo.querySelector("blockquote")!
    expect(cita).toHaveClass("bg-background", "border", "border-border")
  })

  it("el cuerpo con scroll aplica scroll-mb-3 a sus enfocables (no quedan pegados al pie)", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    renderDialog()
    const dialogo = await dialogoListo()
    const cuerpo = dialogo.querySelector("[data-cuerpo-revision]")!
    expect(cuerpo.className).toMatch(/\]:scroll-mb-3(\s|$)/)
    // Además, scroll-padding en el propio contenedor: sin él, un enfocable que ya cabe justo encima del
    // pie no provoca scroll y se queda pegado (comprobado en Chromium y Firefox a 1440 × 900).
    expect(cuerpo).toHaveClass("scroll-pb-3")
  })

  it("los pies son blancos con raya superior de 2 px (sin gris translúcido)", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    renderDialog()
    const dialogo = await dialogoListo()
    const pie = within(dialogo).getByRole("button", { name: "Aprobar" }).closest(".border-t-2")
    expect(pie).not.toBeNull()
    expect(pie).toHaveClass("bg-card", "border-divisor")
    expect(pie!.className).not.toMatch(/bg-muted/)
  })

  it.each([
    ["APROBADO", "Marcar como hecho en OVZ", {}],
    ["HECHO_EN_OVZ", "Quitar la marca", { hechoEnOvzEn: "2026-10-07T10:30:00Z", hechoEnOvzPorNombre: "Ana Pérez" }],
  ])("el pie de %s también es blanco con raya superior de 2 px", async (estado, nombreBoton, extra) => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json({ ...DETALLE, estado, ...extra })))
    renderDialog()
    const dialogo = await dialogoListo()
    const pie = within(dialogo).getByRole("button", { name: nombreBoton }).closest(".border-t-2")
    expect(pie).not.toBeNull()
    expect(pie).toHaveClass("bg-card", "border-divisor")
    expect(pie!.className).not.toMatch(/bg-muted/)
  })

  it("la banda «Hecho en OVZ» es recta y lleva el par de éxito", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () =>
        HttpResponse.json({ ...DETALLE, estado: "HECHO_EN_OVZ", hechoEnOvzEn: "2026-10-07T10:30:00Z", hechoEnOvzPorNombre: "Ana Pérez" }),
      ),
    )
    renderDialog()
    const dialogo = await dialogoListo()
    const franja = dialogo.querySelector("[data-franja-hecho-en-ovz]")!
    expect(franja).toHaveClass("bg-success", "text-success-foreground")
    expect(franja.className).not.toMatch(/rounded/)
  })

  it("la banda del descartado es recta y lleva el par neutro (no bg-muted)", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () =>
        HttpResponse.json({
          ...DETALLE,
          estado: "RECHAZADO",
          motivoRechazo: "SIN_TRAMITE",
          tipoTramite: null,
          historialDescarte: [{ tipo: "DESCARTADO", usuarioNombre: "Ana Pérez", en: "2026-10-12T09:00:00Z" }],
        }),
      ),
    )
    renderDialog()
    const dialogo = await dialogoListo()
    const banda = dialogo.querySelector("[data-banda-descartado]")!
    expect(banda).toHaveClass("bg-neutral", "text-neutral-foreground")
    expect(banda.className).not.toMatch(/bg-muted|rounded/)
  })

  it("con el panel de WhatsApp abierto, «Enviar por WhatsApp» es secundario: el único rojo sólido sigue siendo «Aprobar»", async () => {
    const whatsapp: WhatsappTramite = {
      destinatarioNombre: "Juan Ganadero",
      telefonoEnmascarado: "+34******001",
      contactoActivo: true,
      ventanaAbiertaHasta: new Date(Date.now() + 5 * 3_600_000).toISOString(),
      firmaGestoria: "Gestoría Norte",
      motivoNoDisponible: null,
      respuestas: [],
    }
    server.use(
      http.get(apiUrl("/tramites/7"), () =>
        HttpResponse.json({ ...DETALLE, tipoTramite: "BAJA_MUERTE", origen: "WHATSAPP", estadoExtraccion: "COMPLETADA", whatsapp }),
      ),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(within(dialogo).getByRole("button", { name: "Responder por WhatsApp" }))
    await user.keyboard("la fecha de la muerte")

    const enviar = within(dialogo).getByRole("button", { name: "Enviar por WhatsApp" })
    expect(enviar).toHaveClass("border-input", "bg-card")
    expect(enviar).not.toHaveClass("bg-primary")
    const conPrimario = within(dialogo)
      .getAllByRole("button")
      .filter((b) => b.classList.contains("bg-primary"))
      .map((b) => b.textContent?.trim())
    expect(conPrimario).toEqual(["Aprobar"])
  })
})
