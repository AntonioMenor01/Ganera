import { act, render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { useState } from "react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { listarAnimalesDeExplotacion } from "./api"
import { AnimalesDeExplotacion } from "./AnimalesDeExplotacion"

// La función real, envuelta solo para ver qué AbortSignal recibe cada petición: el signal de la
// request de MSW no refleja el abort de XHR en jsdom (mismo patrón que useBuscarExplotaciones).
// La petición HTTP y el paso de la cancelación a ErrorApi{cancelado} siguen siendo los reales.
vi.mock("./api", async (importOriginal) => {
  const real = await importOriginal<typeof import("./api")>()
  return { ...real, listarAnimalesDeExplotacion: vi.fn(real.listarAnimalesDeExplotacion) }
})

beforeEach(() => {
  vi.mocked(listarAnimalesDeExplotacion).mockClear()
})

/** AbortSignal de la llamada que pidió la página `page`. */
function senalDePagina(page: number): AbortSignal {
  const llamada = vi.mocked(listarAnimalesDeExplotacion).mock.calls.find(([, p]) => p === page)
  if (!llamada) throw new Error(`no se pidió la página ${page}`)
  return llamada[3] as AbortSignal
}

const RUTA = "/explotaciones/:id/animales"

interface AnimalApi {
  id: number
  crotal: string
  crotalUltimosDigitos: string
}

function animal(id: number, crotal = `ES01000000${String(id).padStart(4, "0")}`): AnimalApi {
  return { id, crotal, crotalUltimosDigitos: crotal.slice(-6) }
}

function pagina(content: AnimalApi[], { number = 0, totalPages = 1, totalElements = content.length } = {}) {
  return { content, totalElements, totalPages, number, size: 20 }
}

/** Promesa que se resuelve desde fuera: deja una respuesta de MSW "en vuelo" hasta que el test quiera. */
function diferido() {
  let resolver!: () => void
  const promesa = new Promise<void>((r) => {
    resolver = r
  })
  return { promesa, resolver }
}

/** Monta el panel detrás de un botón, como lo hacen las dos pantallas: plegar lo desmonta. */
function ConInterruptor({ explotacionId }: { explotacionId: number }) {
  const [abierto, setAbierto] = useState(true)
  return (
    <>
      <button type="button" onClick={() => setAbierto((a) => !a)}>
        Alternar
      </button>
      {abierto && <AnimalesDeExplotacion explotacionId={explotacionId} />}
    </>
  )
}

function crotalesMostrados(): string[] {
  const lista = screen.getByRole("list", { name: "Crotales" })
  return within(lista)
    .getAllByRole("listitem")
    .map((item) => item.textContent ?? "")
}

describe("AnimalesDeExplotacion: carga", () => {
  it("pide una sola vez la primera página de 20 de esa explotación, ordenada por crotal (y id)", async () => {
    const urls: URL[] = []
    server.use(
      http.get(apiUrl(RUTA), ({ request }) => {
        urls.push(new URL(request.url))
        return HttpResponse.json(pagina([animal(1), animal(2), animal(3)]))
      }),
    )
    render(<AnimalesDeExplotacion explotacionId={7} />)

    await screen.findByRole("list", { name: "Crotales" })
    expect(urls).toHaveLength(1)
    expect(urls[0].pathname).toBe("/explotaciones/7/animales")
    expect(urls[0].searchParams.get("page")).toBe("0")
    expect(urls[0].searchParams.get("size")).toBe("20")
    expect(urls[0].searchParams.getAll("sort")).toEqual(["crotal,asc", "id,asc"])
  })

  it("pinta los crotales en el orden en que llegan, con el recuento y sin paginación si cabe en una", async () => {
    server.use(
      http.get(apiUrl(RUTA), () =>
        HttpResponse.json(
          pagina([animal(1, "ES010000000001"), animal(2, "ES010000000002"), animal(3, "FR1234567890")]),
        ),
      ),
    )
    render(<AnimalesDeExplotacion explotacionId={7} />)

    await screen.findByRole("list", { name: "Crotales" })
    expect(crotalesMostrados()).toEqual(["ES010000000001", "ES010000000002", "FR1234567890"])
    expect(screen.getByText("3 animales")).toBeInTheDocument()
    expect(screen.queryByRole("navigation")).not.toBeInTheDocument()
  })

  it("con un solo animal, el recuento va en singular", async () => {
    server.use(http.get(apiUrl(RUTA), () => HttpResponse.json(pagina([animal(1)]))))
    render(<AnimalesDeExplotacion explotacionId={7} />)
    expect(await screen.findByText("1 animal")).toBeInTheDocument()
  })

  it("destaca los últimos dígitos (lo que se escribe por WhatsApp) sin partir el crotal", async () => {
    server.use(
      http.get(apiUrl(RUTA), () =>
        HttpResponse.json(
          pagina([
            { id: 1, crotal: "ES010000001234", crotalUltimosDigitos: "001234" },
            // Si no cuadran (dato raro), el crotal se pinta entero y sin destacar nada.
            { id: 2, crotal: "ES010000009999", crotalUltimosDigitos: "123456" },
          ]),
        ),
      ),
    )
    render(<AnimalesDeExplotacion explotacionId={7} />)

    const [primero, segundo] = within(await screen.findByRole("list", { name: "Crotales" })).getAllByRole(
      "listitem",
    )
    expect(primero).toHaveTextContent(/^ES010000001234$/)
    expect(within(primero).getByText("001234")).toHaveClass("text-foreground")
    expect(within(primero).getByText("ES010000")).toHaveClass("text-muted-foreground")
    expect(segundo).toHaveTextContent(/^ES010000009999$/)
    expect(within(segundo).queryByText("123456")).not.toBeInTheDocument()
  })

  it("mientras carga enseña un esqueleto con estado para lector de pantalla, y luego lo quita", async () => {
    const puerta = diferido()
    server.use(
      http.get(apiUrl(RUTA), async () => {
        await puerta.promesa
        return HttpResponse.json(pagina([animal(1)]))
      }),
    )
    render(<AnimalesDeExplotacion explotacionId={7} />)

    const estado = screen.getByRole("status")
    expect(estado).toHaveTextContent("Cargando animales…")
    expect(within(estado).getByText("Cargando animales…")).toHaveClass("sr-only")

    puerta.resolver()
    await screen.findByRole("list", { name: "Crotales" })
    expect(screen.queryByText("Cargando animales…")).not.toBeInTheDocument()
  })

  it("el estado está siempre montado, fuera de aria-busy, y solo cambia su texto (4.1.3)", async () => {
    const puerta = diferido()
    server.use(
      http.get(apiUrl(RUTA), async ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"))
        if (page === 1) await puerta.promesa
        return HttpResponse.json(pagina([animal(page + 1)], { number: page, totalPages: 2, totalElements: 21 }))
      }),
    )
    const user = userEvent.setup()
    render(<AnimalesDeExplotacion explotacionId={7} />)

    const estado = screen.getByRole("status")
    expect(estado).toHaveTextContent("Cargando animales…")
    expect(estado).toHaveClass("sr-only")
    expect(estado.closest("[aria-busy]")).toBeNull()

    await screen.findByRole("list", { name: "Crotales" })
    expect(screen.getByRole("status")).toBe(estado)
    expect(estado).toBeEmptyDOMElement()

    // Otra página: el mismo nodo vuelve a decir que carga, y se vacía al llegar.
    await user.click(screen.getByRole("button", { name: "Siguiente" }))
    expect(screen.getByRole("status")).toBe(estado)
    expect(estado).toHaveTextContent("Cargando animales…")
    puerta.resolver()
    await waitFor(() => expect(estado).toBeEmptyDOMElement())
    expect(screen.getAllByRole("status")).toHaveLength(1)
  })
})

