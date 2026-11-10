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

/** Sesión iniciada y lo que pide la app autenticada al aterrizar (inicio o trámites). */
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
    http.get(apiUrl("/ganaderos"), () =>
      HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 1 }),
    ),
  )
}

describe("rutas: comodín del layout autenticado (D5)", () => {
  it.each(["/facturacion", "/no-existe", "/ganaderos/5/otra-cosa"])(
    "con sesión, %s lleva a / (inicio, sin la página de error de React Router)",
    async (url) => {
      conSesion()
      const router = montarApp(url)

      // El saludo del dashboard es el h1 del inicio (hora de Madrid → «Buenos…»).
      expect(await screen.findByRole("heading", { level: 1 })).toHaveTextContent(/Buenos|Buenas/)
      expect(router.state.location.pathname).toBe("/")
      // replace: la ruta desconocida no queda en el historial.
      expect(router.state.historyAction).toBe("REPLACE")
    },
  )

  it("sin sesión, /facturacion lleva a /login (el comodín no se salta RequireAuth)", async () => {
    const router = montarApp("/facturacion")

    expect(await screen.findByText("Usa el email y la contraseña que te enviamos al contratar.")).toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/login")
  })

  it("sin sesión, /login sigue siendo su pantalla", async () => {
    const router = montarApp("/login")
    expect(await screen.findByText("Usa el email y la contraseña que te enviamos al contratar.")).toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/login")
  })
})

describe("rutas: /registro ya no es una pantalla (Prompt C, D4)", () => {
  // El alta pública está cerrada en el backend (404) y pasará a la landing: /registro lleva a /login.
  it("sin sesión, /registro lleva a /login sin quedar en el historial", async () => {
    const router = montarApp("/registro")

    expect(await screen.findByText("Usa el email y la contraseña que te enviamos al contratar.")).toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/login")
    expect(router.state.historyAction).toBe("REPLACE")
    expect(screen.queryByText("Registra tu gestoría y empieza tu prueba de 15 días.")).not.toBeInTheDocument()
  })

  it("con sesión, /registro lleva a /login como cualquier visita a /login (sin pantalla rota)", async () => {
    conSesion()
    const router = montarApp("/registro")

    expect(await screen.findByText("Usa el email y la contraseña que te enviamos al contratar.")).toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/login")
    expect(screen.queryByText("Registra tu gestoría y empieza tu prueba de 15 días.")).not.toBeInTheDocument()
  })

  it("navegar a /registro desde /login deja en /login", async () => {
    const router = montarApp("/login")
    await screen.findByText("Usa el email y la contraseña que te enviamos al contratar.")

    await act(() => router.navigate("/registro"))
    expect(router.state.location.pathname).toBe("/login")
    expect(await screen.findByLabelText("Contraseña")).toBeInTheDocument()
  })
})

describe("rutas: ficha para OVZ (ficha OVZ, T5)", () => {
  const detalle = {
    id: 5,
    tipoTramite: "BAJA_MUERTE",
    estado: "APROBADO",
    motivoError: null,
    explotacionId: 3,
    explotacionCodigoRega: "ES061230000012",
    explotacionNombre: "Los Llanos",
    ganaderoNombre: "Juan Pérez Gil",
    ganaderoNif: "12345678Z",
    mensajeOriginal: null,
    crotales: [],
    version: 1,
    origen: null,
    estadoExtraccion: null,
    crotalesDescartados: 0,
    hechoEnOvzEn: null,
    hechoEnOvzPorNombre: null,
    whatsapp: null,
    motivoRechazo: null,
    descartable: false,
    historialDescarte: [],
  }

  it("con sesión, /tramites/5/ovz es la ficha, fuera del layout (sin la barra de la app)", async () => {
    conSesion()
    server.use(http.get(apiUrl("/tramites/5"), () => HttpResponse.json(detalle)))
    const router = montarApp("/tramites/5/ovz")

    expect(await screen.findByRole("heading", { level: 1, name: "Baja de Bovino" })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/tramites/5/ovz")
    expect(screen.queryByRole("navigation", { name: "Principal" })).not.toBeInTheDocument()
  })

  it("sin sesión (ventana abierta sin heredarla, D3), va al login recordando la ficha para volver", async () => {
    const router = montarApp("/tramites/5/ovz")

    expect(await screen.findByText("Usa el email y la contraseña que te enviamos al contratar.")).toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/login")
    expect(router.state.location.state).toMatchObject({ from: "/tramites/5/ovz" })
  })
})
