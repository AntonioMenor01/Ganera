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
    expect(logos[0]).toHaveClass("logo-ganera", "text-primary")
    expect(logos[0]).toHaveAttribute("aria-hidden", "true")
    // El texto sigue siendo el nombre visible (y accesible) de la marca, y el punto ya no está.
    expect(within(cabecera).getByText("GANERA")).toBeInTheDocument()
    expect(cabecera.querySelector(".rounded-full")).toBeNull()
  })

  it.each([
    ["/login", "Inicia sesión con tu cuenta de gestoría."],
    ["/registro", "Registra tu gestoría y empieza tu prueba de 15 días."],
  ])("%s: símbolo encima del título de la tarjeta", async (url, descripcion) => {
    const { container } = montarApp(url)

    await screen.findByText(descripcion)
    const logos = logosDe(container)
    expect(logos).toHaveLength(1)
    const [logo] = logos
    expect(logo).toHaveClass("logo-ganera", "text-primary")
    // Decorativo: el título visible "Ganera" ya da el nombre; no se lee dos veces.
    expect(logo).toHaveAttribute("aria-hidden", "true")
    const titulo = screen.getByText("Ganera")
    expect(logo.nextElementSibling).toBe(titulo)
  })
})
