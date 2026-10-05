import { act, cleanup, render, screen, within } from "@testing-library/react"
import { http, HttpResponse } from "msw"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { AuthProvider } from "@/shared/auth/AuthContext"
import { AppLayout } from "@/shared/layout/AppLayout"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"

const EMAIL_LARGO = "administracion.gestoria.ejemplo@agroservicios-castilla.es"

/** El layout real con páginas de relleno: lo que se prueba es la barra, no las pantallas. */
function montarLayout(url: string) {
  const relleno = (texto: string) => <p>{texto}</p>
  const router = createMemoryRouter(
    [
      {
        path: "/",
        element: <AppLayout />,
        children: [
          { path: "tramites", element: relleno("pantalla tramites") },
          { path: "ganaderos", element: relleno("pantalla ganaderos") },
          { path: "explotaciones", element: relleno("pantalla explotaciones") },
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
  it("hay un solo nav, llamado Principal, con los 3 enlaces en orden de trabajo (sin Facturación)", async () => {
    montarLayout("/tramites")
    await screen.findByText("pantalla tramites")

    const navs = screen.getAllByRole("navigation")
    expect(navs).toHaveLength(1)
    const nav = screen.getByRole("navigation", { name: "Principal" })
    expect(within(nav).getAllByRole("link").map((e) => e.textContent)).toEqual([
      "Trámites",
      "Ganaderos",
      "Explotaciones",
    ])
    expect(within(nav).queryByRole("link", { name: /facturaci/i })).not.toBeInTheDocument()
    expect(within(nav).getAllByRole("link").map((e) => e.getAttribute("href"))).toEqual([
      "/tramites",
      "/ganaderos",
      "/explotaciones",
    ])
  })

  it.each([
    ["/tramites", "Trámites"],
    ["/ganaderos", "Ganaderos"],
    ["/explotaciones", "Explotaciones"],
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

  it("el email se muestra truncado con el email completo en title", async () => {
    montarLayout("/tramites")
    const email = await screen.findByText(EMAIL_LARGO)
    expect(email).toHaveAttribute("title", EMAIL_LARGO)
    expect(email).toHaveClass("truncate", "min-w-0")
    // Oculto por debajo de md; desde md, con el ancho máximo calculado para 768 px (112 px).
    expect(email).toHaveClass("hidden", "md:block", "md:max-w-28", "lg:max-w-xs")
  })

  it("la tira es la referencia de offsetLeft de sus enlaces (relative) y se desplaza por dentro", async () => {
    montarLayout("/tramites")
    const nav = await screen.findByRole("navigation", { name: "Principal" })
    expect(nav).toHaveClass("relative", "overflow-x-auto", "overscroll-x-contain", "scrollbar-oculta", "md:overflow-visible")
  })

  it("orden de tabulación: los enlaces van antes que Salir", async () => {
    montarLayout("/tramites")
    const nav = await screen.findByRole("navigation", { name: "Principal" })
    const salir = screen.getByRole("button", { name: "Salir" })
    const ultimoEnlace = within(nav).getByRole("link", { name: "Explotaciones" })
    expect(ultimoEnlace.compareDocumentPosition(salir) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it("al navegar, ajusta el scrollLeft de la tira para mostrar el activo, sin scrollIntoView", async () => {
    const scrollIntoView = espiarScrollIntoView()

    const router = montarLayout("/tramites")
    const nav = await screen.findByRole("navigation", { name: "Principal" })

    // Medidas de una tira de 375 px: margen interior 16, enlaces de 80 px con 4 px de separación.
    const medidas = simularMedidas(nav, 375)
    const scrollLeftActual = medidas.scrollLeft

    // Ganaderos (100–180) ya se ve: la tira no se mueve.
    await act(() => router.navigate("/ganaderos"))
    expect(scrollLeftActual()).toBe(0)

    // Explotaciones (184–264)... con 375 de ancho y margen 16 cabe hasta 359: tampoco se mueve.
    // Se estrecha la tira para que Explotaciones quede cortada por la derecha (visible hasta 204).
    Object.defineProperty(nav, "clientWidth", { configurable: true, value: 220 })
    await act(() => router.navigate("/explotaciones"))
    // fin 264 → 264 - 220 + 16 = 60
    expect(scrollLeftActual()).toBe(60)

    // De vuelta a Trámites (16–96), fuera por la izquierda: vuelve al principio.
    await act(() => router.navigate("/tramites"))
    expect(scrollLeftActual()).toBe(0)

    expect(scrollIntoView).not.toHaveBeenCalled()
  })

  it("al recibir foco un enlace cortado por la tira, ajusta el scrollLeft para mostrarlo entero (N1)", async () => {
    const scrollIntoView = espiarScrollIntoView()

    montarLayout("/explotaciones")
    const nav = await screen.findByRole("navigation", { name: "Principal" })
    const medidas = simularMedidas(nav, 220)
    // La tira desplazada para enseñar Explotaciones (como tras cargar /explotaciones en una tira estrecha).
    nav.scrollLeft = 60

    // Trámites (16–96) queda cortado por la izquierda: al enfocarlo, la tira vuelve al principio.
    act(() => within(nav).getByRole("link", { name: "Trámites" }).focus())
    expect(medidas.scrollLeft()).toBe(0)

    // Ganaderos (100–180) ya se ve entero con el margen: enfocarlo no mueve la tira.
    act(() => within(nav).getByRole("link", { name: "Ganaderos" }).focus())
    expect(medidas.scrollLeft()).toBe(0)

    // Explotaciones (184–264), cortado por la derecha (visible hasta 204): 264 - 220 + 16 = 60.
    act(() => within(nav).getByRole("link", { name: "Explotaciones" }).focus())
    expect(medidas.scrollLeft()).toBe(60)

    expect(scrollIntoView).not.toHaveBeenCalled()
  })

  it("al cambiar de tamaño la tira (giro, ventana, fuente tardía) re-ajusta el scrollLeft, solo si hace falta", async () => {
    vi.stubGlobal("ResizeObserver", ResizeObserverFalso)
    const scrollIntoView = espiarScrollIntoView()

    const router = montarLayout("/tramites")
    const nav = await screen.findByRole("navigation", { name: "Principal" })
    await act(() => router.navigate("/explotaciones"))
    const medidas = simularMedidas(nav, 375)

    const observador = ResizeObserverFalso.instancias.at(-1)!
    const explotaciones = within(nav).getByRole("link", { name: "Explotaciones" })
    expect(observador.observados.has(nav)).toBe(true)
    expect(observador.observados.has(explotaciones)).toBe(true)

    // Con 375 px Explotaciones (184–264) se ve entera: el aviso de tamaño no mueve la tira.
    act(() => observador.disparar())
    expect(medidas.scrollLeft()).toBe(0)

    // La tira se estrecha a 220 px: Explotaciones queda cortada y se trae a la vista (264 - 220 + 16).
    Object.defineProperty(nav, "clientWidth", { configurable: true, value: 220 })
    act(() => observador.disparar())
    expect(medidas.scrollLeft()).toBe(60)
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
