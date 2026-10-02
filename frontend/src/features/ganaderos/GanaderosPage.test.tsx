import { render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { createMemoryRouter, RouterProvider, useParams } from "react-router-dom"
import { describe, expect, it } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { GanaderosPage } from "./GanaderosPage"

const GANADEROS = [
  { id: 7, nombre: "Ana Ruiz", nif: "12345678Z", numeroExplotaciones: 3 },
  { id: 9, nombre: "Hermanos Gil SC", nif: "B12345678", numeroExplotaciones: 1 },
]

function pagina(content: unknown[], totalElements = content.length, totalPages = 1, number = 0) {
  return HttpResponse.json({ content, totalElements, totalPages, number, size: 20 })
}

/** Promesa que el test abre cuando quiere: deja una respuesta de MSW "en vuelo". */
function puerta() {
  let abrir!: () => void
  const promesa = new Promise<void>((resolve) => {
    abrir = resolve
  })
  return { promesa, abrir }
}

function DestinoDetalle() {
  const { id } = useParams()
  return <p>Detalle del ganadero {id}</p>
}

function DestinoExplotaciones() {
  return <p>Pantalla de explotaciones</p>
}

function montar() {
  const router = createMemoryRouter(
    [
      { path: "/ganaderos", element: <GanaderosPage /> },
      { path: "/ganaderos/:id", element: <DestinoDetalle /> },
      { path: "/explotaciones", element: <DestinoExplotaciones /> },
    ],
    { initialEntries: ["/ganaderos"] },
  )
  render(<RouterProvider router={router} />)
  return router
}

/** Registra cada petición a /ganaderos y contesta con `responder`. */
function registrarPeticiones(responder: (url: URL) => Response | Promise<Response> = () => pagina(GANADEROS)) {
  const urls: URL[] = []
  server.use(
    http.get(apiUrl("/ganaderos"), ({ request }) => {
      const url = new URL(request.url)
      urls.push(url)
      return responder(url)
    }),
  )
  return urls
}

describe("GanaderosPage: listado", () => {
  it("pinta nombre, NIF y número de explotaciones, con el recuento debajo del título", async () => {
    registrarPeticiones()
    montar()

    expect(await screen.findByRole("link", { name: "Ana Ruiz" })).toHaveAttribute("href", "/ganaderos/7")
    expect(screen.getByRole("heading", { level: 1, name: "Ganaderos" })).toBeInTheDocument()
    expect(screen.getByText("2 ganaderos")).toBeInTheDocument()
    const filaAna = screen.getByRole("link", { name: "Ana Ruiz" }).closest("tr")!
    expect(within(filaAna).getByText("12345678Z")).toHaveClass("tabular-nums")
    expect(within(filaAna).getByText("3")).toHaveClass("tabular-nums", "text-right")
    expect(screen.getByRole("link", { name: "Hermanos Gil SC" })).toHaveAttribute("href", "/ganaderos/9")
  })

  it("un nombre largo puede partirse en su celda (móvil: el recuento sigue a la vista)", async () => {
    registrarPeticiones()
    montar()

    const celda = (await screen.findByRole("link", { name: "Ana Ruiz" })).closest("td")!
    expect(celda).toHaveClass("whitespace-normal", "wrap-anywhere")
  })

  it("un ganadero sin NIF no deja la celda en blanco", async () => {
    registrarPeticiones(() => pagina([{ id: 4, nombre: "Sin Nif", nif: null, numeroExplotaciones: 0 }]))
    montar()

    const fila = (await screen.findByRole("link", { name: "Sin Nif" })).closest("tr")!
    expect(within(fila).getByText("Sin NIF")).toBeInTheDocument()
    expect(screen.getByText("1 ganadero")).toBeInTheDocument()
  })

  it("pide 20 por página sin sort: aplica el orden por defecto del backend", async () => {
    const urls = registrarPeticiones()
    montar()

    await screen.findByRole("link", { name: "Ana Ruiz" })
    expect(urls).toHaveLength(1)
    expect(urls[0].searchParams.get("page")).toBe("0")
    expect(urls[0].searchParams.get("size")).toBe("20")
    expect(urls[0].searchParams.getAll("sort")).toEqual([])
  })

  it("no enseña nada de OVZ aunque la respuesta trajera campos de más", async () => {
    registrarPeticiones(() =>
      pagina([{ ...GANADEROS[0], ovzUsuario: "usuario-ovz-secreto", ovzPasswordCifrada: "xyz" }]),
    )
    montar()

    await screen.findByRole("link", { name: "Ana Ruiz" })
    expect(document.body.textContent).not.toMatch(/ovz|usuario-ovz-secreto|xyz/i)
  })
})

describe("GanaderosPage: ordenación accesible", () => {
  it("solo Nombre y NIF son ordenables; Nombre sale activo ascendente por defecto", async () => {
    registrarPeticiones()
    montar()
    await screen.findByRole("link", { name: "Ana Ruiz" })

    const cabeceras = screen.getAllByRole("columnheader")
    expect(cabeceras).toHaveLength(3)
    const [nombre, nif, explotaciones] = cabeceras
    expect(nombre).toHaveAccessibleName("Nombre")
    expect(nif).toHaveAccessibleName("NIF")
    // En móvil se ve "Expl." (aria-hidden); el nombre accesible es siempre "Explotaciones".
    expect(explotaciones).toHaveAccessibleName("Explotaciones")
    expect(within(explotaciones).getByText("Expl.")).toHaveAttribute("aria-hidden", "true")
    expect(within(explotaciones).getByText("Explotaciones")).toHaveClass("max-sm:sr-only")
    expect(nombre).toHaveAttribute("aria-sort", "ascending")
    expect(nif).not.toHaveAttribute("aria-sort")
    expect(explotaciones).not.toHaveAttribute("aria-sort")
    expect(within(nombre).getByRole("button", { name: "Nombre" })).toBeInTheDocument()
    expect(within(nif).getByRole("button", { name: "NIF" })).toBeInTheDocument()
    expect(within(explotaciones).queryByRole("button")).not.toBeInTheDocument()
    // Icono de dirección visible (decorativo para el lector: la dirección la da aria-sort).
    expect(nombre.querySelector("svg")).toHaveAttribute("aria-hidden", "true")
  })

  it("Nombre alterna a descendente, luego ascendente, y manda el sort con id de desempate", async () => {
    const urls = registrarPeticiones()
    const user = userEvent.setup()
    montar()
    await screen.findByRole("link", { name: "Ana Ruiz" })

    await user.click(screen.getByRole("button", { name: "Nombre" }))
    await screen.findByRole("link", { name: "Ana Ruiz" })
    expect(urls.at(-1)!.searchParams.getAll("sort")).toEqual(["nombre,desc", "id,desc"])
    expect(screen.getAllByRole("columnheader")[0]).toHaveAttribute("aria-sort", "descending")

    await user.click(screen.getByRole("button", { name: "Nombre" }))
    await screen.findByRole("link", { name: "Ana Ruiz" })
    expect(urls.at(-1)!.searchParams.getAll("sort")).toEqual(["nombre,asc", "id,asc"])
    expect(screen.getAllByRole("columnheader")[0]).toHaveAttribute("aria-sort", "ascending")
  })

  it("NIF ordena ascendente y luego descendente; Nombre deja de estar activo", async () => {
    const urls = registrarPeticiones()
    const user = userEvent.setup()
    montar()
    await screen.findByRole("link", { name: "Ana Ruiz" })

    await user.click(screen.getByRole("button", { name: "NIF" }))
    await screen.findByRole("link", { name: "Ana Ruiz" })
    expect(urls.at(-1)!.searchParams.getAll("sort")).toEqual(["nif,asc", "id,asc"])
    const [nombre, nif] = screen.getAllByRole("columnheader")
    expect(nif).toHaveAttribute("aria-sort", "ascending")
    expect(nombre).not.toHaveAttribute("aria-sort")

    await user.click(screen.getByRole("button", { name: "NIF" }))
    await screen.findByRole("link", { name: "Ana Ruiz" })
    expect(urls.at(-1)!.searchParams.getAll("sort")).toEqual(["nif,desc", "id,desc"])
    expect(screen.getAllByRole("columnheader")[1]).toHaveAttribute("aria-sort", "descending")
  })

  it("cambiar el orden vuelve a la primera página", async () => {
    const urls = registrarPeticiones((url) => {
      const page = Number(url.searchParams.get("page"))
      return pagina([{ ...GANADEROS[0], id: 100 + page, nombre: `Ganadero p${page}` }], 60, 3, page)
    })
    const user = userEvent.setup()
    montar()
    await screen.findByRole("link", { name: "Ganadero p0" })

    await user.click(screen.getByRole("button", { name: "Siguiente" }))
    await screen.findByRole("link", { name: "Ganadero p1" })
    expect(screen.getByText("Página 2 de 3")).toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "NIF" }))
    await screen.findByRole("link", { name: "Ganadero p0" })
    const ultima = urls.at(-1)!
    expect(ultima.searchParams.get("page")).toBe("0")
    expect(ultima.searchParams.getAll("sort")).toEqual(["nif,asc", "id,asc"])
    expect(screen.getByText("Página 1 de 3")).toBeInTheDocument()
  })
})

