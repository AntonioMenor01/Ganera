import { act, render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { afterEach, describe, expect, it, vi } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { ExplotacionesPage } from "./ExplotacionesPage"

// Revisión de la Task 8: I1 (móvil sin scroll lateral) y m4 (Reintentar e importar pliegan).

function explotacion(id: number) {
  return { id, codigoRega: `ES00${id}`, nombre: `Finca ${id}`, ganaderoId: 1, nombreGanadero: "Ana" }
}

function listado(ids: number[]) {
  return HttpResponse.json({ content: ids.map(explotacion), totalElements: ids.length, totalPages: 1, number: 0, size: 20 })
}

function servirAnimales() {
  server.use(
    http.get(apiUrl("/explotaciones/:id/animales"), ({ params }) =>
      HttpResponse.json({
        content: [{ id: 1, crotal: `ES01000000${params.id}999`, crotalUltimosDigitos: `00${params.id}999` }],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
      }),
    ),
  )
}

function montar() {
  const router = createMemoryRouter([{ path: "/explotaciones", element: <ExplotacionesPage /> }], {
    initialEntries: ["/explotaciones"],
  })
  render(<RouterProvider router={router} />)
}

function filaDe(codigoRega: string): HTMLTableRowElement {
  return screen.getByText(codigoRega).closest("tr")!
}

function resumenVacio() {
  const hoja = { filasProcesadas: 0, creadas: 0, actualizadas: 0 }
  return { explotaciones: hoja, animales: hoja, contactos: hoja, errores: [] }
}

async function subirExcel(user: ReturnType<typeof userEvent.setup>) {
  const input = document.querySelector<HTMLInputElement>('input[type="file"]')!
  await user.upload(input, new File(["x"], "inventario.xlsx"))
}

async function abrirPanel(user: ReturnType<typeof userEvent.setup>, codigoRega: string) {
  await user.click(screen.getByRole("button", { name: `Ver animales de ${codigoRega}` }))
  await screen.findByRole("list", { name: "Crotales" })
}

/** matchMedia simulado para `(min-width: 40rem)` (el `sm` de Tailwind). jsdom no trae matchMedia ni
 * hace maquetación; `cambiar` simula girar o redimensionar la pantalla. */
function simularAncho(desdeSm: boolean) {
  const oyentes = new Set<() => void>()
  let coincide = desdeSm
  vi.stubGlobal(
    "matchMedia",
    vi.fn((media: string) => ({
      media,
      get matches() {
        return coincide
      },
      addEventListener: (_tipo: string, oyente: () => void) => oyentes.add(oyente),
      removeEventListener: (_tipo: string, oyente: () => void) => oyentes.delete(oyente),
    })),
  )
  return {
    cambiar(nuevo: boolean) {
      coincide = nuevo
      act(() => oyentes.forEach((oyente) => oyente()))
    },
  }
}

function cabeceras() {
  return screen.getAllByRole("columnheader").map((th) => th.textContent)
}

function celdaDelPanel(codigoRega: string) {
  const boton = screen.getByRole("button", { name: `Ver animales de ${codigoRega}` })
  return document.getElementById(boton.getAttribute("aria-controls")!)!.closest("td")!
}

describe("ExplotacionesPage: móvil sin scroll lateral (I1)", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("por debajo de sm: sin columna Nombre; el nombre va una sola vez, en gris bajo el REGA, y el panel ocupa 3", async () => {
    simularAncho(false)
    server.use(http.get(apiUrl("/explotaciones"), () => listado([1, 2, 3])))
    servirAnimales()
    const user = userEvent.setup()
    montar()

    await screen.findByText("ES002")
    expect(window.matchMedia).toHaveBeenCalledWith("(min-width: 40rem)")
    expect(cabeceras()).toEqual(["Código REGA", "Ganadero", "Animales"])

    const fila = filaDe("ES002")
    expect(fila.cells).toHaveLength(3)
    const nombre = within(fila.cells[0]).getByText("Finca 2")
    expect(nombre).toHaveClass("block", "text-muted-foreground")
    expect(nombre.previousElementSibling).toHaveTextContent(/^ES002$/)
    // Una sola vez en el DOM: nada duplicado para el lector de pantalla.
    expect(screen.getAllByText("Finca 2")).toHaveLength(1)

    await abrirPanel(user, "ES002")
    expect(celdaDelPanel("ES002")).toHaveAttribute("colspan", "3")
  })

  it("desde sm: columna Nombre propia, el nombre una sola vez y el panel a 4 columnas", async () => {
    simularAncho(true)
    server.use(http.get(apiUrl("/explotaciones"), () => listado([1, 2, 3])))
    servirAnimales()
    const user = userEvent.setup()
    montar()

    await screen.findByText("ES002")
    expect(cabeceras()).toEqual(["Código REGA", "Nombre", "Ganadero", "Animales"])
    const fila = filaDe("ES002")
    expect(fila.cells).toHaveLength(4)
    expect(fila.cells[0]).toHaveTextContent(/^ES002$/)
    expect(fila.cells[1]).toHaveTextContent(/^Finca 2$/)
    expect(screen.getAllByText("Finca 2")).toHaveLength(1)

    await abrirPanel(user, "ES002")
    expect(celdaDelPanel("ES002")).toHaveAttribute("colspan", "4")
  })

  it("al cruzar el punto de corte se reajustan la tabla y el panel abierto, sin plegarlo ni volver a pedirlo", async () => {
    const pantalla = simularAncho(true)
    const peticionesAnimales: string[] = []
    server.use(http.get(apiUrl("/explotaciones"), () => listado([1, 2, 3])))
    servirAnimales()
    const escuchar = ({ request }: { request: Request }) => {
      if (request.url.includes("/animales")) peticionesAnimales.push(request.url)
    }
    server.events.on("request:start", escuchar)
    const user = userEvent.setup()
    try {
      montar()
      await screen.findByText("ES002")
      await abrirPanel(user, "ES002")

      pantalla.cambiar(false)
      expect(cabeceras()).toHaveLength(3)
      expect(celdaDelPanel("ES002")).toHaveAttribute("colspan", "3")
      expect(screen.getByRole("list", { name: "Crotales" })).toBeInTheDocument()

      pantalla.cambiar(true)
      expect(cabeceras()).toHaveLength(4)
      expect(celdaDelPanel("ES002")).toHaveAttribute("colspan", "4")
      expect(peticionesAnimales).toHaveLength(1)
    } finally {
      server.events.removeListener("request:start", escuchar)
    }
  })

  it("sin matchMedia (navegador antiguo), la tabla completa de escritorio", async () => {
    vi.stubGlobal("matchMedia", undefined)
    server.use(http.get(apiUrl("/explotaciones"), () => listado([1])))
    montar()
    await screen.findByText("ES001")
    expect(cabeceras()).toEqual(["Código REGA", "Nombre", "Ganadero", "Animales"])
  })
})

