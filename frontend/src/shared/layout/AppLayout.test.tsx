import { act, cleanup, render, screen, within } from "@testing-library/react"
import { http, HttpResponse } from "msw"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { AuthProvider } from "@/shared/auth/AuthContext"
import { AppLayout } from "@/shared/layout/AppLayout"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"

const EMAIL_LARGO = "administracion.gestoria.ejemplo@agroservicios-castilla.es"

const ENLACES_ESPERADOS = [
  "Inicio",
  "Bandeja",
  "Trámites",
  "Ganaderos",
  "Explotaciones",
  "Calendario",
  "Informes",
  "Configuración",
] as const

const HREFS_ESPERADOS = [
  "/",
  "/bandeja",
  "/tramites",
  "/ganaderos",
  "/explotaciones",
  "/calendario",
  "/informes",
  "/configuracion",
] as const

/** El layout real con páginas de relleno: lo que se prueba es la barra, no las pantallas. */
function montarLayout(url: string) {
  const relleno = (texto: string) => <p>{texto}</p>
  const router = createMemoryRouter(
    [
      {
        path: "/",
        element: <AppLayout />,
        children: [
          { index: true, element: relleno("pantalla inicio") },
          { path: "bandeja", element: relleno("pantalla bandeja") },
          { path: "tramites", element: relleno("pantalla tramites") },
          { path: "ganaderos", element: relleno("pantalla ganaderos") },
          { path: "explotaciones", element: relleno("pantalla explotaciones") },
          { path: "calendario", element: relleno("pantalla calendario") },
          { path: "informes", element: relleno("pantalla informes") },
          { path: "configuracion", element: relleno("pantalla configuracion") },
        ],
      },
    ],
    { initialEntries: [url] },
  )
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
      HttpResponse.json({ id: 1, email: EMAIL_LARGO, nombre: "Ana", gestoriaId: 1, activo: true }),
    ),
    http.get(apiUrl("/facturacion/suscripcion"), () => new HttpResponse(null, { status: 404 })),
  )
})

/** Deshace el stub de Element.prototype.scrollIntoView (jsdom no lo implementa) pase o falle el test. */
let restaurarScrollIntoView: (() => void) | null = null

function espiarScrollIntoView() {
  const original = Object.getOwnPropertyDescriptor(Element.prototype, "scrollIntoView")
  const espia = vi.fn()
  Object.defineProperty(Element.prototype, "scrollIntoView", { configurable: true, writable: true, value: espia })
  restaurarScrollIntoView = () => {
    if (original) Object.defineProperty(Element.prototype, "scrollIntoView", original)
    else delete (Element.prototype as Partial<Element>).scrollIntoView
  }
  return espia
}

/** ResizeObserver simulado: guarda lo observado y deja disparar el callback a mano. */
class ResizeObserverFalso {
  static instancias: ResizeObserverFalso[] = []
  observados = new Set<Element>()
  desconectado = false
  private readonly callback: ResizeObserverCallback
  constructor(callback: ResizeObserverCallback) {
    this.callback = callback
    ResizeObserverFalso.instancias.push(this)
  }
  observe(el: Element) {
    this.observados.add(el)
  }
  unobserve(el: Element) {
    this.observados.delete(el)
  }
  disconnect() {
    this.desconectado = true
    this.observados.clear()
  }
  disparar() {
    this.callback([], this as unknown as ResizeObserver)
  }
}

/** Medidas simuladas de la tira: margen 16, enlaces de 80 px separados 4 px. */
function simularMedidas(nav: HTMLElement, anchoVisible: number) {
  let scrollLeft = 0
  Object.defineProperty(nav, "scrollLeft", {
    configurable: true,
    get: () => scrollLeft,
    set: (v: number) => {
      scrollLeft = v
    },
  })
  Object.defineProperty(nav, "clientWidth", { configurable: true, value: anchoVisible })
  within(nav)
    .getAllByRole("link")
    .forEach((enlace, i) => {
      Object.defineProperty(enlace, "offsetLeft", { configurable: true, value: 16 + i * 84 })
      Object.defineProperty(enlace, "offsetWidth", { configurable: true, value: 80 })
    })
  return { scrollLeft: () => scrollLeft }
}