describe("GanaderosPage: navegación al detalle", () => {
  it("el nombre es un enlace real que lleva al detalle", async () => {
    registrarPeticiones()
    const user = userEvent.setup()
    const router = montar()

    await user.click(await screen.findByRole("link", { name: "Ana Ruiz" }))
    expect(await screen.findByText("Detalle del ganadero 7")).toBeInTheDocument()
    // Un solo paso en el historial: el clic del enlace no se duplica con el de la fila.
    expect(router.state.historyAction).toBe("PUSH")
    router.navigate(-1)
    expect(await screen.findByRole("link", { name: "Ana Ruiz" })).toBeInTheDocument()
  })

  it("el enlace se alcanza con el teclado y abre con Enter", async () => {
    registrarPeticiones()
    const user = userEvent.setup()
    montar()
    await screen.findByRole("link", { name: "Ana Ruiz" })

    // Tab: botón Nombre, botón NIF, enlace del primer ganadero.
    await user.tab()
    await user.tab()
    await user.tab()
    expect(screen.getByRole("link", { name: "Ana Ruiz" })).toHaveFocus()
    await user.keyboard("{Enter}")
    expect(await screen.findByText("Detalle del ganadero 7")).toBeInTheDocument()
  })

  it("clic en cualquier parte de la fila también abre el detalle", async () => {
    registrarPeticiones()
    const user = userEvent.setup()
    montar()

    await screen.findByRole("link", { name: "Hermanos Gil SC" })
    await user.click(screen.getByText("B12345678"))
    expect(await screen.findByText("Detalle del ganadero 9")).toBeInTheDocument()
  })

  it("ctrl+clic en la fila no navega en esta pestaña (eso es cosa del enlace)", async () => {
    registrarPeticiones()
    const user = userEvent.setup()
    montar()

    await screen.findByRole("link", { name: "Ana Ruiz" })
    await user.keyboard("{Control>}")
    await user.click(screen.getByText("12345678Z"))
    await user.keyboard("{/Control}")
    expect(screen.getByRole("link", { name: "Ana Ruiz" })).toBeInTheDocument()
    expect(screen.queryByText(/Detalle del ganadero/)).not.toBeInTheDocument()
  })
})

