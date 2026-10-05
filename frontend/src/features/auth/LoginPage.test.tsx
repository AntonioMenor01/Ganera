import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { MemoryRouter } from "react-router-dom"
import { describe, expect, it } from "vitest"
import { AuthProvider } from "@/shared/auth/AuthContext"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { LoginPage } from "./LoginPage"

describe("LoginPage: error uniforme (I1)", () => {
  it("un 401 con motivo del backend sigue mostrando solo el texto uniforme", async () => {
    server.use(
      http.post(apiUrl("/auth/login"), () =>
        HttpResponse.json({ motivo: "Usuario inactivo" }, { status: 401 }),
      ),
    )
    const user = userEvent.setup()
    render(
      <AuthProvider>
        <MemoryRouter>
          <LoginPage />
        </MemoryRouter>
      </AuthProvider>,
    )

    await user.type(screen.getByLabelText("Email"), "a@b.es")
    await user.type(screen.getByLabelText("Contraseña"), "x")
    await user.click(screen.getByRole("button", { name: "Entrar" }))

    expect(await screen.findByText("Email o contraseña incorrectos.")).toBeInTheDocument()
    expect(screen.queryByText("Usuario inactivo")).not.toBeInTheDocument()
    // El error lleva su icono decorativo, no solo el color.
    expect(screen.getByRole("alert").firstElementChild?.matches('svg[aria-hidden="true"]')).toBe(true)
  })
})

describe("LoginPage: estructura y enlace (Task 10)", () => {
  function renderizar() {
    render(
      <AuthProvider>
        <MemoryRouter>
          <LoginPage />
        </MemoryRouter>
      </AuthProvider>,
    )
  }

  it("tiene la región main y el h1 «Ganera»", () => {
    renderizar()
    expect(screen.getByRole("main")).toBeInTheDocument()
    expect(screen.getByRole("heading", { level: 1, name: "Ganera" })).toBeInTheDocument()
  })

  it("el enlace «Regístrate» usa la receta común, con el anillo de foco", () => {
    renderizar()
    expect(screen.getByRole("link", { name: "Regístrate" })).toHaveClass("focus-visible:ring-3")
  })
})
