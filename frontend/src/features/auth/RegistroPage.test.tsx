import { render, screen } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { describe, expect, it } from "vitest"
import { RegistroPage } from "./RegistroPage"

describe("RegistroPage: estructura y enlace (Task 10)", () => {
  function renderizar() {
    render(
      <MemoryRouter>
        <RegistroPage />
      </MemoryRouter>,
    )
  }

  it("tiene la región main y el h1 «Ganera»", () => {
    renderizar()
    expect(screen.getByRole("main")).toBeInTheDocument()
    expect(screen.getByRole("heading", { level: 1, name: "Ganera" })).toBeInTheDocument()
  })

  it("el enlace «Inicia sesión» usa la receta común, con el anillo de foco", () => {
    renderizar()
    expect(screen.getByRole("link", { name: "Inicia sesión" })).toHaveClass("focus-visible:ring-3")
  })
})
