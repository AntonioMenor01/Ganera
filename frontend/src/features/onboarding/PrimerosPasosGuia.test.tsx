import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it, vi } from "vitest"
import { PrimerosPasosGuia } from "./PrimerosPasosGuia"
import { PASOS_GUIA } from "./primerosPasos"

const TOTAL = PASOS_GUIA.length
const PRIMER_TITULO = PASOS_GUIA[0].titulo
const SEGUNDO_TITULO = PASOS_GUIA[1].titulo
const ULTIMO_TITULO = PASOS_GUIA[TOTAL - 1].titulo

function montar(props: Partial<Parameters<typeof PrimerosPasosGuia>[0]> = {}) {
  const onClose = vi.fn()
  const onIr = vi.fn()
  render(<PrimerosPasosGuia open onClose={onClose} onIr={onIr} {...props} />)
  return { onClose, onIr }
}

describe("PrimerosPasosGuia", () => {
  it("cerrada no renderiza nada", () => {
    render(<PrimerosPasosGuia open={false} onClose={vi.fn()} onIr={vi.fn()} />)
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
  })

  it("abierta muestra el primer paso y el contador «Paso 1 de N»", () => {
    montar()
    expect(screen.getByRole("heading", { name: PRIMER_TITULO })).toBeInTheDocument()
    expect(screen.getByText(`Paso 1 de ${TOTAL}`)).toBeInTheDocument()
    // En el primer paso no hay botón «Atrás».
    expect(screen.queryByRole("button", { name: "Atrás" })).not.toBeInTheDocument()
  })

  it("«Siguiente» avanza y «Atrás» retrocede", async () => {
    montar()
    await userEvent.click(screen.getByRole("button", { name: /Siguiente/ }))
    expect(screen.getByRole("heading", { name: SEGUNDO_TITULO })).toBeInTheDocument()
    expect(screen.getByText(`Paso 2 de ${TOTAL}`)).toBeInTheDocument()
    await userEvent.click(screen.getByRole("button", { name: "Atrás" }))
    expect(screen.getByRole("heading", { name: PRIMER_TITULO })).toBeInTheDocument()
  })

  it("«Omitir por ahora» cierra", async () => {
    const { onClose } = montar()
    await userEvent.click(screen.getByRole("button", { name: "Omitir por ahora" }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it("en el último paso el botón es «Empezar» y cierra; ya no hay «Omitir»", async () => {
    const { onClose } = montar()
    // Avanzar hasta el final.
    for (let i = 0; i < TOTAL - 1; i++) {
      await userEvent.click(screen.getByRole("button", { name: /Siguiente|Empezar/ }))
    }
    expect(screen.getByRole("heading", { name: ULTIMO_TITULO })).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Omitir por ahora" })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole("button", { name: "Empezar" }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it("una CTA de paso llama a onIr con su ruta", async () => {
    // El segundo paso (OVZ) tiene CTA a /configuracion.
    const cta = PASOS_GUIA[1].cta!
    const { onIr } = montar()
    await userEvent.click(screen.getByRole("button", { name: /Siguiente/ }))
    await userEvent.click(screen.getByRole("button", { name: new RegExp(cta.label) }))
    expect(onIr).toHaveBeenCalledWith(cta.to)
  })

  it("Escape cierra", async () => {
    const { onClose } = montar()
    await userEvent.keyboard("{Escape}")
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
