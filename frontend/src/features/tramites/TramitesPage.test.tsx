import { render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { beforeEach, describe, expect, it } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { ESTADOS_TRAMITE } from "./etiquetas"
import { TramitesPage } from "./TramitesPage"

const EXPLOTACIONES = [
  { id: 3, codigoRega: "ES280790000123", nombre: "Finca La Dehesa", ganaderoId: 1, nombreGanadero: "Ana" },
  { id: 5, codigoRega: "ES280790000555", nombre: "El Encinar", ganaderoId: 1, nombreGanadero: "Ana" },
]

function paginaExplotaciones(lista = EXPLOTACIONES, totalElements = lista.length) {
  return HttpResponse.json({
    content: lista,
    totalElements,
    totalPages: totalElements === 0 ? 0 : 1,
    number: 0,
    size: 20,
  })
}

function tramite(id: number, extra: Record<string, unknown> = {}) {
  return {
    id,
    explotacionId: null,
    explotacionCodigoRega: null,
    explotacionNombre: null,
    tipoTramite: "ALTA",
    estado: "PENDIENTE_REVISION",
    motivoError: null,
    crotales: [],
    version: 0,
    ...extra,
  }
}

function paginaTramites(content: unknown[], totalElements: number, totalPages: number, number = 0) {
  return HttpResponse.json({ content, totalElements, totalPages, number, size: 20 })
}

function detalle(id: number) {
  return {
    ...tramite(id),
    explotacionCodigoRega: null,
    explotacionNombre: null,
    mensajeOriginal: "alta del 1234",
  }
}

/** Promesa que el test abre cuando quiere: deja una respuesta de MSW "en vuelo". */
function puerta() {
  let abrir!: () => void
  const promesa = new Promise<void>((resolve) => {
    abrir = resolve
  })
  return { promesa, abrir }
}

describe("TramitesPage: etiquetas legibles", () => {
  it("la cola muestra la etiqueta del estado y del tipo, nunca el enum", async () => {
    server.use(
      http.get(apiUrl("/tramites"), () =>
        HttpResponse.json({
          content: [
            {
              id: 1,
              explotacionId: null,
              explotacionCodigoRega: null,
              explotacionNombre: null,
              tipoTramite: "MOVIMIENTO",
              estado: "PENDIENTE_REVISION",
              motivoError: null,
              crotales: [],
              version: 0,
            },
            {
              id: 2,
              explotacionId: 3,
              explotacionCodigoRega: "ES280790000123",
              explotacionNombre: "Finca La Dehesa",
              tipoTramite: "ALTA",
              estado: "EJECUTADO_OVZ",
              motivoError: null,
              crotales: [],
              version: 4,
            },
          ],
          totalElements: 2,
          totalPages: 1,
          number: 0,
          size: 20,
        }),
      ),
    )
    render(<TramitesPage />)

    // Decisión 28: el disparador del filtro también dice "Pendiente de revisión"; el badge se busca
    // dentro de la tabla.
    await screen.findByRole("button", { name: "Revisar trámite #1" })
    const tabla = screen.getByRole("table")
    expect(within(tabla).getByText("Pendiente de revisión")).toHaveClass("bg-warning")
    expect(within(tabla).getByText("Ejecutado en OVZ.net")).toHaveClass("bg-success")
    expect(screen.getByText("Movimiento")).toBeInTheDocument()
    expect(screen.getByText("Alta")).toBeInTheDocument()
    for (const enumCrudo of ["PENDIENTE_REVISION", "EJECUTADO_OVZ", "MOVIMIENTO", "ALTA"]) {
      expect(screen.queryByText(enumCrudo)).not.toBeInTheDocument()
    }
  })

  it("un estado o tipo que el frontend aún no conoce sale tal cual, sin romper", async () => {
    server.use(
      http.get(apiUrl("/tramites"), () =>
        HttpResponse.json({
          content: [
            {
              id: 9,
              explotacionId: null,
              explotacionCodigoRega: null,
              explotacionNombre: null,
              tipoTramite: "TRASLADO_FERIA",
              estado: "PAUSADO",
              motivoError: null,
              crotales: [],
              version: 0,
            },
          ],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 20,
        }),
      ),
    )
    render(<TramitesPage />)

    expect(await screen.findByText("PAUSADO")).toHaveClass("border-border")
    expect(screen.getByText("TRASLADO_FERIA")).toBeInTheDocument()
  })
})

describe("TramitesPage: errores visibles", () => {
  it("si falla la carga de la cola, lo dice y permite reintentar (antes no se veía nada)", async () => {
    let llamadas = 0
    server.use(
      http.get(apiUrl("/tramites"), () => {
        llamadas += 1
        if (llamadas === 1) return HttpResponse.error()
        return HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })
      }),
    )
    const user = userEvent.setup()
    render(<TramitesPage />)

    expect(await screen.findByText("No se han podido cargar los trámites")).toBeInTheDocument()
    expect(screen.getByText("No se han podido cargar los trámites").closest("[role=alert]")!.firstElementChild?.matches('svg[aria-hidden="true"]')).toBe(true)
    expect(
      screen.getByText("No se ha podido conectar con Ganera. Inténtalo de nuevo en unos segundos."),
    ).toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "Reintentar" }))
    expect(await screen.findByText("No hay trámites en «Pendiente de revisión».")).toBeInTheDocument()
    expect(screen.queryByText("No se han podido cargar los trámites")).not.toBeInTheDocument()
  })

  it("si falla una página intermedia, la paginación sigue ahí para salir de ella (M3)", async () => {
    server.use(
      http.get(apiUrl("/tramites"), ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"))
        if (page === 1) return new HttpResponse(null, { status: 500 })
        return HttpResponse.json({
          content: [
            {
              id: 100 + page,
              explotacionId: null,
              explotacionCodigoRega: null,
              explotacionNombre: null,
              tipoTramite: null,
              estado: "PENDIENTE_REVISION",
              motivoError: null,
            },
          ],
          totalElements: 45,
          totalPages: 3,
          number: page,
          size: 20,
        })
      }),
    )
    const user = userEvent.setup()
    render(<TramitesPage />)

    await screen.findByText("#100")
    await user.click(screen.getByRole("button", { name: "Siguiente" }))
    expect(await screen.findByText("No se han podido cargar los trámites")).toBeInTheDocument()
    expect(screen.getByText("Página 2 de 3")).toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "Anterior" }))
    expect(await screen.findByText("#100")).toBeInTheDocument()
    expect(screen.queryByText("No se han podido cargar los trámites")).not.toBeInTheDocument()
  })
})

