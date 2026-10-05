import { act, fireEvent, render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { StrictMode, useLayoutEffect } from "react"
import { createMemoryRouter, RouterProvider, useLocation } from "react-router-dom"
import { describe, expect, it, vi } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { GanaderoDetallePage } from "./GanaderoDetallePage"

function explotacion(id: number, contactos: unknown[] = []) {
  return { id, codigoRega: `ES28079000${id}`, nombre: `Finca ${id}`, contactos }
}

const CONTACTOS = [
  { contactoId: 1, nombre: "Ana Ruiz", telefono: "+34612345678", rol: "TITULAR" },
  { contactoId: 2, nombre: "Luis Peón", telefono: "+447911123456", rol: "EMPLEADO" },
]

function ganadero(explotaciones: unknown[]) {
  return { id: 7, nombre: "Ana Ruiz Gómez", nif: "12345678Z", explotaciones }
}

function responderCon(respuesta: () => Response) {
  let llamadas = 0
  server.use(
    http.get(apiUrl("/ganaderos/:id"), () => {
      llamadas += 1
      return respuesta()
    }),
  )
  return () => llamadas
}

function montar(url = "/ganaderos/7") {
  const router = createMemoryRouter(
    [
      { path: "/ganaderos", element: <p>Listado de ganaderos</p> },
      { path: "/ganaderos/:id", element: <GanaderoDetallePage /> },
    ],
    { initialEntries: [url] },
  )
  render(<RouterProvider router={router} />)
  return router
}

describe("GanaderoDetallePage: ficha", () => {
  it("cabecera: nombre como título, NIF tabular al lado y nº de explotaciones", async () => {
    responderCon(() => HttpResponse.json(ganadero([explotacion(1, CONTACTOS), explotacion(2)])))
    montar()

    expect(await screen.findByRole("heading", { level: 1, name: "Ana Ruiz Gómez" })).toBeInTheDocument()
    expect(screen.getByText("12345678Z")).toHaveClass("tabular-nums")
    expect(screen.getByText("2 explotaciones")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Ganaderos" })).toHaveAttribute("href", "/ganaderos")
  })

  it("una sección por explotación, con REGA y nombre en su cabecera", async () => {
    responderCon(() => HttpResponse.json(ganadero([explotacion(1, CONTACTOS), explotacion(2)])))
    montar()

    const secciones = await screen.findAllByRole("region", { name: /^ES28079000/ })
    expect(secciones).toHaveLength(2)
    const primera = secciones[0]
    const titulo = within(primera).getByRole("heading", { level: 2 })
    expect(titulo).toHaveTextContent("ES280790001")
    expect(titulo).toHaveTextContent("Finca 1")
    expect(within(titulo).getByText("ES280790001")).toHaveClass("tabular-nums")
  })

  it("contactos con nombre, teléfono tel: exacto en E.164 y badge de rol", async () => {
    responderCon(() => HttpResponse.json(ganadero([explotacion(1, CONTACTOS)])))
    montar()

    const seccion = await screen.findByRole("region", { name: /ES280790001/ })
    const lista = within(seccion).getByRole("list", { name: "Contactos" })
    const items = within(lista).getAllByRole("listitem")
    expect(items).toHaveLength(2)

    const ana = items[0]
    expect(within(ana).getByText("Ana Ruiz")).toBeInTheDocument()
    const telAna = within(ana).getByRole("link", { name: /612 345 678/ })
    expect(telAna).toHaveAttribute("href", "tel:+34612345678")
    expect(telAna).toHaveClass("tabular-nums")
    expect(within(ana).getByText("Titular")).toHaveClass("bg-success")

    const luis = items[1]
    expect(within(luis).getByRole("link", { name: /\+447911123456/ })).toHaveAttribute(
      "href",
      "tel:+447911123456",
    )
    expect(within(luis).getByText("Empleado")).toHaveClass("border-border")
    expect(within(seccion).queryByText("TITULAR")).not.toBeInTheDocument()
  })

  it("una explotación sin contactos activos lo dice", async () => {
    responderCon(() => HttpResponse.json(ganadero([explotacion(1)])))
    montar()

    const seccion = await screen.findByRole("region", { name: /ES280790001/ })
    expect(within(seccion).getByText("Sin contactos activos en esta explotación.")).toBeInTheDocument()
  })

  it("un ganadero sin explotaciones lo dice", async () => {
    responderCon(() => HttpResponse.json(ganadero([])))
    montar()

    expect(
      await screen.findByText("Este ganadero no tiene explotaciones. Se añaden al importar el Excel."),
    ).toBeInTheDocument()
    expect(screen.getByText("0 explotaciones")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Ir a Explotaciones" })).toHaveAttribute("href", "/explotaciones")
  })

  it("sin explotaciones, un clic real en «Ir a Explotaciones» lleva a /explotaciones", async () => {
    responderCon(() => HttpResponse.json(ganadero([])))
    const router = createMemoryRouter(
      [
        { path: "/ganaderos/:id", element: <GanaderoDetallePage /> },
        { path: "/explotaciones", element: <p>Pantalla de explotaciones</p> },
      ],
      { initialEntries: ["/ganaderos/7"] },
    )
    render(<RouterProvider router={router} />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole("link", { name: "Ir a Explotaciones" }))

    expect(await screen.findByText("Pantalla de explotaciones")).toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/explotaciones")
  })

  it("1 explotación en singular", async () => {
    responderCon(() => HttpResponse.json(ganadero([explotacion(1)])))
    montar()
    expect(await screen.findByText("1 explotación")).toBeInTheDocument()
  })

  it("un ganadero sin NIF no deja un hueco raro", async () => {
    responderCon(() => HttpResponse.json({ ...ganadero([]), nif: null }))
    montar()
    await screen.findByRole("heading", { level: 1, name: "Ana Ruiz Gómez" })
    expect(screen.getByText("Sin NIF")).toBeInTheDocument()
  })

  it("no enseña nada de OVZ aunque la respuesta trajera campos de más", async () => {
    responderCon(() =>
      HttpResponse.json({
        ...ganadero([explotacion(1, CONTACTOS)]),
        ovzUsuario: "usuario-ovz-secreto",
        ovzPasswordCifrada: "xyz",
      }),
    )
    montar()
    await screen.findByRole("heading", { level: 1, name: "Ana Ruiz Gómez" })
    expect(document.body.textContent).not.toMatch(/ovz|usuario-ovz-secreto|xyz/i)
  })
})

describe("GanaderoDetallePage: índice de explotaciones", () => {
  it("con 3 explotaciones o menos no hay índice", async () => {
    responderCon(() => HttpResponse.json(ganadero([explotacion(1), explotacion(2), explotacion(3)])))
    montar()

    await screen.findAllByRole("region", { name: /^ES28079000/ })
    expect(screen.queryByRole("navigation", { name: "Explotaciones de este ganadero" })).not.toBeInTheDocument()
  })

  it("con más de 3, un índice de enlaces REGA · nombre a cada sección, que lleva el foco a su título", async () => {
    const lista = [explotacion(1), explotacion(2), explotacion(3), explotacion(4)]
    responderCon(() => HttpResponse.json(ganadero(lista)))
    const user = userEvent.setup()
    montar()

    const indice = await screen.findByRole("navigation", { name: "Explotaciones de este ganadero" })
    const enlaces = within(indice).getAllByRole("link")
    expect(enlaces).toHaveLength(4)
    expect(enlaces[2]).toHaveTextContent("ES280790003 · Finca 3")

    const secciones = screen.getAllByRole("region", { name: /^ES28079000/ })
    enlaces.forEach((enlace, i) => {
      expect(enlace).toHaveAttribute("href", `#${secciones[i].id}`)
    })

    await user.click(enlaces[2])
    const tituloTercera = within(secciones[2]).getByRole("heading", { level: 2 })
    expect(tituloTercera).toHaveFocus()
  })
})

describe("GanaderoDetallePage: animales plegados", () => {
  it("'Ver animales' está plegado por defecto y alterna aria-expanded", async () => {
    responderCon(() => HttpResponse.json(ganadero([explotacion(1), explotacion(2)])))
    // Task 8: desplegar monta el panel real, que pide los animales (aquí, ninguno).
    server.use(
      http.get(apiUrl("/explotaciones/:id/animales"), () =>
        HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
      ),
    )
    const user = userEvent.setup()
    montar()

    const seccion = (await screen.findAllByRole("region", { name: /^ES28079000/ }))[0]
    const boton = within(seccion).getByRole("button", { name: "Ver animales" })
    expect(boton).toHaveAttribute("aria-expanded", "false")
    const panel = document.getElementById(boton.getAttribute("aria-controls")!)!
    expect(panel).toBeInTheDocument()
    expect(panel).not.toBeVisible()

    await user.click(boton)
    expect(boton).toHaveAttribute("aria-expanded", "true")
    expect(panel).toBeVisible()

    // Cada sección tiene su propio panel.
    const otroBoton = within(screen.getAllByRole("region", { name: /^ES28079000/ })[1]).getByRole("button", {
      name: "Ver animales",
    })
    expect(otroBoton).toHaveAttribute("aria-expanded", "false")
    expect(otroBoton.getAttribute("aria-controls")).not.toBe(boton.getAttribute("aria-controls"))

    await user.click(boton)
    expect(boton).toHaveAttribute("aria-expanded", "false")
    expect(panel).not.toBeVisible()
  })
})

describe("GanaderoDetallePage: errores", () => {
  it("un 404 es 'Ganadero no encontrado' con enlace a la lista, nunca un error genérico", async () => {
    responderCon(() => new HttpResponse(null, { status: 404 }))
    const user = userEvent.setup()
    montar()

    expect(await screen.findByRole("heading", { level: 1, name: "Ganadero no encontrado" })).toBeInTheDocument()
    expect(screen.getByText("Este ganadero no existe o no es de tu gestoría.")).toBeInTheDocument()
    expect(screen.queryByRole("alert")).not.toBeInTheDocument()
    // Una sola acción para volver: el enlace "← Ganaderos" de arriba no se repite aquí.
    expect(screen.getAllByRole("link")).toHaveLength(1)
    expect(screen.queryByRole("link", { name: "Ganaderos" })).not.toBeInTheDocument()
    await user.click(screen.getByRole("link", { name: "Volver a Ganaderos" }))
    expect(await screen.findByText("Listado de ganaderos")).toBeInTheDocument()
  })

  it.each(["abc", "7x", "0", "-3", "1.5"])("un id no válido (%s) es 'no encontrado' sin pedir nada", async (id) => {
    const llamadas = responderCon(() => HttpResponse.json(ganadero([])))
    montar(`/ganaderos/${id}`)

    expect(await screen.findByRole("heading", { level: 1, name: "Ganadero no encontrado" })).toBeInTheDocument()
    expect(llamadas()).toBe(0)
  })

  it("otro error enseña una alerta con Reintentar, que vuelve a pedirlo", async () => {
    let llamadas = 0
    responderCon(() => {
      llamadas += 1
      return llamadas === 1 ? HttpResponse.error() : HttpResponse.json(ganadero([explotacion(1)]))
    })
    const user = userEvent.setup()
    montar()

    const alerta = await screen.findByRole("alert")
    expect(within(alerta).getByText("No se ha podido cargar el ganadero")).toBeInTheDocument()
    expect(alerta.firstElementChild?.matches('svg[aria-hidden="true"]')).toBe(true)
    expect(
      within(alerta).getByText("No se ha podido conectar con Ganera. Inténtalo de nuevo en unos segundos."),
    ).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Ganaderos" })).toBeInTheDocument()

    await user.click(within(alerta).getByRole("button", { name: "Reintentar" }))
    expect(await screen.findByRole("heading", { level: 1, name: "Ana Ruiz Gómez" })).toBeInTheDocument()
    expect(screen.queryByRole("alert")).not.toBeInTheDocument()
  })

  it("mientras carga, lo anuncia sin enseñar datos", async () => {
    let abrir!: () => void
    const promesa = new Promise<void>((resolve) => {
      abrir = resolve
    })
    server.use(
      http.get(apiUrl("/ganaderos/:id"), async () => {
        await promesa
        return HttpResponse.json(ganadero([]))
      }),
    )
    montar()

    expect(await screen.findByText("Cargando ganadero…")).toBeInTheDocument()
    abrir()
    expect(await screen.findByRole("heading", { level: 1, name: "Ana Ruiz Gómez" })).toBeInTheDocument()
    expect(screen.queryByText("Cargando ganadero…")).not.toBeInTheDocument()
  })
})

/** Respuesta de MSW que el test suelta cuando quiere. */
function puerta() {
  let abrir!: () => void
  const promesa = new Promise<void>((resolve) => {
    abrir = resolve
  })
  return { promesa, abrir }
}

function ganaderoConId(id: number) {
  return { id, nombre: `Ganadero ${id}`, nif: null, explotaciones: [] }
}

/** Anota, en cada commit (antes de que corran los efectos pasivos), qué ficha se ve en qué URL. */
function Sonda({ registro }: { registro: string[] }) {
  const location = useLocation()
  useLayoutEffect(() => {
    const titulo = document.querySelector("h1")?.textContent ?? "(sin título)"
    registro.push(`${location.pathname} => ${titulo}`)
  })
  return null
}

function montarConSonda(url: string, registro: string[]) {
  const router = createMemoryRouter(
    [
      {
        path: "/ganaderos/:id",
        element: (
          <>
            <GanaderoDetallePage />
            <Sonda registro={registro} />
          </>
        ),
      },
    ],
    { initialEntries: [url] },
  )
  render(<RouterProvider router={router} />)
  return router
}

describe("GanaderoDetallePage: cambio de ganadero en la misma pantalla (I1)", () => {
  it("de /ganaderos/1 a /ganaderos/2 nunca se pinta la ficha de 1 bajo la URL de 2", async () => {
    const puerta2 = puerta()
    server.use(
      http.get(apiUrl("/ganaderos/:id"), async ({ params }) => {
        const id = Number(params.id)
        if (id === 2) await puerta2.promesa
        return HttpResponse.json(ganaderoConId(id))
      }),
    )
    const registro: string[] = []
    const router = montarConSonda("/ganaderos/1", registro)
    await screen.findByRole("heading", { level: 1, name: "Ganadero 1" })

    await act(() => router.navigate("/ganaderos/2"))
    expect(screen.queryByText("Ganadero 1")).not.toBeInTheDocument()
    expect(screen.getByText("Cargando ganadero…")).toBeInTheDocument()

    puerta2.abrir()
    expect(await screen.findByRole("heading", { level: 1, name: "Ganadero 2" })).toBeInTheDocument()
    expect(registro.filter((linea) => linea.startsWith("/ganaderos/2 => Ganadero 1"))).toEqual([])
  })

  it("una respuesta lenta de 1 que llega tras ir a 2 nunca se enseña", async () => {
    const puerta1 = puerta()
    const puerta2 = puerta()
    server.use(
      http.get(apiUrl("/ganaderos/:id"), async ({ params }) => {
        const id = Number(params.id)
        await (id === 1 ? puerta1 : puerta2).promesa
        return HttpResponse.json(ganaderoConId(id))
      }),
    )
    const registro: string[] = []
    const router = montarConSonda("/ganaderos/1", registro)
    await screen.findByText("Cargando ganadero…")

    await act(() => router.navigate("/ganaderos/2"))
    puerta1.abrir()
    // Deja correr la respuesta de 1 (y cualquier setState que provocara).
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))
    expect(screen.queryByText("Ganadero 1")).not.toBeInTheDocument()
    expect(screen.queryByRole("alert")).not.toBeInTheDocument()
    expect(screen.getByText("Cargando ganadero…")).toBeInTheDocument()

    puerta2.abrir()
    expect(await screen.findByRole("heading", { level: 1, name: "Ganadero 2" })).toBeInTheDocument()
    expect(registro.some((linea) => linea.includes("Ganadero 1"))).toBe(false)
  })

  it("una petición cancelada nunca se enseña como error (StrictMode aborta la primera)", async () => {
    const puerta7 = puerta()
    let peticiones = 0
    server.use(
      http.get(apiUrl("/ganaderos/:id"), async () => {
        peticiones += 1
        await puerta7.promesa
        return HttpResponse.json(ganadero([]))
      }),
    )
    const router = createMemoryRouter([{ path: "/ganaderos/:id", element: <GanaderoDetallePage /> }], {
      initialEntries: ["/ganaderos/7"],
    })
    render(
      <StrictMode>
        <RouterProvider router={router} />
      </StrictMode>,
    )

    // La primera la aborta el desmontaje de StrictMode (axios ni la llega a enviar): su rechazo
    // por cancelación no debe convertirse en una alerta.
    await vi.waitFor(() => expect(peticiones).toBeGreaterThanOrEqual(1))
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))
    expect(screen.queryByRole("alert")).not.toBeInTheDocument()
    expect(screen.getByText("Cargando ganadero…")).toBeInTheDocument()

    puerta7.abrir()
    expect(await screen.findByRole("heading", { level: 1, name: "Ana Ruiz Gómez" })).toBeInTheDocument()
  })
})

