import { act, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { delay, http, HttpResponse } from "msw"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { AuthProvider } from "@/shared/auth/AuthContext"
import { routes } from "@/router"
import { TEXTO_ERROR_RED, TEXTO_ERROR_SERVIDOR } from "@/shared/api/errores"
import { notifyUnauthorized } from "@/shared/api/authSession"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"

const USUARIO = {
  id: 7,
  email: "ana@gestoria.es",
  nombre: "Ana",
  gestoriaId: 3,
  activo: true,
}
const AVISO_CADUCADA = "Tu sesión ha caducado. Vuelve a iniciar sesión."
const CLAVE = "ganera.token"

/** Monta la app real (AuthProvider + rutas reales) en una URL, como tras una recarga. */
function montarApp(url: string) {
  const router = createMemoryRouter(routes, { initialEntries: [url] })
  const rutasVisitadas: string[] = [router.state.location.pathname]
  router.subscribe((estado) => rutasVisitadas.push(estado.location.pathname))
  render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  )
  return { router, rutasVisitadas }
}

// /ganaderos es la ruta autenticada de estos tests. Desde la Task 7 es la pantalla real y pide su
// listado: aquí basta con una página vacía (lo que se prueba es la sesión, no el listado).
beforeEach(() => {
  server.use(
    http.get(apiUrl("/ganaderos"), () =>
      HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
    ),
  )
})

/**
 * Sale de /login y vuelve a entrar, para que LoginPage se monte de nuevo y relea el aviso pendiente.
 * Sin sesión, "/" pasa por RequireAuth, que manda a /login (antes se usaba /registro, que desde el
 * Prompt C ya no es una pantalla).
 */
async function volverALogin(router: ReturnType<typeof createMemoryRouter>) {
  await act(() => router.navigate("/"))
  expect(router.state.location.pathname).toBe("/login")
}

const TITULO_GANADEROS = { level: 1, name: "Ganaderos" } as const

/** Handlers de lo que pide la app autenticada fuera de la sesión (banner de suscripción). */
function suscripcionSinDatos() {
  server.use(http.get(apiUrl("/facturacion/suscripcion"), () => new HttpResponse(null, { status: 404 })))
}

function meDevuelveUsuario(cabeceras: (string | null)[] = []) {
  server.use(
    http.get(apiUrl("/auth/me"), ({ request }) => {
      cabeceras.push(request.headers.get("authorization"))
      return HttpResponse.json(USUARIO)
    }),
  )
}