describe("TramitesPage: filas accesibles por teclado (P0 de la critique)", () => {
  beforeEach(() => {
    server.use(
      http.get(apiUrl("/tramites"), () => paginaTramites([tramite(1), tramite(2)], 2, 1)),
      http.get(apiUrl("/tramites/:id"), ({ params }) => HttpResponse.json(detalle(Number(params.id)))),
    )
  })

  it("cada fila tiene un botón enfocable con Tab, con nombre accesible que incluye el texto visible", async () => {
    const user = userEvent.setup()
    render(<TramitesPage />)
    const boton = await screen.findByRole("button", { name: "Revisar trámite #1" })
    expect(boton).toHaveTextContent("#1")
    // Enlace en columna de tabla: Tinta en reposo; Rojo 700 solo al apuntar o con foco.
    expect(boton).toHaveClass("text-foreground", "hover:text-enlace", "focus-visible:text-enlace", "focus-visible:ring-ring")
    expect(boton).not.toHaveClass("text-enlace")
    expect(boton).toHaveClass("font-medium", "tabular-nums")

    for (let i = 0; i < 10 && document.activeElement !== boton; i++) await user.tab()
    expect(boton).toHaveFocus()
  })

  it("Enter en la fila enfocada abre la revisión de ese trámite", async () => {
    const user = userEvent.setup()
    render(<TramitesPage />)
    const boton = await screen.findByRole("button", { name: "Revisar trámite #2" })
    boton.focus()
    await user.keyboard("{Enter}")

    const dialogo = await screen.findByRole("dialog")
    // 9b: el título del modal es "Trámite #N" (antes "Revisión de trámite" + descripción).
    expect(dialogo).toHaveAccessibleName("Trámite #2")
    expect(await within(dialogo).findByText("Trámite #2")).toBeInTheDocument()
  })

  it("Espacio también la abre", async () => {
    const user = userEvent.setup()
    render(<TramitesPage />)
    const boton = await screen.findByRole("button", { name: "Revisar trámite #1" })
    boton.focus()
    await user.keyboard(" ")

    const dialogo = await screen.findByRole("dialog")
    expect(await within(dialogo).findByText("Trámite #1")).toBeInTheDocument()
  })

  it("el recuento usa singular y plural", async () => {
    render(<TramitesPage />)
    expect(await screen.findByText("2 trámites")).toBeInTheDocument()
  })
})