describe("AnimalesDeExplotacion: estados sin contenido", () => {
  it("vacío: explica que se cargan al importar el Excel", async () => {
    server.use(http.get(apiUrl(RUTA), () => HttpResponse.json(pagina([]))))
    render(<AnimalesDeExplotacion explotacionId={7} />)

    expect(
      await screen.findByText(
        "Esta explotación no tiene animales en el inventario. Se cargan al importar el Excel.",
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole("list", { name: "Crotales" })).not.toBeInTheDocument()
    expect(screen.queryByRole("alert")).not.toBeInTheDocument()
  })

  it("404: la explotación no existe o no es de tu gestoría, sin error genérico ni Reintentar", async () => {
    server.use(http.get(apiUrl(RUTA), () => new HttpResponse(null, { status: 404 })))
    render(<AnimalesDeExplotacion explotacionId={7} />)

    expect(
      await screen.findByText("Esta explotación no existe o no es de tu gestoría."),
    ).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Reintentar" })).not.toBeInTheDocument()
    expect(screen.queryByText(/Inténtalo de nuevo/)).not.toBeInTheDocument()
  })

  it("error: alerta con el texto del error y Reintentar vuelve a pedir la misma página", async () => {
    let llamadas = 0
    server.use(
      http.get(apiUrl(RUTA), () => {
        llamadas += 1
        if (llamadas === 1) return new HttpResponse(null, { status: 500 })
        return HttpResponse.json(pagina([animal(1)]))
      }),
    )
    const user = userEvent.setup()
    render(<AnimalesDeExplotacion explotacionId={7} />)

    const alerta = await screen.findByRole("alert")
    expect(alerta).toHaveTextContent("No se han podido cargar los animales")
    expect(alerta.firstElementChild?.matches('svg[aria-hidden="true"]')).toBe(true)
    expect(alerta).toHaveTextContent(
      "Ha fallado algo en el servidor. Inténtalo de nuevo; si se repite, avísanos.",
    )

    await user.click(within(alerta).getByRole("button", { name: "Reintentar" }))
    await screen.findByRole("list", { name: "Crotales" })
    expect(llamadas).toBe(2)
    expect(screen.queryByRole("alert")).not.toBeInTheDocument()
  })

  it("error sin motivo del backend: el texto genérico de animales, nunca vacío", async () => {
    server.use(http.get(apiUrl(RUTA), () => new HttpResponse(null, { status: 418 })))
    render(<AnimalesDeExplotacion explotacionId={7} />)

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "No se han podido cargar los animales. Inténtalo de nuevo.",
    )
  })
})

