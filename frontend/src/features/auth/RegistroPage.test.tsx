import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { MemoryRouter } from "react-router-dom"
import { describe, expect, it } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { RegistroPage } from "./RegistroPage"

describe("RegistroPage: error", () => {
  it("un 400 enseña la alerta con su icono decorativo", async () => {
    server.use(
      http.post(apiUrl("/gestorias/registro"), () =>
        HttpResponse.json({ motivo: "Revisa los datos." }, { status: 400 }),
      ),
    )
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <RegistroPage />
      </MemoryRouter>,
    )

    await user.type(screen.getByLabelText("Nombre de la gestoría"), "Gestoría Uno")
    await user.type(screen.getByLabelText("Tu nombre"), "Ana")
    await user.type(screen.getByLabelText("Email"), "a@b.es")
    await user.type(screen.getByLabelText("Contraseña"), "x")
    await user.click(screen.getByRole("button", { name: "Continuar a pago" }))

    const alerta = await screen.findByRole("alert")
    expect(alerta).toHaveTextContent("No se ha podido completar el registro")
    expect(alerta.firstElementChild?.matches('svg[aria-hidden="true"]')).toBe(true)
  })
})

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