describe("TramitesPage: código REGA desde el listado (punto 4)", () => {
  beforeEach(() => {
    server.use(
      http.get(apiUrl("/tramites"), () =>
        paginaTramites(
          [
            tramite(1, {
              explotacionId: 3,
              explotacionCodigoRega: "ES280790000123",
              explotacionNombre: "Finca La Dehesa",
            }),
            tramite(2),
            tramite(4, { explotacionId: 77, explotacionCodigoRega: "ES990000000777", explotacionNombre: "" }),
            tramite(6, { explotacionId: 88, explotacionCodigoRega: null, explotacionNombre: null }),
          ],
          4,
          1,
        ),
      ),
    )
  })

  it("pinta el REGA de cada trámite con el nombre en title y sr-only; null = Sin asignar; nunca el id", async () => {
    render(<TramitesPage />)

    const rega = await screen.findByText("ES280790000123")
    expect(rega).toHaveAttribute("title", "Finca La Dehesa")
    expect(rega).toHaveClass("tabular-nums")
    // M3: el nombre también llega sin depender de title (lector de pantalla, teclado).
    const fila = rega.closest("tr") as HTMLElement
    expect(within(fila).getByText(", Finca La Dehesa")).toHaveClass("sr-only")
    expect(screen.getByText("Sin asignar")).toBeInTheDocument()
    expect(screen.queryByText("#3")).not.toBeInTheDocument()
  })

  it("con el nombre vacío: solo el código, sin title ni coma para el lector de pantalla", async () => {
    render(<TramitesPage />)

    const rega = await screen.findByText("ES990000000777")
    expect(rega).not.toHaveAttribute("title")
    const celda = rega.closest("td") as HTMLElement
    expect(celda).toHaveTextContent(/^ES990000000777$/)
    expect(within(celda).queryByText(/^,/)).not.toBeInTheDocument()
  })

  it("con explotación pero sin código REGA (no debería pasar): «—» y «Código REGA no disponible», nunca «Sin asignar» ni el id", async () => {
    render(<TramitesPage />)

    const boton = await screen.findByRole("button", { name: "Revisar trámite #6" })
    const celda = within(boton.closest("tr") as HTMLElement).getAllByRole("cell")[3]
    expect(celda).toHaveTextContent("—")
    expect(within(celda).getByText("Código REGA no disponible")).toHaveClass("sr-only")
    expect(within(celda).queryByText("Sin asignar")).not.toBeInTheDocument()
    expect(celda).not.toHaveTextContent("88")
    // Solo el trámite #2 está sin asignar.
    expect(screen.getAllByText("Sin asignar")).toHaveLength(1)
  })

  it("la cola no pide /explotaciones (T4): el REGA sale del listado", async () => {
    let pedidas = 0
    server.use(
      http.get(apiUrl("/explotaciones"), () => {
        pedidas += 1
        return paginaExplotaciones()
      }),
    )
    render(<TramitesPage />)

    expect(await screen.findByText("ES280790000123")).toBeInTheDocument()
    // Margen para que una petición, si la hubiera, llegara.
    await new Promise((r) => setTimeout(r, 50))
    expect(pedidas).toBe(0)
    expect(screen.queryByText("No se han podido cargar los códigos REGA")).not.toBeInTheDocument()
    expect(screen.queryByText("Cargando código REGA…")).not.toBeInTheDocument()
  })
})

