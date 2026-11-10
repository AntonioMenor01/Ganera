import { render, screen } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { describe, expect, it } from "vitest"
import { ProximamentePage } from "./ProximamentePage"

describe("ProximamentePage", () => {
  it("muestra el título, la descripción y enlaces al trabajo real", () => {
    render(
      <MemoryRouter>
        <ProximamentePage
          titulo="Bandeja"
          descripcion="Los mensajes de WhatsApp llegarán aquí."
        />
      </MemoryRouter>,
    )

    expect(screen.getByRole("heading", { level: 1, name: "Bandeja" })).toBeInTheDocument()
    expect(screen.getByText(/mensajes de WhatsApp/i)).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Inicio" })).toHaveAttribute("href", "/")
    expect(screen.getByRole("link", { name: "Trámites" })).toHaveAttribute("href", "/tramites")
    expect(screen.getByRole("link", { name: "Ganaderos" })).toHaveAttribute("href", "/ganaderos")
  })
})