describe("AnimalesDeExplotacion: paginación", () => {
  function servirTresPaginas(peticiones: number[] = []) {
    server.use(
      http.get(apiUrl(RUTA), ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"))
        peticiones.push(page)
        return HttpResponse.json(
          pagina([animal(page * 20 + 1)], { number: page, totalPages: 3, totalElements: 45 }),
        )
      }),
    )
  }

  it("Siguiente pide la página siguiente y la pinta; Anterior está desactivado en la primera", async () => {
    const peticiones: number[] = []
    servirTresPaginas(peticiones)
    const user = userEvent.setup()
    render(<AnimalesDeExplotacion explotacionId={7} />)

    await screen.findByText("45 animales")
    const paginacion = screen.getByRole("navigation", { name: "Páginas de animales" })
    expect(within(paginacion).getByText("Página 1 de 3")).toBeInTheDocument()
    expect(within(paginacion).getByRole("button", { name: "Anterior" })).toBeDisabled()

    await user.click(within(paginacion).getByRole("button", { name: "Siguiente" }))
    await waitFor(() => expect(crotalesMostrados()).toEqual(["ES010000000021"]))
    expect(within(paginacion).getByText("Página 2 de 3")).toBeInTheDocument()
    expect(peticiones).toEqual([0, 1])
  })

  it("una respuesta vieja nunca pisa a la nueva, y su cancelación no se ve como error", async () => {
    const puertaPagina1 = diferido()
    const puertaPagina2 = diferido()
    server.use(
      http.get(apiUrl(RUTA), async ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"))
        if (page === 1) await puertaPagina1.promesa
        if (page === 2) await puertaPagina2.promesa
        return HttpResponse.json(
          pagina([animal(page * 20 + 1)], { number: page, totalPages: 3, totalElements: 45 }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<AnimalesDeExplotacion explotacionId={7} />)

    await screen.findByText("45 animales")
    await user.click(screen.getByRole("button", { name: "Siguiente" })) // página 2, se queda en vuelo
    await user.click(screen.getByRole("button", { name: "Siguiente" })) // página 3, también en vuelo
    await waitFor(() => expect(screen.getByText("Página 3 de 3")).toBeInTheDocument())
    expect(senalDePagina(1).aborted).toBe(true)
    expect(senalDePagina(2).aborted).toBe(false)

    // Con la nueva aún en vuelo: la cancelada no enseña error ni quita la página que se ve.
    await new Promise((r) => setTimeout(r, 50))
    expect(screen.queryByRole("alert")).not.toBeInTheDocument()
    expect(crotalesMostrados()).toEqual(["ES010000000001"])
    expect(screen.getByRole("status")).toHaveTextContent("Cargando animales…")

    puertaPagina2.resolver()
    await waitFor(() => expect(crotalesMostrados()).toEqual(["ES010000000041"]))

    puertaPagina1.resolver()
    // Se da tiempo a que la respuesta de la página 2 llegue: no debe cambiar nada.
    await new Promise((r) => setTimeout(r, 50))
    expect(crotalesMostrados()).toEqual(["ES010000000041"])
    expect(screen.getByText("Página 3 de 3")).toBeInTheDocument()
  })

  it("si falla una página, la paginación conserva el último total para poder salir de ella", async () => {
    server.use(
      http.get(apiUrl(RUTA), ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"))
        if (page === 1) return HttpResponse.error()
        return HttpResponse.json(
          pagina([animal(page * 20 + 1)], { number: page, totalPages: 3, totalElements: 45 }),
        )
      }),
    )
    const user = userEvent.setup()
    render(<AnimalesDeExplotacion explotacionId={7} />)

    await screen.findByText("45 animales")
    await user.click(screen.getByRole("button", { name: "Siguiente" }))
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "No se ha podido conectar con Ganera. Inténtalo de nuevo en unos segundos.",
    )
    // La página que falló no deja a la vista la anterior como si fuera la suya.
    expect(screen.queryByRole("list", { name: "Crotales" })).not.toBeInTheDocument()

    const paginacion = screen.getByRole("navigation", { name: "Páginas de animales" })
    expect(within(paginacion).getByText("Página 2 de 3")).toBeInTheDocument()
    expect(within(paginacion).getByRole("button", { name: "Siguiente" })).toBeEnabled()
    await user.click(within(paginacion).getByRole("button", { name: "Anterior" }))
    await waitFor(() => expect(crotalesMostrados()).toEqual(["ES010000000001"]))
    expect(screen.queryByRole("alert")).not.toBeInTheDocument()
  })
})