describe("TramitesPage: crotales visibles en la fila", () => {
  it("muestra los dos primeros con su badge de resolución y resume el resto", async () => {
    server.use(
      http.get(apiUrl("/tramites"), () =>
        paginaTramites(
          [
            tramite(1, {
              crotales: [
                {
                  crotalIndicado: "789012",
                  crotal: "ES123456789012",
                  animalId: 8,
                  enInventario: true,
                  resolucion: "EN_INVENTARIO",
                },
                {
                  crotalIndicado: "1234",
                  crotal: "1234",
                  animalId: null,
                  enInventario: false,
                  resolucion: "AMBIGUO",
                },
                {
                  crotalIndicado: "ES000000000001",
                  crotal: "ES000000000001",
                  animalId: null,
                  enInventario: false,
                  resolucion: "NO_ENCONTRADO",
                },
              ],
            }),
            tramite(2),
          ],
          2,
          1,
        ),
      ),
    )
    render(<TramitesPage />)

    const resuelto = await screen.findByText("ES123456789012")
    expect(resuelto).toHaveAttribute("title", "Indicado: 789012")
    expect(screen.getByText("1234")).toBeInTheDocument()
    expect(screen.getByText("En inventario")).toHaveClass("bg-success")
    expect(screen.getByText("Varios animales coinciden")).toHaveClass("bg-warning")
    // Task 10: "+N más" es un botón que despliega (antes, title + texto sr-only "Además: …").
    const resto = screen.getByRole("button", { name: "+1 más" })
    expect(resto).toHaveAttribute("aria-expanded", "false")
    expect(resto).not.toHaveAttribute("title")
    expect(screen.queryByText("ES000000000001")).not.toBeInTheDocument()
    expect(screen.queryByText(/Además/)).not.toBeInTheDocument()
    // M3: lo que antes solo estaba en title se puede leer sin él (texto solo para lector de pantalla,
    // sin cargar la fila a la vista).
    expect(screen.getByText(", Indicado: 789012")).toHaveClass("sr-only")
    expect(screen.getByText("Sin crotales")).toBeInTheDocument()
  })
})