describe("ExplotacionesPage: recargar el listado pliega los paneles (m4)", () => {
  it("importar un Excel pliega los paneles abiertos y vuelve a pedir el listado", async () => {
    let listados = 0
    server.use(
      http.get(apiUrl("/explotaciones"), () => {
        listados += 1
        return listado([1, 2, 3])
      }),
      http.post(apiUrl("/explotaciones/importar"), () => HttpResponse.json(resumenVacio())),
    )
    servirAnimales()
    const user = userEvent.setup()
    montar()

    await screen.findByText("ES001")
    await abrirPanel(user, "ES001")
    expect(listados).toBe(1)

    await subirExcel(user)

    await waitFor(() => expect(listados).toBe(2))
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Ver animales de ES001" })).toHaveAttribute("aria-expanded", "false"),
    )
    expect(screen.queryByRole("list", { name: "Crotales" })).not.toBeInTheDocument()
  })

  it("si la recarga falla, Reintentar deja el listado con los paneles plegados", async () => {
    let listados = 0
    server.use(
      http.get(apiUrl("/explotaciones"), () => {
        listados += 1
        // La recarga tras importar (la 2.ª) falla; Reintentar (la 3.ª) funciona.
        if (listados === 2) return new HttpResponse(null, { status: 500 })
        return listado([1, 2, 3])
      }),
      http.post(apiUrl("/explotaciones/importar"), () => HttpResponse.json(resumenVacio())),
    )
    servirAnimales()
    const user = userEvent.setup()
    montar()

    await screen.findByText("ES001")
    await abrirPanel(user, "ES001")
    await subirExcel(user)
    await user.click(await screen.findByRole("button", { name: "Reintentar" }))

    await screen.findByText("ES001")
    expect(listados).toBe(3)
    expect(screen.getByRole("button", { name: "Ver animales de ES001" })).toHaveAttribute("aria-expanded", "false")
    expect(screen.queryByRole("list", { name: "Crotales" })).not.toBeInTheDocument()
  })
})
