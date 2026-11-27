import { render, screen } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { describe, expect, it } from "vitest"
import { SubnavCartera } from "./SubnavCartera"

function montar(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <SubnavCartera />
    </MemoryRouter>,
  )
}

describe("SubnavCartera (unificación de la cartera)", () => {
  it("tiene las dos vistas, enlazadas a sus rutas", () => {
    montar("/ganaderos")
    const subnav = screen.getByRole("navigation", { name: "Cartera" })
    const enlaces = screen.getAllByRole("link")
    expect(enlaces.map((e) => e.textContent)).toEqual(["Ganaderos", "Explotaciones"])
    expect(screen.getByRole("link", { name: "Ganaderos" })).toHaveAttribute("href", "/ganaderos")
    expect(screen.getByRole("link", { name: "Explotaciones" })).toHaveAttribute("href", "/explotaciones")
    expect(subnav).toBeInTheDocument()
  })

  it.each([
    ["/ganaderos", "Ganaderos"],
    ["/ganaderos/5", "Ganaderos"],
    ["/explotaciones", "Explotaciones"],
    ["/explotaciones/9", "Explotaciones"],
  ])("en %s la pestaña activa es %s", (url, activa) => {
    montar(url)
    for (const enlace of screen.getAllByRole("link")) {
      if (enlace.textContent === activa) expect(enlace).toHaveAttribute("aria-current", "page")
      else expect(enlace).not.toHaveAttribute("aria-current")
    }
  })
})
