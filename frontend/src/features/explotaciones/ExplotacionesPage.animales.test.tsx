import { render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { describe, expect, it } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { ExplotacionesPage } from "./ExplotacionesPage"

function explotacion(id: number) {
  return { id, codigoRega: `ES00${id}`, nombre: `Finca ${id}`, ganaderoId: 1, nombreGanadero: "Ana" }
}

function montar() {
  const router = createMemoryRouter([{ path: "/explotaciones", element: <ExplotacionesPage /> }], {
    initialEntries: ["/explotaciones"],
  })
  render(<RouterProvider router={router} />)
}

/** Sirve el listado (paginado según `totalPages`) y los animales de cada explotación; apunta las
 * rutas de animales pedidas. */
function servir({ totalPages = 1 } = {}) {
  const peticionesAnimales: string[] = []
  server.use(
    http.get(apiUrl("/explotaciones"), ({ request }) => {
      const page = Number(new URL(request.url).searchParams.get("page"))
      const ids = page === 0 ? [1, 2, 3] : [4]
      return HttpResponse.json({
        content: ids.map(explotacion),
        totalElements: 4,
        totalPages,
        number: page,
        size: 20,
      })
    }),
    http.get(apiUrl("/explotaciones/:id/animales"), ({ request, params }) => {
      peticionesAnimales.push(new URL(request.url).pathname)
      return HttpResponse.json({
        content: [
          {
            id: Number(params.id) * 100,
            crotal: `ES01000000${params.id}999`,
            crotalUltimosDigitos: `00${params.id}999`,
          },
        ],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
      })
    }),
  )
  return peticionesAnimales
}

function filaDe(codigoRega: string): HTMLElement {
  return screen.getByRole("cell", { name: codigoRega }).closest("tr")!
}

describe("ExplotacionesPage: 'Ver animales' por fila (H1-A)", () => {
  it("no pide animales hasta desplegar, y al hacerlo pide solo los de esa explotación, una vez", async () => {
    const peticiones = servir()
    const user = userEvent.setup()
    montar()

    await screen.findByText("ES002")
    const botones = screen.getAllByRole("button", { name: /^Ver animales/ })
    expect(botones).toHaveLength(3)
    botones.forEach((boton) => {
      expect(boton).toHaveAttribute("aria-expanded", "false")
      expect(boton).not.toHaveAttribute("aria-controls")
    })
    expect(peticiones).toEqual([])

    await user.click(screen.getByRole("button", { name: "Ver animales de ES002" }))
    await screen.findByRole("list", { name: "Crotales" })
    expect(peticiones).toEqual(["/explotaciones/2/animales"])
  })

  it("alterna aria-expanded y pinta el panel en la fila justo debajo de la suya, a todo el ancho", async () => {
    servir()
    const user = userEvent.setup()
    montar()

    await screen.findByText("ES002")
    const boton = screen.getByRole("button", { name: "Ver animales de ES002" })
    await user.click(boton)
    expect(boton).toHaveAttribute("aria-expanded", "true")

    const panel = document.getElementById(boton.getAttribute("aria-controls")!)!
    expect(panel).toBeInTheDocument()
    const filaPanel = panel.closest("tr")!
    expect(filaDe("ES002").nextElementSibling).toBe(filaPanel)
    const celda = within(filaPanel).getByRole("cell")
    const columnas = screen.getAllByRole("columnheader").length
    expect(celda).toHaveAttribute("colspan", String(columnas))
    expect(await within(panel).findByRole("list", { name: "Crotales" })).toHaveTextContent(
      "ES010000002999",
    )

    await user.click(boton)
    expect(boton).toHaveAttribute("aria-expanded", "false")
    expect(boton).not.toHaveAttribute("aria-controls")
    expect(filaDe("ES002").nextElementSibling).toBe(filaDe("ES003"))
    expect(screen.queryByRole("list", { name: "Crotales" })).not.toBeInTheDocument()
  })

  it("se pueden tener varias abiertas a la vez, cada una con lo suyo", async () => {
    const peticiones = servir()
    const user = userEvent.setup()
    montar()

    await screen.findByText("ES001")
    await user.click(screen.getByRole("button", { name: "Ver animales de ES001" }))
    await user.click(screen.getByRole("button", { name: "Ver animales de ES003" }))

    await waitFor(() => expect(screen.getAllByRole("list", { name: "Crotales" })).toHaveLength(2))
    expect(filaDe("ES001").nextElementSibling).toHaveTextContent("ES010000001999")
    expect(filaDe("ES003").nextElementSibling).toHaveTextContent("ES010000003999")
    expect(screen.getByRole("button", { name: "Ver animales de ES002" })).toHaveAttribute(
      "aria-expanded",
      "false",
    )
    expect([...peticiones].sort()).toEqual(["/explotaciones/1/animales", "/explotaciones/3/animales"])
  })

  it("cambiar de página del listado pliega los paneles abiertos", async () => {
    servir({ totalPages: 2 })
    const user = userEvent.setup()
    montar()

    await screen.findByText("ES001")
    await user.click(screen.getByRole("button", { name: "Ver animales de ES001" }))
    await screen.findByRole("list", { name: "Crotales" })

    await user.click(screen.getByRole("button", { name: "Siguiente" }))
    await screen.findByText("ES004")
    await user.click(screen.getByRole("button", { name: "Anterior" }))
    await screen.findByText("ES001")
    expect(screen.getByRole("button", { name: "Ver animales de ES001" })).toHaveAttribute(
      "aria-expanded",
      "false",
    )
    expect(screen.queryByRole("list", { name: "Crotales" })).not.toBeInTheDocument()
  })
})

describe("ExplotacionesPage: tabla y paginación (Task 10)", () => {
  it("la celda del panel se asocia a la cabecera «Animales», no a «Código REGA»", async () => {
    servir()
    const user = userEvent.setup()
    montar()

    await screen.findByText("ES002")
    const boton = screen.getByRole("button", { name: "Ver animales de ES002" })
    await user.click(boton)
    const celda = document.getElementById(boton.getAttribute("aria-controls")!)!.closest("td")!
    const idCabecera = celda.getAttribute("headers")
    expect(idCabecera).toBeTruthy()
    expect(document.getElementById(idCabecera!)).toHaveTextContent("Animales")
    expect(document.getElementById(idCabecera!)?.tagName).toBe("TH")
  })

  it("con más de una página, la paginación es un nav con nombre y cifras tabulares", async () => {
    servir({ totalPages: 2 })
    montar()

    await screen.findByText("ES001")
    const nav = screen.getByRole("navigation", { name: "Paginación" })
    expect(within(nav).getByText("Página 1 de 2")).toHaveClass("tabular-nums")
    expect(within(nav).getByRole("button", { name: "Siguiente" })).toBeInTheDocument()
  })

  it("el código REGA usa cifras tabulares", async () => {
    servir()
    montar()

    expect(await screen.findByText("ES002")).toHaveClass("tabular-nums")
  })
})