describe("TramitesPage: filtro y paginación", () => {
  it("las opciones del filtro salen de ESTADOS_TRAMITE (M2 de la revisión de Task 5)", async () => {
    server.use(http.get(apiUrl("/tramites"), () => paginaTramites([], 0, 0)))
    const user = userEvent.setup()
    render(<TramitesPage />)
    await screen.findByText("No hay trámites en «Pendiente de revisión».")

    await user.click(screen.getByRole("combobox"))
    const opciones = await screen.findAllByRole("option")

    expect(opciones.map((o) => o.textContent)).toEqual([
      "Todos los estados",
      ...Object.values(ESTADOS_TRAMITE).map((e) => e.etiqueta),
    ])
  })

  it("N1: al cambiar de filtro vuelve a la página 1 y no queda a la vista nada del filtro anterior", async () => {
    const { promesa, abrir } = puerta()
    const pedidasRechazado: string[] = []
    server.use(
      http.get(apiUrl("/tramites"), async ({ request }) => {
        const params = new URL(request.url).searchParams
        const page = Number(params.get("page"))
        if (params.get("estado") === "RECHAZADO") {
          pedidasRechazado.push(params.get("page") ?? "")
          await promesa
          // El filtro nuevo también tiene varias páginas: si no se volviera a la 1, N2 no lo taparía.
          return paginaTramites([tramite(50 + page, { estado: "RECHAZADO" })], 100, 5, page)
        }
        return paginaTramites([tramite(1 + page)], 100, 5, page)
      }),
    )
    const user = userEvent.setup()
    render(<TramitesPage />)
    expect(await screen.findByText("Página 1 de 5")).toBeInTheDocument()
    // M1 de la revisión de Task 6: se cambia de filtro desde una página posterior.
    await user.click(screen.getByRole("button", { name: "Siguiente" }))
    expect(await screen.findByText("Página 2 de 5")).toBeInTheDocument()
    expect(await screen.findByText("#2")).toBeInTheDocument()

    await user.click(screen.getByRole("combobox"))
    await user.click(await screen.findByRole("option", { name: "Rechazado" }))

    // Task 10: la fila "Cargando trámites…" pasa a esqueleto; el texto queda en el estado.
    expect(await screen.findByRole("status")).toHaveTextContent("Cargando trámites…")
    expect(screen.queryByText(/de 5/)).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Siguiente" })).not.toBeInTheDocument()
    expect(screen.queryByText("#2")).not.toBeInTheDocument()

    abrir()
    expect(await screen.findByText("#50")).toBeInTheDocument()
    expect(screen.getByText("Página 1 de 5")).toBeInTheDocument()
    expect(pedidasRechazado).toEqual(["0"])
  })

  it("con un filtro activo y sin resultados, el vacío lo nombra", async () => {
    server.use(http.get(apiUrl("/tramites"), () => paginaTramites([], 0, 0)))
    const user = userEvent.setup()
    render(<TramitesPage />)
    await screen.findByText("No hay trámites en «Pendiente de revisión».")

    await user.click(screen.getByRole("combobox"))
    await user.click(await screen.findByRole("option", { name: "Rechazado" }))

    expect(await screen.findByText("No hay trámites en «Rechazado».")).toBeInTheDocument()
  })

  it("N2: si aprobar vacía la última página, vuelve a la última página válida", async () => {
    let aprobado = false
    const paginasPedidas: string[] = []
    const primeraPagina = Array.from({ length: 20 }, (_, i) => tramite(i + 1))
    server.use(
      http.get(apiUrl("/tramites"), ({ request }) => {
        const page = new URL(request.url).searchParams.get("page") ?? "0"
        paginasPedidas.push(page)
        if (page === "0") return paginaTramites(primeraPagina, aprobado ? 20 : 21, aprobado ? 1 : 2, 0)
        return aprobado ? paginaTramites([], 20, 1, 1) : paginaTramites([tramite(21)], 21, 2, 1)
      }),
      http.get(apiUrl("/tramites/:id"), ({ params }) => HttpResponse.json(detalle(Number(params.id)))),
      http.post(apiUrl("/tramites/21/aprobar"), () => {
        aprobado = true
        return HttpResponse.json({ ...tramite(21), estado: "APROBADO", version: 1 })
      }),
    )
    const user = userEvent.setup()
    render(<TramitesPage />)

    await screen.findByText("Página 1 de 2")
    await user.click(screen.getByRole("button", { name: "Siguiente" }))
    await user.click(await screen.findByRole("button", { name: "Revisar trámite #21" }))
    const dialogo = await screen.findByRole("dialog")
    await within(dialogo).findByText("Trámite #21")
    await user.click(await within(dialogo).findByRole("button", { name: "Aprobar" }))
    // Decisión 13: tras aprobar, el modal sigue abierto con el resultado; se cierra a mano.
    await within(dialogo).findByText("Trámite aprobado.")
    await user.click(within(dialogo).getByRole("button", { name: "Cerrar" }))

    expect(await screen.findByRole("button", { name: "Revisar trámite #1" })).toBeInTheDocument()
    expect(screen.queryByText(/^No hay trámites/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Página \d+ de/)).not.toBeInTheDocument()
    expect(paginasPedidas.slice(-2)).toEqual(["1", "0"])
  })
})

describe("TramitesPage: el selector del modal busca en el backend (T4)", () => {
  it("abrir el modal no pide explotaciones; abrir el desplegable pide la primera página", async () => {
    let pedidas = 0
    server.use(
      http.get(apiUrl("/explotaciones"), () => {
        pedidas += 1
        return paginaExplotaciones()
      }),
      http.get(apiUrl("/tramites"), () => paginaTramites([tramite(1)], 1, 1)),
      http.get(apiUrl("/tramites/1"), () => HttpResponse.json(detalle(1))),
    )
    const user = userEvent.setup()
    render(<TramitesPage />)
    await user.click(await screen.findByRole("button", { name: "Revisar trámite #1" }))
    const dialogo = await screen.findByRole("dialog")
    const campo = await within(dialogo).findByRole("combobox", { name: "Explotación" })
    expect(pedidas).toBe(0)
    await user.click(campo)
    const opciones = await screen.findAllByRole("option")
    expect(opciones.map((o) => o.textContent)).toEqual([
      expect.stringContaining("ES280790000123"),
      expect.stringContaining("ES280790000555"),
    ])
    expect(pedidas).toBe(1)
  })
})