describe("sesión persistente: recarga con token guardado", () => {
  it("200 en /auth/me: renderiza la ruta pedida sin pasar por /login y usa el token restaurado", async () => {
    sessionStorage.setItem(CLAVE, "tok-guardado")
    const cabeceras: (string | null)[] = []
    meDevuelveUsuario(cabeceras)
    suscripcionSinDatos()

    const { router, rutasVisitadas } = montarApp("/ganaderos?pagina=2")

    expect(await screen.findByRole("heading", TITULO_GANADEROS)).toBeInTheDocument()
    expect(await screen.findByText(USUARIO.email)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/ganaderos")
    expect(router.state.location.search).toBe("?pagina=2")
    expect(rutasVisitadas).not.toContain("/login")
    expect(cabeceras).toEqual(["Bearer tok-guardado"])
    expect(sessionStorage.getItem(CLAVE)).toBe("tok-guardado")
  })

  it("mientras comprueba, enseña un estado de carga accesible y nunca el formulario de login", async () => {
    sessionStorage.setItem(CLAVE, "tok-guardado")
    server.use(
      http.get(apiUrl("/auth/me"), async () => {
        await delay(50)
        return HttpResponse.json(USUARIO)
      }),
    )
    suscripcionSinDatos()

    const { rutasVisitadas } = montarApp("/ganaderos")

    expect(screen.getByRole("status")).toHaveTextContent("Comprobando tu sesión")
    expect(screen.queryByLabelText("Contraseña")).not.toBeInTheDocument()
    expect(await screen.findByRole("heading", TITULO_GANADEROS)).toBeInTheDocument()
    expect(rutasVisitadas).not.toContain("/login")
  })

  it("401 en /auth/me: borra el token y lleva a /login con el aviso de sesión caducada", async () => {
    sessionStorage.setItem(CLAVE, "tok-caducado")
    server.use(http.get(apiUrl("/auth/me"), () => new HttpResponse(null, { status: 401 })))

    const { router } = montarApp("/tramites")

    expect(await screen.findByText(AVISO_CADUCADA)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/login")
    expect(sessionStorage.getItem(CLAVE)).toBeNull()
  })

  it("error de red en /auth/me: conserva el token, ofrece Reintentar y el reintento entra", async () => {
    sessionStorage.setItem(CLAVE, "tok-guardado")
    server.use(http.get(apiUrl("/auth/me"), () => HttpResponse.error()))
    suscripcionSinDatos()
    const user = userEvent.setup()

    const { router, rutasVisitadas } = montarApp("/ganaderos")

    expect(await screen.findByText(TEXTO_ERROR_RED)).toBeInTheDocument()
    const alerta = screen.getByText("No se ha podido comprobar tu sesión").closest('[role="alert"]')!
    expect(alerta.firstElementChild?.matches('svg[aria-hidden="true"]')).toBe(true)
    expect(sessionStorage.getItem(CLAVE)).toBe("tok-guardado")
    expect(screen.queryByText(AVISO_CADUCADA)).not.toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/ganaderos")

    meDevuelveUsuario()
    await user.click(screen.getByRole("button", { name: "Reintentar" }))

    expect(await screen.findByRole("heading", TITULO_GANADEROS)).toBeInTheDocument()
    expect(rutasVisitadas).not.toContain("/login")
  })

  it("5xx en /auth/me: tampoco cierra la sesión", async () => {
    sessionStorage.setItem(CLAVE, "tok-guardado")
    server.use(http.get(apiUrl("/auth/me"), () => new HttpResponse("boom", { status: 503 })))

    const { router } = montarApp("/tramites")

    expect(await screen.findByText(TEXTO_ERROR_SERVIDOR)).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Reintentar" })).toBeInTheDocument()
    expect(sessionStorage.getItem(CLAVE)).toBe("tok-guardado")
    expect(router.state.location.pathname).toBe("/tramites")
  })
})

describe("sesión persistente: login, caducidad en uso y salida manual", () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it("una visita normal a /login no enseña aviso", async () => {
    montarApp("/login")
    expect(await screen.findByLabelText("Contraseña")).toBeInTheDocument()
    expect(screen.queryByText(AVISO_CADUCADA)).not.toBeInTheDocument()
  })

  it("login guarda SOLO el token en sessionStorage y vuelve a la ruta de la que se le echó", async () => {
    const escrituras: [string, string][] = []
    const setItemOriginal = Storage.prototype.setItem
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(function (
      this: Storage,
      clave: string,
      valor: string,
    ) {
      escrituras.push([clave, valor])
      setItemOriginal.call(this, clave, valor)
    })
    server.use(http.post(apiUrl("/auth/login"), () => HttpResponse.json({ token: "tok-nuevo" })))
    meDevuelveUsuario()
    suscripcionSinDatos()
    const user = userEvent.setup()

    const { router } = montarApp("/ganaderos?pagina=2")
    // Sin token, se le manda a /login sin aviso (nunca hubo sesión).
    expect(await screen.findByLabelText("Contraseña")).toBeInTheDocument()
    expect(screen.queryByText(AVISO_CADUCADA)).not.toBeInTheDocument()

    await user.type(screen.getByLabelText("Email"), USUARIO.email)
    await user.type(screen.getByLabelText("Contraseña"), "secreta")
    await user.click(screen.getByRole("button", { name: "Entrar" }))

    expect(await screen.findByRole("heading", TITULO_GANADEROS)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/ganaderos")
    expect(router.state.location.search).toBe("?pagina=2")
    expect(sessionStorage.getItem(CLAVE)).toBe("tok-nuevo")
    // Nada más que el token: ni usuario ni email, ni en sessionStorage ni en localStorage.
    expect(escrituras).toEqual([[CLAVE, "tok-nuevo"]])
    expect(sessionStorage.length).toBe(1)
    expect(localStorage.length).toBe(0)
    expect(JSON.stringify({ ...sessionStorage })).not.toContain(USUARIO.email)
  })

  it("login desde /login directo lleva a /tramites", async () => {
    server.use(
      http.post(apiUrl("/auth/login"), () => HttpResponse.json({ token: "tok-nuevo" })),
      http.get(apiUrl("/tramites"), () =>
        HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
      ),
    )
    meDevuelveUsuario()
    suscripcionSinDatos()
    const user = userEvent.setup()

    const { router } = montarApp("/login")
    await user.type(await screen.findByLabelText("Email"), USUARIO.email)
    await user.type(screen.getByLabelText("Contraseña"), "secreta")
    await user.click(screen.getByRole("button", { name: "Entrar" }))

    await waitFor(() => expect(router.state.location.pathname).toBe("/tramites"))
  })

  it("un 401 del login mantiene su texto uniforme, sin aviso de caducada y sin guardar nada", async () => {
    server.use(http.post(apiUrl("/auth/login"), () => new HttpResponse(null, { status: 401 })))
    const user = userEvent.setup()

    montarApp("/login")
    await user.type(await screen.findByLabelText("Email"), USUARIO.email)
    await user.type(screen.getByLabelText("Contraseña"), "mala")
    await user.click(screen.getByRole("button", { name: "Entrar" }))

    expect(await screen.findByText("Email o contraseña incorrectos.")).toBeInTheDocument()
    expect(screen.queryByText(AVISO_CADUCADA)).not.toBeInTheDocument()
    expect(sessionStorage.length).toBe(0)
  })

  it("si /auth/me falla justo tras el login, no queda token guardado ni aviso de caducada", async () => {
    server.use(
      http.post(apiUrl("/auth/login"), () => HttpResponse.json({ token: "tok-nuevo" })),
      http.get(apiUrl("/auth/me"), () => new HttpResponse(null, { status: 401 })),
    )
    const user = userEvent.setup()

    const { router } = montarApp("/login")
    await user.type(await screen.findByLabelText("Email"), USUARIO.email)
    await user.type(screen.getByLabelText("Contraseña"), "secreta")
    await user.click(screen.getByRole("button", { name: "Entrar" }))

    expect(await screen.findByText("Email o contraseña incorrectos.")).toBeInTheDocument()
    expect(sessionStorage.getItem(CLAVE)).toBeNull()
    expect(router.state.location.pathname).toBe("/login")
    await volverALogin(router)
    expect(await screen.findByLabelText("Contraseña")).toBeInTheDocument()
    expect(screen.queryByText(AVISO_CADUCADA)).not.toBeInTheDocument()
  })

  it("un 401 en mitad del uso cierra la sesión y avisa una sola vez", async () => {
    sessionStorage.setItem(CLAVE, "tok-guardado")
    meDevuelveUsuario()
    // La primera petición de la app autenticada responde 401: el token ha caducado en uso.
    server.use(http.get(apiUrl("/facturacion/suscripcion"), () => new HttpResponse(null, { status: 401 })))

    const { router } = montarApp("/ganaderos")

    expect(await screen.findByText(AVISO_CADUCADA)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/login")
    expect(sessionStorage.getItem(CLAVE)).toBeNull()

    // Una vez visto, no vuelve a salir al volver a /login.
    await volverALogin(router)
    expect(await screen.findByLabelText("Contraseña")).toBeInTheDocument()
    expect(screen.queryByText(AVISO_CADUCADA)).not.toBeInTheDocument()
  })

  it("Salir borra el token y no enseña el aviso de caducada", async () => {
    sessionStorage.setItem(CLAVE, "tok-guardado")
    meDevuelveUsuario()
    suscripcionSinDatos()
    const user = userEvent.setup()

    const { router } = montarApp("/ganaderos")
    await user.click(await screen.findByRole("button", { name: "Salir" }))

    expect(await screen.findByLabelText("Contraseña")).toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/login")
    expect(screen.queryByText(AVISO_CADUCADA)).not.toBeInTheDocument()
    expect(sessionStorage.getItem(CLAVE)).toBeNull()

    // M6: tras Salir no se guarda la ruta de vuelta; el siguiente login va a /tramites.
    server.use(
      http.post(apiUrl("/auth/login"), () => HttpResponse.json({ token: "tok-nuevo" })),
      http.get(apiUrl("/tramites"), () =>
        HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
      ),
    )
    await user.type(screen.getByLabelText("Email"), USUARIO.email)
    await user.type(screen.getByLabelText("Contraseña"), "secreta")
    await user.click(screen.getByRole("button", { name: "Entrar" }))
    await waitFor(() => expect(router.state.location.pathname).toBe("/tramites"))
  })

  it("M4: Salir y un 401 tardío en /login no dejan aviso de caducada para la siguiente visita", async () => {
    sessionStorage.setItem(CLAVE, "tok-guardado")
    meDevuelveUsuario()
    suscripcionSinDatos()
    const user = userEvent.setup()

    const { router } = montarApp("/ganaderos")
    await user.click(await screen.findByRole("button", { name: "Salir" }))
    expect(await screen.findByLabelText("Contraseña")).toBeInTheDocument()

    // Una petición lanzada antes de Salir responde 401 ahora, ya en /login (ruta pública).
    act(() => notifyUnauthorized())
    await volverALogin(router)

    expect(await screen.findByLabelText("Contraseña")).toBeInTheDocument()
    expect(screen.queryByText(AVISO_CADUCADA)).not.toBeInTheDocument()
  })

  it("M3: un token caducado detectado en /login no deja aviso para una visita posterior a /login", async () => {
    sessionStorage.setItem(CLAVE, "tok-caducado")
    let llamadasMe = 0
    server.use(
      http.get(apiUrl("/auth/me"), () => {
        llamadasMe += 1
        return new HttpResponse(null, { status: 401 })
      }),
    )

    const { router } = montarApp("/login")
    await waitFor(() => expect(llamadasMe).toBe(1))
    await waitFor(() => expect(sessionStorage.getItem(CLAVE)).toBeNull())

    await volverALogin(router)
    expect(await screen.findByLabelText("Contraseña")).toBeInTheDocument()
    expect(screen.queryByText(AVISO_CADUCADA)).not.toBeInTheDocument()
  })

  it("M1: una comprobación inicial pendiente no borra el token de un login hecho entretanto", async () => {
    sessionStorage.setItem(CLAVE, "tok-viejo")
    let llamadasMe = 0
    let responderComprobacionVieja: () => void = () => {}
    const comprobacionViejaRespondida = new Promise<void>((resolver) => {
      responderComprobacionVieja = resolver
    })
    let liberarComprobacionVieja: () => void = () => {}
    const puedeResponderVieja = new Promise<void>((resolver) => {
      liberarComprobacionVieja = resolver
    })
    server.use(
      http.post(apiUrl("/auth/login"), () => HttpResponse.json({ token: "tok-nuevo" })),
      http.get(apiUrl("/tramites"), () =>
        HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
      ),
      http.get(apiUrl("/auth/me"), async ({ request }) => {
        llamadasMe += 1
        if (request.headers.get("authorization") === "Bearer tok-viejo") {
          // La comprobación del arranque tarda: solo responde (401) cuando el login ya terminó.
          await puedeResponderVieja
          setTimeout(responderComprobacionVieja, 0)
          return new HttpResponse(null, { status: 401 })
        }
        return HttpResponse.json(USUARIO)
      }),
    )
    suscripcionSinDatos()
    const user = userEvent.setup()

    const { router } = montarApp("/login")
    await user.type(await screen.findByLabelText("Email"), USUARIO.email)
    await user.type(screen.getByLabelText("Contraseña"), "secreta")
    await user.click(screen.getByRole("button", { name: "Entrar" }))
    await waitFor(() => expect(router.state.location.pathname).toBe("/tramites"))
    expect(llamadasMe).toBe(2)

    liberarComprobacionVieja()
    await act(() => comprobacionViejaRespondida)
    await act(() => new Promise((r) => setTimeout(r, 20)))

    expect(sessionStorage.getItem(CLAVE)).toBe("tok-nuevo")
    expect(router.state.location.pathname).toBe("/tramites")
    expect(screen.queryByText(AVISO_CADUCADA)).not.toBeInTheDocument()
  })

  it("M1: la comprobación vieja responde 401 en mitad del login y el login sigue siendo coherente", async () => {
    sessionStorage.setItem(CLAVE, "tok-viejo")
    let liberarVieja: () => void = () => {}
    const puedeResponderVieja = new Promise<void>((resolver) => {
      liberarVieja = resolver
    })
    let marcarViejaRespondida: () => void = () => {}
    const viejaRespondida = new Promise<void>((resolver) => {
      marcarViejaRespondida = resolver
    })
    server.use(
      http.post(apiUrl("/auth/login"), () => HttpResponse.json({ token: "tok-nuevo" })),
      http.get(apiUrl("/tramites"), () =>
        HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
      ),
      http.get(apiUrl("/auth/me"), async ({ request }) => {
        if (request.headers.get("authorization") === "Bearer tok-viejo") {
          await puedeResponderVieja
          setTimeout(marcarViejaRespondida, 0)
          return new HttpResponse(null, { status: 401 })
        }
        // El /auth/me del login: deja que la comprobación vieja responda 401 antes de contestar.
        liberarVieja()
        await viejaRespondida
        await delay(20)
        return HttpResponse.json(USUARIO)
      }),
    )
    suscripcionSinDatos()
    const user = userEvent.setup()

    const { router } = montarApp("/login")
    await user.type(await screen.findByLabelText("Email"), USUARIO.email)
    await user.type(screen.getByLabelText("Contraseña"), "secreta")
    await user.click(screen.getByRole("button", { name: "Entrar" }))

    await waitFor(() => expect(router.state.location.pathname).toBe("/tramites"))
    await act(() => new Promise((r) => setTimeout(r, 20)))
    expect(sessionStorage.getItem(CLAVE)).toBe("tok-nuevo")
    expect(router.state.location.pathname).toBe("/tramites")
  })

  it("M2: con sesión ya activa, si el /auth/me de un nuevo login falla no queda una sesión a medias", async () => {
    sessionStorage.setItem(CLAVE, "tok-viejo")
    let llamadasMe = 0
    server.use(
      http.post(apiUrl("/auth/login"), () => HttpResponse.json({ token: "tok-nuevo" })),
      http.get(apiUrl("/auth/me"), ({ request }) => {
        llamadasMe += 1
        if (request.headers.get("authorization") === "Bearer tok-viejo") {
          return HttpResponse.json(USUARIO)
        }
        return new HttpResponse(null, { status: 503 })
      }),
    )
    suscripcionSinDatos()
    const user = userEvent.setup()

    // Recarga en /login con una sesión válida: la comprobación la deja activa de fondo.
    const { router } = montarApp("/login")
    await waitFor(() => expect(llamadasMe).toBe(1))
    await act(() => new Promise((r) => setTimeout(r, 20)))

    await user.type(screen.getByLabelText("Email"), USUARIO.email)
    await user.type(screen.getByLabelText("Contraseña"), "secreta")
    await user.click(screen.getByRole("button", { name: "Entrar" }))
    expect(await screen.findByText(TEXTO_ERROR_SERVIDOR)).toBeInTheDocument()

    // Reinicio completo y coherente: ni token guardado ni sesión en React.
    expect(sessionStorage.getItem(CLAVE)).toBeNull()
    await act(() => router.navigate("/ganaderos"))
    await waitFor(() => expect(router.state.location.pathname).toBe("/login"))
    expect(screen.queryByRole("heading", TITULO_GANADEROS)).not.toBeInTheDocument()
    expect(screen.queryByText(AVISO_CADUCADA)).not.toBeInTheDocument()
  })

  it("con sessionStorage inaccesible, el login funciona en memoria como antes", async () => {
    vi.spyOn(window, "sessionStorage", "get").mockImplementation(() => {
      throw new DOMException("bloqueado", "SecurityError")
    })
    const cabeceras: (string | null)[] = []
    server.use(http.post(apiUrl("/auth/login"), () => HttpResponse.json({ token: "tok-memoria" })))
    meDevuelveUsuario(cabeceras)
    suscripcionSinDatos()
    const user = userEvent.setup()

    montarApp("/ganaderos")
    await user.type(await screen.findByLabelText("Email"), USUARIO.email)
    await user.type(screen.getByLabelText("Contraseña"), "secreta")
    await user.click(screen.getByRole("button", { name: "Entrar" }))

    expect(await screen.findByRole("heading", TITULO_GANADEROS)).toBeInTheDocument()
    expect(cabeceras).toEqual(["Bearer tok-memoria"])
  })
})
