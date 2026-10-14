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

describe("LoginPage: estructura (Task 10)", () => {
  function renderizar() {
    render(
      <AuthProvider>
        <MemoryRouter>
          <LoginPage />
        </MemoryRouter>
      </AuthProvider>,
    )
  }

  it("tiene la región main y el h1 «Entrar en Ganera»", () => {
    renderizar()
    expect(screen.getByRole("main")).toBeInTheDocument()
    expect(screen.getByRole("heading", { level: 1, name: "Entrar en Ganera" })).toBeInTheDocument()
    // Un solo encabezado: el panel rojo no lleva título propio.
    expect(screen.getAllByRole("heading")).toHaveLength(1)
  })

  it("el panel rojo lleva la frase aprobada, solo desde lg, y nada enfocable", () => {
    renderizar()
    const frase = screen.getByText(
      "Los mensajes de WhatsApp de tus ganaderos llegan como trámites listos para revisar.",
    )
    expect(frase.tagName).toBe("P")
    expect(frase).toHaveClass("hidden", "lg:block")
    const panel = frase.closest('[data-slot="login-panel"]') as HTMLElement
    expect(panel).not.toBeNull()
    expect(panel).toHaveClass("bg-primary", "text-primary-foreground")
    expect(panel.querySelectorAll("a, button, input, [tabindex]")).toHaveLength(0)
    // El panel va antes del formulario en el DOM (como v2).
    const formulario = screen.getByRole("button", { name: "Entrar" }).closest("form") as HTMLElement
    expect(panel.compareDocumentPosition(formulario) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it("los iconos de Email y Contraseña son decorativos y no cambian el nombre de los campos", () => {
    renderizar()
    for (const etiqueta of ["Email", "Contraseña"]) {
      const campo = screen.getByLabelText(etiqueta)
      expect(campo).toHaveAccessibleName(etiqueta)
      expect(campo).toHaveClass("h-11", "pl-10")
      const icono = campo.parentElement?.querySelector("svg")
      expect(icono).not.toBeNull()
      expect(icono).toHaveAttribute("aria-hidden", "true")
      expect(icono).toHaveClass("pointer-events-none", "text-muted-foreground")
    }
  })

  it("el botón se llama exactamente «Entrar» aunque lleve flecha", () => {
    renderizar()
    const boton = screen.getByRole("button", { name: "Entrar" })
    expect(boton).toHaveAccessibleName("Entrar")
    expect(boton).toHaveClass("h-12", "w-full")
  })

  it("no ofrece darse de alta: sin «Regístrate» ni ningún enlace (Prompt C, D4)", () => {
    renderizar()
    expect(screen.queryByText(/Regístrate/)).not.toBeInTheDocument()
    expect(screen.queryByText(/¿No tienes cuenta\?/)).not.toBeInTheDocument()
    expect(screen.queryAllByRole("link")).toHaveLength(0)
  })
})
