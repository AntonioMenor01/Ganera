import { readFileSync } from "node:fs"
import path from "node:path"
import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import { LogoGanera } from "./LogoGanera"

/** index.css tal cual: jsdom no procesa Tailwind, así que la utilidad se comprueba en la fuente. */
const INDEX_CSS = readFileSync(path.join(import.meta.dirname, "../../index.css"), "utf-8")

function bloqueUtilidad(): string {
  const bloque = /@utility logo-ganera \{([^}]*)\}/.exec(INDEX_CSS)
  if (!bloque) throw new Error("No existe la utilidad logo-ganera en index.css")
  return bloque[1]
}

describe("LogoGanera", () => {
  it("por defecto es una imagen accesible llamada Ganera", () => {
    render(<LogoGanera />)
    const logo = screen.getByRole("img", { name: "Ganera" })
    expect(logo).toHaveAttribute("data-slot", "logo-ganera")
    expect(logo).not.toHaveAttribute("aria-hidden")
  })

  it("decorativo: se oculta a los lectores de pantalla y no expone nombre", () => {
    const { container } = render(<LogoGanera decorativo />)
    expect(screen.queryByRole("img")).not.toBeInTheDocument()
    const logo = container.querySelector('[data-slot="logo-ganera"]')
    expect(logo).toHaveAttribute("aria-hidden", "true")
    expect(logo).not.toHaveAttribute("aria-label")
  })

  it("aplica la máscara y deja el tamaño y el color de token a className", () => {
    render(<LogoGanera className="size-10 text-marca" />)
    const logo = screen.getByRole("img", { name: "Ganera" })
    expect(logo).toHaveClass("logo-ganera", "size-10", "text-marca")
    // tailwind-merge sustituye el tamaño por defecto en vez de acumular los dos.
    expect(logo).not.toHaveClass("size-6")
  })

  it("la utilidad logo-ganera pinta ganera-logo.svg como máscara sobre currentColor, sin hex", () => {
    const css = bloqueUtilidad()
    expect(css).toMatch(/background-color:\s*currentColor;/)
    for (const prefijo of ["", "-webkit-"]) {
      expect(css).toContain(`${prefijo}mask-image: url("/ganera-logo.svg");`)
      expect(css).toContain(`${prefijo}mask-size: contain;`)
      expect(css).toContain(`${prefijo}mask-repeat: no-repeat;`)
      expect(css).toContain(`${prefijo}mask-position: center;`)
    }
    expect(css).not.toMatch(/#[0-9a-f]{3,8}\b/i)
  })
})
