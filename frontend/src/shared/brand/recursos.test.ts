import { existsSync, readFileSync } from "node:fs"
import path from "node:path"
import { describe, expect, it } from "vitest"

/** Recursos estáticos del logo en frontend/public/ (contextos sin CSS: el color va fijo en el SVG). */
const publico = path.join(import.meta.dirname, "../../../public")

describe("recursos del logo en public/", () => {
  it("incluye ganera-logo-rojo.svg con el Rojo Ganera fijo", () => {
    const svg = readFileSync(path.join(publico, "ganera-logo-rojo.svg"), "utf-8")
    expect(svg).toContain('fill="#EC3013"')
    expect(svg).not.toContain("#1F3D2B")
  })

  it("ya no incluye la variante verde", () => {
    expect(existsSync(path.join(publico, "ganera-logo-verde.svg"))).toBe(false)
  })
})
