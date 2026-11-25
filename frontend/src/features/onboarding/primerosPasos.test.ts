import { describe, expect, it, vi } from "vitest"
import {
  abrirPrimerosPasos,
  guiaYaVista,
  marcarGuiaVista,
  PASOS_GUIA,
  suscribirAperturaPrimerosPasos,
} from "./primerosPasos"

describe("PASOS_GUIA", () => {
  it("tiene entre 7 y 8 pasos, con ids únicos", () => {
    expect(PASOS_GUIA.length).toBeGreaterThanOrEqual(7)
    expect(PASOS_GUIA.length).toBeLessThanOrEqual(8)
    const ids = PASOS_GUIA.map((p) => p.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it("cada paso tiene título y cuerpo no vacíos, y toda CTA apunta a una ruta interna", () => {
    for (const paso of PASOS_GUIA) {
      expect(paso.titulo.trim()).not.toBe("")
      expect(paso.cuerpo.trim()).not.toBe("")
      if (paso.cta) {
        expect(paso.cta.label.trim()).not.toBe("")
        expect(paso.cta.to.startsWith("/")).toBe(true)
      }
    }
  })
})

describe("marca de «ya vista» por usuario", () => {
  it("por defecto no está vista; marcarla la deja vista solo para ese usuario", () => {
    expect(guiaYaVista(1)).toBe(false)
    marcarGuiaVista(1)
    expect(guiaYaVista(1)).toBe(true)
    // Otro usuario no se ve afectado.
    expect(guiaYaVista(2)).toBe(false)
  })
})

describe("reapertura manual (pub/sub)", () => {
  it("avisa a los suscritos al pedir apertura, y deja de hacerlo tras desuscribir", () => {
    const oyente = vi.fn()
    const desuscribir = suscribirAperturaPrimerosPasos(oyente)
    abrirPrimerosPasos()
    expect(oyente).toHaveBeenCalledTimes(1)
    desuscribir()
    abrirPrimerosPasos()
    expect(oyente).toHaveBeenCalledTimes(1)
  })
})
