import { act, render, screen } from "@testing-library/react"
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
  render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  )
  return router
}

/** Sesión iniciada y lo que pide la app autenticada al aterrizar en /tramites. */
function conSesion() {
  sessionStorage.setItem("ganera.token", "tok")
  server.use(
    http.get(apiUrl("/auth/me"), () =>
      HttpResponse.json({ id: 1, email: "ana@gestoria.es", nombre: "Ana", gestoriaId: 1, activo: true }),
    ),
    http.get(apiUrl("/facturacion/suscripcion"), () => new HttpResponse(null, { status: 404 })),
    http.get(apiUrl("/tramites"), () =>
      HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
    ),
  )
}

describe("rutas: comodín del layout autenticado (D5)", () => {
  it.each(["/facturacion", "/no-existe", "/ganaderos/5/otra-cosa"])(
    "con sesión, %s lleva a /tramites (sin la página de error de React Router)",
    async (url) => {
      conSesion()
      const router = montarApp(url)

      expect(await screen.findByRole("heading", { level: 1, name: "Cola de trámites" })).toBeInTheDocument()
      expect(router.state.location.pathname).toBe("/tramites")
      // replace: la ruta desconocida no queda en el historial.
      expect(router.state.historyAction).toBe("REPLACE")
    },
  )

  it("sin sesión, /facturacion lleva a /login (el comodín no se salta RequireAuth)", async () => {
    const router = montarApp("/facturacion")

    expect(await screen.findByText("Inicia sesión con tu cuenta de gestoría.")).toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/login")
  })

  it("sin sesión, /login y /registro siguen siendo sus pantallas", async () => {
    const router = montarApp("/login")
    expect(await screen.findByText("Inicia sesión con tu cuenta de gestoría.")).toBeInTheDocument()

    await act(() => router.navigate("/registro"))
    expect(await screen.findByText("Registra tu gestoría y empieza tu prueba de 15 días.")).toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/registro")
  })
})