describe("GanaderoDetallePage: índice y hash (m2, m3)", () => {
  const CUATRO = [explotacion(1), explotacion(2), explotacion(3), explotacion(4)]

  it.each([
    ["ctrl", { ctrlKey: true }],
    ["cmd", { metaKey: true }],
    ["shift", { shiftKey: true }],
    // Alt+clic: el navegador descarga el enlace; tampoco se salta aquí.
    ["alt", { altKey: true }],
    ["botón central", { button: 1 }],
  ])("con %s el enlace del índice se deja al navegador (nueva pestaña), sin saltar aquí", async (_nombre, init) => {
    responderCon(() => HttpResponse.json(ganadero(CUATRO)))
    montar()

    const indice = await screen.findByRole("navigation", { name: "Explotaciones de este ganadero" })
    const enlace = within(indice).getAllByRole("link")[2]
    const noCancelado = fireEvent.click(enlace, init)
    expect(noCancelado).toBe(true)
    const seccion = screen.getAllByRole("region", { name: /^ES28079000/ })[2]
    expect(within(seccion).getByRole("heading", { level: 2 })).not.toHaveFocus()
  })

  it("un clic normal sí se queda en la página y enfoca la sección", async () => {
    responderCon(() => HttpResponse.json(ganadero(CUATRO)))
    montar()

    const indice = await screen.findByRole("navigation", { name: "Explotaciones de este ganadero" })
    expect(fireEvent.click(within(indice).getAllByRole("link")[1])).toBe(false)
    const seccion = screen.getAllByRole("region", { name: /^ES28079000/ })[1]
    expect(within(seccion).getByRole("heading", { level: 2 })).toHaveFocus()
  })

  it("abrir la ficha con #explotacion-N lleva el foco a esa sección cuando llegan los datos", async () => {
    responderCon(() => HttpResponse.json(ganadero(CUATRO)))
    montar("/ganaderos/7#explotacion-3")

    const seccion = (await screen.findAllByRole("region", { name: /^ES28079000/ }))[2]
    expect(seccion).toHaveAttribute("id", "explotacion-3")
    await vi.waitFor(() => expect(within(seccion).getByRole("heading", { level: 2 })).toHaveFocus())
  })

  it("un hash que no es de ninguna sección no mueve el foco ni rompe", async () => {
    responderCon(() => HttpResponse.json(ganadero(CUATRO)))
    montar("/ganaderos/7#explotacion-99")

    await screen.findAllByRole("region", { name: /^ES28079000/ })
    expect(document.activeElement).toBe(document.body)
  })
})