afterEach(() => {
  restaurarScrollIntoView?.()
  restaurarScrollIntoView = null
  ResizeObserverFalso.instancias = []
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe("AppLayout: barra de navegación", () => {
  it("hay un solo nav Principal con los 8 enlaces del mock (sin Facturación)", async () => {
    montarLayout("/tramites")
    await screen.findByText("pantalla tramites")

    const navs = screen.getAllByRole("navigation")
    expect(navs).toHaveLength(1)
    const nav = screen.getByRole("navigation", { name: "Principal" })
    expect(within(nav).getAllByRole("link").map((e) => e.textContent)).toEqual([...ENLACES_ESPERADOS])
    expect(within(nav).queryByRole("link", { name: /facturaci/i })).not.toBeInTheDocument()
    expect(within(nav).getAllByRole("link").map((e) => e.getAttribute("href"))).toEqual([...HREFS_ESPERADOS])
  })

  it.each([
    ["/", "Inicio"],
    ["/bandeja", "Bandeja"],
    ["/tramites", "Trámites"],
    ["/ganaderos", "Ganaderos"],
    ["/explotaciones", "Explotaciones"],
    ["/calendario", "Calendario"],
    ["/informes", "Informes"],
    ["/configuracion", "Configuración"],
  ])("en %s solo %s lleva aria-current=page", async (url, activo) => {
    montarLayout(url)
    const nav = await screen.findByRole("navigation", { name: "Principal" })
    for (const enlace of within(nav).getAllByRole("link")) {
      if (enlace.textContent === activo) expect(enlace).toHaveAttribute("aria-current", "page")
      else expect(enlace).not.toHaveAttribute("aria-current")
    }
  })

  it("los enlaces llevan el anillo de foco estándar del sistema", async () => {
    montarLayout("/tramites")
    const nav = await screen.findByRole("navigation", { name: "Principal" })
    for (const enlace of within(nav).getAllByRole("link")) {
      expect(enlace).toHaveClass("outline-none", "focus-visible:ring-3", "focus-visible:ring-ring")
      expect(enlace).not.toHaveClass("focus-visible:ring-ring/50")
    }
  })

  it("el nombre y el email se muestran truncados con el email completo en title", async () => {
    montarLayout("/tramites")
    const nombre = await screen.findByText("Ana")
    expect(nombre).toHaveAttribute("title", EMAIL_LARGO)
    expect(nombre).toHaveClass("truncate", "min-w-0", "max-w-24", "md:max-w-40", "lg:max-w-none")
    expect(nombre).toHaveClass("font-medium")
    expect(nombre.className).not.toMatch(/font-bold/)
    const email = screen.getByText(EMAIL_LARGO)
    expect(email).toHaveAttribute("title", EMAIL_LARGO)
    expect(email).toHaveClass("truncate", "min-w-0")
    expect(email).toHaveClass("hidden", "md:block", "max-w-40", "lg:max-w-none")
  })

  it("las iniciales del usuario van en un cuadro decorativo (aria-hidden)", async () => {
    montarLayout("/tramites")
    await screen.findByText("Ana")
    const cuadro = within(screen.getByRole("banner")).getByText("A")
    expect(cuadro).toHaveAttribute("aria-hidden", "true")
    expect(cuadro).toHaveClass("bg-rojo-100", "text-rojo-800", "size-8")
  })

  it("cada enlace lleva su icono decorativo y su nombre accesible es solo el texto", async () => {
    montarLayout("/tramites")
    const nav = await screen.findByRole("navigation", { name: "Principal" })
    for (const nombre of ENLACES_ESPERADOS) {
      const enlace = within(nav).getByRole("link", { name: nombre })
      const iconos = enlace.querySelectorAll("svg")
      expect(iconos).toHaveLength(1)
      expect(iconos[0]).toHaveAttribute("aria-hidden", "true")
    }
    const clases = within(nav)
      .getAllByRole("link")
      .map((e) => e.querySelector("svg")?.getAttribute("class") ?? "")
    expect(clases[0]).toContain("lucide-layout-dashboard")
    expect(clases[1]).toContain("lucide-message-circle")
    expect(clases[2]).toContain("lucide-clipboard-list")
    expect(clases[3]).toContain("lucide-users")
    expect(clases[4]).toContain("lucide-warehouse")
    expect(clases[5]).toContain("lucide-calendar-clock")
    expect(clases[6]).toContain("lucide-chart-column")
    expect(clases[7]).toContain("lucide-settings")
  })

  it("la tira es la referencia de offsetLeft de sus enlaces (relative) y se desplaza por dentro", async () => {
    montarLayout("/tramites")
    const nav = await screen.findByRole("navigation", { name: "Principal" })
    expect(nav).toHaveClass("relative", "overflow-x-auto", "overscroll-x-contain", "scrollbar-oculta", "md:overflow-visible")
    expect(nav).toHaveClass("lg:flex-col", "lg:overflow-x-visible", "lg:overflow-y-auto")
  })

  it("orden de tabulación: los enlaces van antes que Cerrar sesión", async () => {
    montarLayout("/tramites")
    const nav = await screen.findByRole("navigation", { name: "Principal" })
    const salir = screen.getByRole("button", { name: "Cerrar sesión" })
    const ultimoEnlace = within(nav).getByRole("link", { name: "Configuración" })
    expect(ultimoEnlace.compareDocumentPosition(salir) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it("al navegar, ajusta el scrollLeft de la tira para mostrar el activo, sin scrollIntoView", async () => {
    const scrollIntoView = espiarScrollIntoView()

    const router = montarLayout("/tramites")
    const nav = await screen.findByRole("navigation", { name: "Principal" })

    // Índices mock: Inicio 0, Bandeja 1, Trámites 2, Ganaderos 3, Explotaciones 4 (16+i*84).
    const medidas = simularMedidas(nav, 375)
    const scrollLeftActual = medidas.scrollLeft

    // Ganaderos (268–348) cabe en 375: no se mueve.
    await act(() => router.navigate("/ganaderos"))
    expect(scrollLeftActual()).toBe(0)

    // Explotaciones (352–432) se corta a la derecha con 375 → scroll 432-375+16 = 73.
    await act(() => router.navigate("/explotaciones"))
    expect(scrollLeftActual()).toBe(73)

    // Tira más estrecha (220): hay que salir de la ruta para que el efecto se re-dispare.
    await act(() => router.navigate("/ganaderos"))
    Object.defineProperty(nav, "clientWidth", { configurable: true, value: 220 })
    await act(() => router.navigate("/explotaciones"))
    expect(scrollLeftActual()).toBe(228)

    // De vuelta a Trámites (184–264), fuera por la izquierda: 184-16 = 168.
    await act(() => router.navigate("/tramites"))
    expect(scrollLeftActual()).toBe(168)

    expect(scrollIntoView).not.toHaveBeenCalled()
  })

  it("al recibir foco un enlace cortado por la tira, ajusta el scrollLeft para mostrarlo entero (N1)", async () => {
    const scrollIntoView = espiarScrollIntoView()

    montarLayout("/explotaciones")
    const nav = await screen.findByRole("navigation", { name: "Principal" })
    const medidas = simularMedidas(nav, 220)
    // Como tras cargar /explotaciones en tira estrecha (índice 4 → 228).
    nav.scrollLeft = 228

    // Trámites (184–264) cortado por la izquierda → 184-16 = 168.
    act(() => within(nav).getByRole("link", { name: "Trámites" }).focus())
    expect(medidas.scrollLeft()).toBe(168)

    // Ganaderos (268–348) ya cabe con scroll 168 y ancho 220: no mueve.
    act(() => within(nav).getByRole("link", { name: "Ganaderos" }).focus())
    expect(medidas.scrollLeft()).toBe(168)

    // Explotaciones (352–432) cortado a la derecha → 228.
    act(() => within(nav).getByRole("link", { name: "Explotaciones" }).focus())
    expect(medidas.scrollLeft()).toBe(228)

    expect(scrollIntoView).not.toHaveBeenCalled()
  })

  it("al cambiar de tamaño la tira (giro, ventana, fuente tardía) re-ajusta el scrollLeft, solo si hace falta", async () => {
    vi.stubGlobal("ResizeObserver", ResizeObserverFalso)
    const scrollIntoView = espiarScrollIntoView()

    const router = montarLayout("/tramites")
    const nav = await screen.findByRole("navigation", { name: "Principal" })
    await act(() => router.navigate("/explotaciones"))
    const medidas = simularMedidas(nav, 500)

    const observador = ResizeObserverFalso.instancias.at(-1)!
    const explotaciones = within(nav).getByRole("link", { name: "Explotaciones" })
    expect(observador.observados.has(nav)).toBe(true)
    expect(observador.observados.has(explotaciones)).toBe(true)

    // Con 500 px Explotaciones (352–432) se ve entera: no mueve.
    act(() => observador.disparar())
    expect(medidas.scrollLeft()).toBe(0)

    // Tira a 220 px: Explotaciones cortada → 228.
    Object.defineProperty(nav, "clientWidth", { configurable: true, value: 220 })
    act(() => observador.disparar())
    expect(medidas.scrollLeft()).toBe(228)
    expect(scrollIntoView).not.toHaveBeenCalled()
  })

  it("el ResizeObserver se desconecta al cambiar de ruta y al desmontar", async () => {
    vi.stubGlobal("ResizeObserver", ResizeObserverFalso)
    const router = montarLayout("/tramites")
    await screen.findByRole("navigation", { name: "Principal" })
    const primero = ResizeObserverFalso.instancias.at(-1)!

    await act(() => router.navigate("/ganaderos"))
    expect(primero.desconectado).toBe(true)
    const segundo = ResizeObserverFalso.instancias.at(-1)!
    expect(segundo).not.toBe(primero)
    expect(segundo.observados.has(within(screen.getByRole("navigation")).getByRole("link", { name: "Ganaderos" }))).toBe(true)

    cleanup()
    expect(segundo.desconectado).toBe(true)
  })

  it("sin ResizeObserver (navegador antiguo) la barra funciona igual", async () => {
    vi.stubGlobal("ResizeObserver", undefined)
    const router = montarLayout("/tramites")
    await screen.findByRole("navigation", { name: "Principal" })
    await act(() => router.navigate("/explotaciones"))
    expect(within(screen.getByRole("navigation")).getByRole("link", { name: "Explotaciones" })).toHaveAttribute("aria-current", "page")
  })
})
