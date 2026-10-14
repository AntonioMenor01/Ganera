import { render, screen, within } from "@testing-library/react"
import { http, HttpResponse } from "msw"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { describe, expect, it } from "vitest"
import { AuthProvider } from "@/shared/auth/AuthContext"
import { routes } from "@/router"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"

/** Monta la app real (AuthProvider + rutas reales) en una URL. */
function montarApp(url: string) {
  const router = createMemoryRouter(routes, { initialEntries: [url] })
  return render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  )
}

function logosDe(elemento: HTMLElement) {
  return elemento.querySelectorAll('[data-slot="logo-ganera"]')
}

describe("marca: el símbolo de Ganera aparece donde debe", () => {
  it("navbar: símbolo decorativo en color de token junto al logotipo GANERA", async () => {
    sessionStorage.setItem("ganera.token", "tok")
    server.use(
      http.get(apiUrl("/auth/me"), () =>
        HttpResponse.json({ id: 1, email: "ana@gestoria.es", nombre: "Ana", gestoriaId: 1, activo: true }),
      ),
      http.get(apiUrl("/facturacion/suscripcion"), () => new HttpResponse(null, { status: 404 })),
      // /ganaderos es la pantalla real desde la Task 7: pide su listado.
      http.get(apiUrl("/ganaderos"), () =>
        HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
      ),
    )

    montarApp("/ganaderos")

    const cabecera = await screen.findByRole("banner")
    const logos = logosDe(cabecera)
    expect(logos).toHaveLength(1)
    expect(logos[0]).toHaveClass("logo-ganera", "text-marca")
    expect(logos[0]).toHaveAttribute("aria-hidden", "true")
    // El texto sigue siendo el nombre visible (y accesible) de la marca, y el punto ya no está.
    const logotipo = within(cabecera).getByText("GANERA")
    expect(logotipo).toBeInTheDocument()
    // Logotipo en Tinta y 700; el rojo de marca es solo del símbolo.
    expect(logotipo).toHaveClass("font-bold", "text-foreground")
    expect(logotipo).not.toHaveClass("text-primary")
    expect(cabecera.querySelector(".rounded-full")).toBeNull()
  })

  it("/login: símbolo encima del título del formulario y símbolo blanco en el panel rojo", async () => {
    const { container } = montarApp("/login")

    await screen.findByText("Inicia sesión con tu cuenta de gestoría.")
    const logos = logosDe(container)
    expect(logos).toHaveLength(2)
    const [logoPanel, logoFormulario] = logos
    // Panel rojo: decorativo (al lado está el texto «GANERA») y en blanco sobre Rojo 600.
    expect(logoPanel).toHaveClass("logo-ganera", "text-primary-foreground")
    expect(logoPanel).not.toHaveClass("text-marca")
    expect(logoPanel).toHaveAttribute("aria-hidden", "true")
    expect(logoPanel.closest('[data-slot="login-panel"]')).toHaveClass("bg-primary")
    // Formulario: en marca, decorativo (el título visible ya da el nombre) y justo encima del título.
    expect(logoFormulario).toHaveClass("logo-ganera", "text-marca")
    expect(logoFormulario).toHaveAttribute("aria-hidden", "true")
    const titulo = screen.getByRole("heading", { level: 1, name: "Entrar en Ganera" })
    expect(logoFormulario.nextElementSibling).toBe(titulo)
  })
})

describe("marca: enlace de texto", () => {
  it("el enlace de texto usa Rojo 700 (--enlace) y el anillo de foco sólido", async () => {
    // Antes se comprobaba en «Regístrate» del login, que ya no existe (Prompt C): ahora en el
    // «Volver a Ganaderos» de la ficha de un ganadero que no se encuentra.
    sessionStorage.setItem("ganera.token", "tok")
    server.use(
      http.get(apiUrl("/auth/me"), () =>
        HttpResponse.json({ id: 1, email: "ana@gestoria.es", nombre: "Ana", gestoriaId: 1, activo: true }),
      ),
      http.get(apiUrl("/facturacion/suscripcion"), () => new HttpResponse(null, { status: 404 })),
      http.get(apiUrl("/ganaderos/99"), () => new HttpResponse(null, { status: 404 })),
    )
    montarApp("/ganaderos/99")

    const enlace = await screen.findByRole("link", { name: "Volver a Ganaderos" })
    expect(enlace).toHaveClass("text-enlace", "decoration-enlace/40", "focus-visible:ring-3", "focus-visible:ring-ring")
    expect(enlace).not.toHaveClass("text-primary")
    expect(enlace).not.toHaveClass("focus-visible:ring-ring/50")
    // La variante de tabla (Tinta en reposo) no se usa fuera de las tablas.
    expect(enlace).not.toHaveClass("text-foreground")
    expect(enlace).not.toHaveClass("hover:text-enlace")
  })
})