describe("AnimalesDeExplotacion: respuesta que llega ya resuelta tras cancelarla (m2)", () => {
  it("una página que resuelve después de haberse cancelado (el abort ya no la rechaza) no pisa la actual", async () => {
    // Promesas resueltas a mano que IGNORAN el signal: es el caso en que la respuesta ya había
    // llegado cuando se canceló, y axios ya no puede rechazarla. Solo la guarda del `then` la para.
    const respuestas = new Map<number, (valor: ReturnType<typeof pagina>) => void>()
    const pedir = (_id: number, page: number) =>
      new Promise<ReturnType<typeof pagina>>((resolver) => {
        respuestas.set(page, resolver)
      })
    vi.mocked(listarAnimalesDeExplotacion)
      .mockImplementationOnce(pedir)
      .mockImplementationOnce(pedir)
      .mockImplementationOnce(pedir)
    const responder = (page: number) =>
      act(() =>
        respuestas.get(page)!(pagina([animal(page * 20 + 1)], { number: page, totalPages: 3, totalElements: 45 })),
      )
    const user = userEvent.setup()
    render(<AnimalesDeExplotacion explotacionId={7} />)

    await waitFor(() => expect(respuestas.has(0)).toBe(true))
    await responder(0)
    await screen.findByText("45 animales")
    await user.click(screen.getByRole("button", { name: "Siguiente" })) // página 2
    await user.click(screen.getByRole("button", { name: "Siguiente" })) // página 3
    await waitFor(() => expect(respuestas.has(2)).toBe(true))
    expect(senalDePagina(1).aborted).toBe(true)

    await responder(2)
    await waitFor(() => expect(crotalesMostrados()).toEqual(["ES010000000041"]))

    await responder(1) // llega tarde, ya cancelada
    expect(crotalesMostrados()).toEqual(["ES010000000041"])
    expect(screen.getByText("Página 3 de 3")).toBeInTheDocument()
  })
})

describe("AnimalesDeExplotacion: desmontaje", () => {
  it("plegar con la petición en vuelo la cancela, sin enseñar ni registrar ningún error", async () => {
    const puerta = diferido()
    let peticiones = 0
    server.use(
      http.get(apiUrl(RUTA), async () => {
        peticiones += 1
        await puerta.promesa
        return HttpResponse.json(pagina([animal(1)]))
      }),
    )
    const errorConsola = vi.spyOn(console, "error")
    const user = userEvent.setup()
    render(<ConInterruptor explotacionId={7} />)

    await waitFor(() => expect(peticiones).toBe(1))
    const senal = senalDePagina(0)
    expect(senal.aborted).toBe(false)
    await user.click(screen.getByRole("button", { name: "Alternar" }))
    expect(senal.aborted).toBe(true)
    puerta.resolver()
    await new Promise((r) => setTimeout(r, 50))

    expect(screen.queryByRole("alert")).not.toBeInTheDocument()
    expect(screen.queryByRole("status")).not.toBeInTheDocument()
    expect(errorConsola).not.toHaveBeenCalled()
  })
})
