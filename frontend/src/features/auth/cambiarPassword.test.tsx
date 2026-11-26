import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { createMemoryRouter, RouterProvider, type RouteObject } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it } from "vitest"
import { AuthProvider } from "@/shared/auth/AuthContext"
import { RequireAuth } from "@/shared/auth/RequireAuth"
import { CambiarPasswordPage } from "@/features/auth/CambiarPasswordPage"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"

const CLAVE = "ganera.token"
const USUARIO_FORZADO = {
  id: 7,
  email: "nuevo@gestoria.es",
  nombre: "Nuevo",
  gestoriaId: 3,
  activo: true,
  debeCambiarPassword: true,
}

/** Router mínimo (RequireAuth + la página real + destinos tontos) para no arrastrar el dashboard. */
function rutas(): RouteObject[] {
  return [
    { path: "/login", element: <h1>LOGIN</h1> },
    {
      element: <RequireAuth />,
      children: [
        { path: "/cambiar-password", element: <CambiarPasswordPage /> },
        { path: "/", element: <h1>INICIO</h1> },
        { path: "/ganaderos", element: <h1>GANADEROS</h1> },
      ],
    },
  ]
}

function montar(url: string) {
  const router = createMemoryRouter(rutas(), { initialEntries: [url] })
  render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  )
  return router
}

let cambiosRecibidos: Array<{ passwordActual: string; passwordNueva: string }>

beforeEach(() => {
  sessionStorage.setItem(CLAVE, "tok-guardado")
  cambiosRecibidos = []
  server.use(http.get(apiUrl("/auth/me"), () => HttpResponse.json(USUARIO_FORZADO)))
})

afterEach(() => {
  sessionStorage.clear()
})

/** Handler de cambio correcto: guarda el cuerpo y devuelve el usuario ya sin el flag. */
function cambioOk() {
  server.use(
    http.post(apiUrl("/auth/cambiar-password"), async ({ request }) => {
      cambiosRecibidos.push((await request.json()) as (typeof cambiosRecibidos)[number])
      return HttpResponse.json({ ...USUARIO_FORZADO, debeCambiarPassword: false })
    }),
  )
}

describe("cambio de contraseña forzado", () => {
  it("con debeCambiarPassword, una ruta protegida redirige a /cambiar-password", async () => {
    montar("/ganaderos")

    expect(await screen.findByRole("heading", { name: "Cambia tu contraseña" })).toBeInTheDocument()
    expect(screen.queryByRole("heading", { name: "GANADEROS" })).not.toBeInTheDocument()
  })

  it("un cambio correcto manda la contraseña y lleva al inicio", async () => {
    cambioOk()
    montar("/cambiar-password")
    await screen.findByRole("heading", { name: "Cambia tu contraseña" })

    await userEvent.type(screen.getByLabelText("Contraseña actual"), "temporal123")
    await userEvent.type(screen.getByLabelText("Contraseña nueva"), "miClaveNueva1")
    await userEvent.type(screen.getByLabelText("Repite la contraseña nueva"), "miClaveNueva1")
    await userEvent.click(screen.getByRole("button", { name: /Guardar y continuar/ }))

    expect(await screen.findByRole("heading", { name: "INICIO" })).toBeInTheDocument()
    expect(cambiosRecibidos).toEqual([{ passwordActual: "temporal123", passwordNueva: "miClaveNueva1" }])
  })

  it("una contraseña nueva demasiado corta no llama al backend", async () => {
    cambioOk()
    montar("/cambiar-password")
    await screen.findByRole("heading", { name: "Cambia tu contraseña" })

    await userEvent.type(screen.getByLabelText("Contraseña actual"), "temporal123")
    await userEvent.type(screen.getByLabelText("Contraseña nueva"), "corta")
    await userEvent.type(screen.getByLabelText("Repite la contraseña nueva"), "corta")
    await userEvent.click(screen.getByRole("button", { name: /Guardar y continuar/ }))

    expect(await screen.findByText(/al menos 8 caracteres/)).toBeInTheDocument()
    expect(cambiosRecibidos).toHaveLength(0)
  })

  it("si las dos contraseñas no coinciden, avisa y no llama al backend", async () => {
    cambioOk()
    montar("/cambiar-password")
    await screen.findByRole("heading", { name: "Cambia tu contraseña" })

    await userEvent.type(screen.getByLabelText("Contraseña actual"), "temporal123")
    await userEvent.type(screen.getByLabelText("Contraseña nueva"), "miClaveNueva1")
    await userEvent.type(screen.getByLabelText("Repite la contraseña nueva"), "otraDistinta1")
    await userEvent.click(screen.getByRole("button", { name: /Guardar y continuar/ }))

    expect(await screen.findByText(/no coinciden/)).toBeInTheDocument()
    expect(cambiosRecibidos).toHaveLength(0)
  })

  it("un 400 del backend enseña su motivo", async () => {
    server.use(
      http.post(apiUrl("/auth/cambiar-password"), () =>
        HttpResponse.json({ motivo: "La contraseña actual no es correcta." }, { status: 400 }),
      ),
    )
    montar("/cambiar-password")
    await screen.findByRole("heading", { name: "Cambia tu contraseña" })

    await userEvent.type(screen.getByLabelText("Contraseña actual"), "lo-que-sea")
    await userEvent.type(screen.getByLabelText("Contraseña nueva"), "miClaveNueva1")
    await userEvent.type(screen.getByLabelText("Repite la contraseña nueva"), "miClaveNueva1")
    await userEvent.click(screen.getByRole("button", { name: /Guardar y continuar/ }))

    expect(await screen.findByText("La contraseña actual no es correcta.")).toBeInTheDocument()
    expect(screen.queryByRole("heading", { name: "INICIO" })).not.toBeInTheDocument()
  })
})