describe("GanaderosPage: estados", () => {
  it("mientras carga, lo dice en la tabla", async () => {
    const { promesa, abrir } = puerta()
    registrarPeticiones(async () => {
      await promesa
      return pagina(GANADEROS)
    })
    montar()

    // Esqueleto de 5 filas; el texto queda solo para lector de pantalla, como estado.
    expect(await screen.findByRole("status")).toHaveTextContent("Cargando ganaderos…")
    expect(screen.getByText("Cargando ganaderos…")).toHaveClass("sr-only")
    expect(screen.getByText("Cargando…")).toBeInTheDocument()
    expect(screen.getByRole("table")).toHaveAttribute("aria-busy", "true")
    const filasCuerpo = within(screen.getByRole("table")).getAllByRole("row").slice(1)
    expect(filasCuerpo).toHaveLength(5)
    expect(filasCuerpo[0].querySelectorAll(".animate-pulse, [class*='animate-pulse']")).toHaveLength(3)
    abrir()
    expect(await screen.findByRole("link", { name: "Ana Ruiz" })).toBeInTheDocument()
    expect(screen.queryByText("Cargando ganaderos…")).not.toBeInTheDocument()
  })

  it("el estado de carga está siempre montado, fuera de aria-busy, y solo cambia su texto (4.1.3)", async () => {
    const { promesa, abrir } = puerta()
    registrarPeticiones(async () => {
      await promesa
      return pagina(GANADEROS)
    })
    montar()

    const estado = screen.getByRole("status")
    expect(estado).toHaveTextContent("Cargando ganaderos…")
    expect(estado).toHaveClass("sr-only")
    expect(estado.closest("[aria-busy]")).toBeNull()
    abrir()
    expect(await screen.findByRole("link", { name: "Ana Ruiz" })).toBeInTheDocument()
    // El mismo nodo, ahora vacío: no se desmonta ni se vuelve a montar.
    expect(screen.getByRole("status")).toBe(estado)
    expect(estado).toBeEmptyDOMElement()
  })

  it("si falla, lo dice con Reintentar, que vuelve a pedirlo", async () => {
    let llamadas = 0
    registrarPeticiones(() => {
      llamadas += 1
      return llamadas === 1 ? new HttpResponse(null, { status: 500 }) : pagina(GANADEROS)
    })
    const user = userEvent.setup()
    montar()

    const alerta = await screen.findByRole("alert")
    expect(within(alerta).getByText("No se han podido cargar los ganaderos")).toBeInTheDocument()
    expect(
      within(alerta).getByText("Ha fallado algo en el servidor. Inténtalo de nuevo; si se repite, avísanos."),
    ).toBeInTheDocument()

    await user.click(within(alerta).getByRole("button", { name: "Reintentar" }))
    expect(await screen.findByRole("link", { name: "Ana Ruiz" })).toBeInTheDocument()
    expect(screen.queryByRole("alert")).not.toBeInTheDocument()
  })

  it("si falla una página intermedia, la paginación sigue ahí para salir de ella", async () => {
    registrarPeticiones((url) => {
      const page = Number(url.searchParams.get("page"))
      if (page === 1) return HttpResponse.error()
      return pagina([{ ...GANADEROS[0], nombre: `Ganadero p${page}` }], 60, 3, page)
    })
    const user = userEvent.setup()
    montar()

    await screen.findByRole("link", { name: "Ganadero p0" })
    await user.click(screen.getByRole("button", { name: "Siguiente" }))
    expect(await screen.findByText("No se han podido cargar los ganaderos")).toBeInTheDocument()
    expect(screen.getByText("Página 2 de 3")).toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "Anterior" }))
    expect(await screen.findByRole("link", { name: "Ganadero p0" })).toBeInTheDocument()
  })

  it("vacío: explica de dónde salen los ganaderos y enlaza a Explotaciones", async () => {
    registrarPeticiones(() => pagina([], 0, 0))
    const user = userEvent.setup()
    montar()

    expect(
      await screen.findByText(
        "Todavía no hay ganaderos. Se crean al importar el Excel de explotaciones.",
      ),
    ).toBeInTheDocument()
    expect(screen.getByText("0 ganaderos")).toBeInTheDocument()
    const enlace = screen.getByRole("link", { name: "Ir a Explotaciones" })
    expect(enlace).toHaveAttribute("href", "/explotaciones")
    await user.click(enlace)
    expect(await screen.findByText("Pantalla de explotaciones")).toBeInTheDocument()
  })
})
