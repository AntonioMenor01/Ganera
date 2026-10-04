import { render, screen, within } from "@testing-library/react"
import { http, HttpResponse } from "msw"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { beforeEach, describe, expect, it } from "vitest"
import { AuthProvider } from "@/shared/auth/AuthContext"
import { routes } from "@/router"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"

/** Monta la app real (AuthProvider + rutas reales) en una URL, con sesión iniciada. */
function montarApp(url: string) {
  const router = createMemoryRouter(routes, { initialEntries: [url] })
  render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  )
  return router
}

beforeEach(() => {
  sessionStorage.setItem("ganera.token", "tok")
  server.use(
    http.get(apiUrl("/auth/me"), () =>
      HttpResponse.json({ id: 1, email: "ana@gestoria.es", nombre: "Ana", gestoriaId: 1, activo: true }),
    ),
    http.get(apiUrl("/facturacion/suscripcion"), () => new HttpResponse(null, { status: 404 })),
  )
})

describe("navegación: Ganaderos", () => {
  it("el enlace Ganaderos está en la barra, en orden, y activo en el detalle /ganaderos/5", async () => {
    server.use(
      http.get(apiUrl("/ganaderos/5"), () =>
        HttpResponse.json({ id: 5, nombre: "Pedro Sanz", nif: "11111111H", explotaciones: [] }),
      ),
    )
    montarApp("/ganaderos/5")

    expect(await screen.findByRole("heading", { level: 1, name: "Pedro Sanz" })).toBeInTheDocument()
    const barra = within(screen.getByRole("banner")).getByRole("navigation")
    const enlaces = within(barra).getAllByRole("link")
    expect(enlaces.map((e) => e.textContent)).toEqual([
      "Trámites",
      "Ganaderos",
      "Explotaciones",
    ])
    const ganaderos = within(barra).getByRole("link", { name: "Ganaderos" })
    expect(ganaderos).toHaveAttribute("href", "/ganaderos")
    expect(ganaderos).toHaveAttribute("aria-current", "page")
    expect(within(barra).getByRole("link", { name: "Trámites" })).not.toHaveAttribute("aria-current")
  })

  it("/ganaderos es la pantalla real del listado, no un marcador", async () => {
    server.use(
      http.get(apiUrl("/ganaderos"), () =>
        HttpResponse.json({
          content: [{ id: 5, nombre: "Pedro Sanz", nif: "11111111H", numeroExplotaciones: 2 }],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 20,
        }),
      ),
    )
    montarApp("/ganaderos")

    expect(await screen.findByRole("link", { name: "Pedro Sanz" })).toHaveAttribute("href", "/ganaderos/5")
    expect(screen.queryByText("Ganaderos (pendiente)")).not.toBeInTheDocument()
  })
})