describe("TramitesPage: filtro inicial (decisión 28)", () => {
  it("la cola se abre filtrada por «Pendiente de revisión»", async () => {
    const peticiones: URLSearchParams[] = []
    server.use(
      http.get(apiUrl("/tramites"), ({ request }) => {
        peticiones.push(new URL(request.url).searchParams)
        return paginaTramites([], 0, 0)
      }),
    )
    render(<TramitesPage />)

    expect(await screen.findByText("No hay trámites en «Pendiente de revisión».")).toBeInTheDocument()
    expect(peticiones).toHaveLength(1)
    expect(peticiones[0].get("estado")).toBe("PENDIENTE_REVISION")
    expect(peticiones[0].get("page")).toBe("0")
    expect(screen.getByRole("combobox", { name: "Filtrar por estado" })).toHaveTextContent(
      "Pendiente de revisión",
    )
  })

  it("«Todos los estados» sigue disponible y, al elegirlo, no manda estado", async () => {
    const peticiones: URLSearchParams[] = []
    server.use(
      http.get(apiUrl("/tramites"), ({ request }) => {
        peticiones.push(new URL(request.url).searchParams)
        return paginaTramites([], 0, 0)
      }),
    )
    const user = userEvent.setup()
    render(<TramitesPage />)
    await screen.findByText("No hay trámites en «Pendiente de revisión».")

    await user.click(screen.getByRole("combobox"))
    await user.click(await screen.findByRole("option", { name: "Todos los estados" }))

    expect(await screen.findByText("No hay trámites que mostrar.")).toBeInTheDocument()
    expect(peticiones).toHaveLength(2)
    expect(peticiones[1].has("estado")).toBe(false)
    expect(peticiones[1].get("page")).toBe("0")
    expect(screen.getByRole("combobox", { name: "Filtrar por estado" })).toHaveTextContent(
      "Todos los estados",
    )
  })
})

/** `completo` describe lo ESCRITO (`crotalIndicado`), como en la API real; por defecto, en estos
 * datos de prueba, solo lo es el que empieza por "ES". */
function crotal(
  crotalIndicado: string,
  crotal: string,
  resolucion: string,
  completo: boolean = crotalIndicado.startsWith("ES"),
) {
  return {
    crotalIndicado,
    crotal,
    completo,
    animalId: resolucion === "EN_INVENTARIO" ? 8 : null,
    enInventario: resolucion === "EN_INVENTARIO",
    resolucion,
  }
}

describe("TramitesPage: crotal no encontrado e incompleto (completo, punto 5)", () => {
  beforeEach(() => {
    server.use(
      http.get(apiUrl("/tramites"), () =>
        paginaTramites(
          [
            tramite(1, {
              crotales: [
                crotal("4321", "4321", "NO_ENCONTRADO", false),
                crotal("ES000000000004", "ES000000000004", "NO_ENCONTRADO", true),
                crotal("8765", "8765", "NO_ENCONTRADO", false),
              ],
            }),
          ],
          1,
          1,
        ),
      ),
    )
  })

  it("en la fila: ámbar con «· incompleto» si lo escrito no es completo; neutro si lo es", async () => {
    render(<TramitesPage />)
    const fila = (await screen.findByText("4321")).closest("li")!
    expect(within(fila).getByText("No está en el inventario · incompleto")).toHaveClass("bg-warning")
    const completoFila = screen.getByText("ES000000000004").closest("li")!
    expect(within(completoFila).getByText("No está en el inventario")).toHaveClass("border-border")
    expect(within(completoFila).queryByText(/incompleto/)).not.toBeInTheDocument()
  })

  it("en el desplegable «+N más» también", async () => {
    const user = userEvent.setup()
    render(<TramitesPage />)
    await user.click(await screen.findByRole("button", { name: "+1 más" }))
    const fila = screen.getByText("8765").closest("li")!
    expect(within(fila).getByText("No está en el inventario · incompleto")).toHaveClass("bg-warning")
  })
})

describe("TramitesPage: «+N más» despliega los crotales (Task 10)", () => {
  beforeEach(() => {
    server.use(
      http.get(apiUrl("/tramites"), () =>
        paginaTramites(
          [
            tramite(1, {
              crotales: [
                crotal("111111", "ES000000111111", "EN_INVENTARIO"),
                crotal("222222", "222222", "AMBIGUO"),
                crotal("333333", "ES000000333333", "EN_INVENTARIO"),
                crotal("ES000000000004", "ES000000000004", "NO_ENCONTRADO"),
              ],
            }),
          ],
          1,
          1,
        ),
      ),
      http.get(apiUrl("/tramites/:id"), ({ params }) => HttpResponse.json(detalle(Number(params.id)))),
    )
  })

  it("es un botón «+2 más» plegado, con la clase de foco del sistema", async () => {
    render(<TramitesPage />)
    const boton = await screen.findByRole("button", { name: "+2 más" })
    expect(boton).toHaveAttribute("aria-expanded", "false")
    expect(boton).toHaveClass("text-muted-foreground", "focus-visible:ring-3")
    expect(screen.queryByText("ES000000333333")).not.toBeInTheDocument()
  })

  it("al pulsarlo enseña el resto con el mismo marcado, sin abrir el modal; luego pliega", async () => {
    const user = userEvent.setup()
    render(<TramitesPage />)
    await user.click(await screen.findByRole("button", { name: "+2 más" }))

    const lista = screen.getByText("ES000000111111").closest("ul")!
    expect(within(lista).getByText("ES000000333333")).toHaveAttribute("title", "Indicado: 333333")
    expect(within(lista).getByText(", Indicado: 333333")).toHaveClass("sr-only")
    expect(within(lista).getByText("ES000000000004")).toBeInTheDocument()
    expect(within(lista).getByText("No está en el inventario")).toBeInTheDocument()
    expect(within(lista).getAllByRole("listitem")).toHaveLength(5)
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()

    const menos = screen.getByRole("button", { name: "Ver menos" })
    expect(menos).toHaveAttribute("aria-expanded", "true")
    await user.click(menos)
    expect(screen.getByRole("button", { name: "+2 más" })).toHaveAttribute("aria-expanded", "false")
    expect(screen.queryByText("ES000000333333")).not.toBeInTheDocument()
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
  })

  it("con Enter también despliega, y no abre el modal", async () => {
    const user = userEvent.setup()
    render(<TramitesPage />)
    const boton = await screen.findByRole("button", { name: "+2 más" })
    boton.focus()
    await user.keyboard("{Enter}")

    expect(screen.getByText("ES000000333333")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Ver menos" })).toHaveAttribute("aria-expanded", "true")
    // Margen para que un modal abierto por error llegara a montarse.
    await new Promise((r) => setTimeout(r, 50))
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
  })
})

describe("TramitesPage: estado de carga y esqueleto (Task 10)", () => {
  it("el estado está siempre montado, fuera de aria-busy; mientras carga hay esqueleto, no una fila de texto", async () => {
    const { promesa, abrir } = puerta()
    server.use(
      http.get(apiUrl("/tramites"), async () => {
        await promesa
        return paginaTramites([tramite(1)], 1, 1)
      }),
    )
    render(<TramitesPage />)

    const estado = screen.getByRole("status")
    expect(estado).toHaveTextContent("Cargando trámites…")
    expect(estado).toHaveClass("sr-only")
    expect(estado.closest("[aria-busy]")).toBeNull()
    const tabla = screen.getByRole("table")
    expect(tabla).toHaveAttribute("aria-busy", "true")
    const filasCuerpo = within(tabla).getAllByRole("row").slice(1)
    expect(filasCuerpo).toHaveLength(5)
    expect(filasCuerpo[0].querySelectorAll("[aria-hidden='true'].animate-pulse, [aria-hidden='true'][class*='animate-pulse']")).toHaveLength(5)
    expect(within(tabla).queryByText("Cargando trámites…")).not.toBeInTheDocument()

    abrir()
    expect(await screen.findByText("#1")).toBeInTheDocument()
    expect(screen.getByRole("status")).toBe(estado)
    expect(estado).toBeEmptyDOMElement()
  })
})
