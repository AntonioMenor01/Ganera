import { act, render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { afterEach, describe, expect, it, vi } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { TramitesPage } from "./TramitesPage"

// Tarjetas-fila por debajo de xl (1280 px); tabla con 1 crotal de 1280 a 1439 px; desde 1440 px, la
// tabla de siempre con 2. jsdom no maqueta: se simula matchMedia con un ancho en px.

/** matchMedia que responde a `(min-width: Nrem)` según un ancho simulado; `cambiar` lo redimensiona. */
function simularAncho(px: number) {
  const oyentes = new Set<() => void>()
  let ancho = px
  vi.stubGlobal(
    "matchMedia",
    vi.fn((media: string) => {
      const rem = /\(min-width:\s*([\d.]+)rem\)/.exec(media)
      if (!rem) throw new Error(`Consulta no prevista en el test: ${media}`)
      return {
        media,
        get matches() {
          return ancho >= Number(rem[1]) * 16
        },
        addEventListener: (_tipo: string, oyente: () => void) => oyentes.add(oyente),
        removeEventListener: (_tipo: string, oyente: () => void) => oyentes.delete(oyente),
      }
    }),
  )
  return {
    cambiar(nuevo: number) {
      ancho = nuevo
      act(() => oyentes.forEach((oyente) => oyente()))
    },
  }
}

function crotal(crotalIndicado: string, crotalResuelto: string, resolucion: string, completo = crotalIndicado.startsWith("ES")) {
  return {
    crotalIndicado,
    crotal: crotalResuelto,
    completo,
    animalId: resolucion === "EN_INVENTARIO" ? 8 : null,
    enInventario: resolucion === "EN_INVENTARIO",
    resolucion,
  }
}

function tramite(id: number, extra: Record<string, unknown> = {}) {
  return {
    id,
    explotacionId: null,
    explotacionCodigoRega: null,
    explotacionNombre: null,
    tipoTramite: "ALTA_NACIMIENTO",
    estado: "PENDIENTE_REVISION",
    motivoError: null,
    crotales: [],
    version: 0,
    origen: null,
    estadoExtraccion: null,
    crotalesDescartados: 0,
    hechoEnOvzEn: null,
    hechoEnOvzPorNombre: null,
    motivoRechazo: null,
    descartable: false,
    ...extra,
  }
}

const CUATRO_CROTALES = [
  crotal("111111", "ES000000111111", "EN_INVENTARIO"),
  crotal("222222", "222222", "AMBIGUO"),
  crotal("333333", "ES000000333333", "EN_INVENTARIO"),
  crotal("ES000000000004", "ES000000000004", "NO_ENCONTRADO"),
]

const COLA = [
  tramite(1, {
    tipoTramite: "BAJA_MUERTE",
    origen: "WHATSAPP",
    estadoExtraccion: "FALLIDA",
    crotalesDescartados: 2,
    explotacionId: 3,
    explotacionCodigoRega: "ES280790000123",
    explotacionNombre: "Finca La Dehesa",
    crotales: CUATRO_CROTALES,
  }),
  tramite(2, { tipoTramite: null, crotales: [crotal("1244", "1244", "SIN_EXPLOTACION", false)] }),
]

function servir(content: unknown[] = COLA, totalPages = 1, number = 0) {
  server.use(
    http.get(apiUrl("/tramites"), () =>
      HttpResponse.json({ content, totalElements: content.length * totalPages, totalPages, number, size: 20 }),
    ),
    http.get(apiUrl("/tramites/:id"), ({ params }) =>
      HttpResponse.json({ ...tramite(Number(params.id)), mensajeOriginal: "alta", whatsapp: null }),
    ),
  )
}

function tarjeta(id: number): HTMLElement {
  return screen.getByRole("button", { name: `Revisar trámite #${id}` }).closest("li")!
}

describe("TramitesPage: tarjetas-fila por debajo de xl", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("a 375 px no hay tabla: una lista «Trámites» con una tarjeta por trámite", async () => {
    simularAncho(375)
    servir()
    render(<TramitesPage />)

    const lista = await screen.findByRole("list", { name: "Trámites" })
    expect(window.matchMedia).toHaveBeenCalledWith("(min-width: 80rem)")
    expect(screen.queryByRole("table")).not.toBeInTheDocument()
    expect(screen.queryByRole("columnheader")).not.toBeInTheDocument()
    expect(within(lista).getAllByRole("button", { name: /^Revisar trámite #/ })).toHaveLength(2)
    expect(tarjeta(1).parentElement).toBe(lista)
  })

  it("la tarjeta lleva estado, tipo, «REGA · nombre», origen, avisos y 1 crotal + «+N más»", async () => {
    simularAncho(375)
    servir()
    render(<TramitesPage />)
    await screen.findByRole("list", { name: "Trámites" })

    const primera = tarjeta(1)
    expect(within(primera).getByText("Pendiente de revisión")).toBeInTheDocument()
    expect(within(primera).getByText("Baja por muerte")).toBeInTheDocument()
    // Origen anunciado una sola vez, fuera del botón.
    expect(within(primera).getAllByText("Recibido por WhatsApp")).toHaveLength(1)
    expect(within(primera).getByText("No se ha podido extraer")).toBeInTheDocument()
    expect(within(primera).getByText("2 descartados")).toBeInTheDocument()

    // Explotación: REGA y nombre visibles, el nombre recortable con «…» y con title para el ratón.
    const rega = within(primera).getByText("ES280790000123")
    const nombre = within(primera).getByText("Finca La Dehesa")
    expect(nombre).toHaveClass("truncate")
    expect(nombre).toHaveAttribute("title", "Finca La Dehesa")
    expect(rega).toHaveClass("shrink-0")
    expect(rega.parentElement).toBe(nombre.parentElement)
    // El «·» es solo visual; el lector oye una pausa («, »), como el sr-only de la tabla.
    const separador = within(primera).getByText((_, el) => el?.textContent === " · ")
    expect(separador).toHaveAttribute("aria-hidden", "true")
    expect(separador.nextElementSibling).toHaveTextContent(/^, $/, { normalizeWhitespace: false })
    expect(separador.nextElementSibling).toHaveClass("sr-only")
    expect(separador.nextElementSibling!.nextElementSibling).toBe(nombre)
    expect(within(primera).getAllByText(/Finca La Dehesa/)).toHaveLength(1)

    // Un crotal a la vista y el resto resumido.
    expect(within(primera).getByText("ES000000111111")).toBeInTheDocument()
    expect(within(primera).queryByText("222222")).not.toBeInTheDocument()
    const resto = within(primera).getByRole("button", { name: "+3 más: 3 crotales más" })
    expect(resto).toHaveTextContent(/^\+3 más$/)
    expect(resto).toHaveAttribute("aria-expanded", "false")

    const segunda = tarjeta(2)
    expect(within(segunda).getByText("Sin determinar")).toBeInTheDocument()
    expect(within(segunda).getByText("Sin asignar")).toBeInTheDocument()
    expect(within(segunda).getByText("1244")).toBeInTheDocument()
    expect(within(segunda).queryByRole("button", { name: /más/ })).not.toBeInTheDocument()
  })

  it("explotación sin nombre: solo el REGA, sin «·»", async () => {
    simularAncho(375)
    servir([tramite(1, { explotacionId: 3, explotacionCodigoRega: "ES280790000123", explotacionNombre: "  " })])
    render(<TramitesPage />)
    await screen.findByRole("list", { name: "Trámites" })
    expect(within(tarjeta(1)).getByText("ES280790000123").parentElement).toHaveTextContent(/^ES280790000123$/)
  })

  it("tocar la tarjeta abre la revisión; «+N más» despliega y pliega sin abrirla", async () => {
    simularAncho(375)
    servir()
    const user = userEvent.setup()
    render(<TramitesPage />)
    await screen.findByRole("list", { name: "Trámites" })

    await user.click(within(tarjeta(1)).getByRole("button", { name: "+3 más: 3 crotales más" }))
    expect(within(tarjeta(1)).getByText("ES000000000004")).toBeInTheDocument()
    const menos = within(tarjeta(1)).getByRole("button", { name: "Ver menos" })
    expect(menos).toHaveAttribute("aria-expanded", "true")
    await user.click(menos)
    expect(within(tarjeta(1)).queryByText("ES000000000004")).not.toBeInTheDocument()
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()

    await user.click(within(tarjeta(2)).getByText("Sin asignar"))
    const dialogo = await screen.findByRole("dialog")
    expect(dialogo).toHaveAccessibleName("Trámite #2")
  })

  it("orden de tabulación: #1 → «+N más» de #1 → #2", async () => {
    simularAncho(375)
    servir()
    const user = userEvent.setup()
    render(<TramitesPage />)
    await screen.findByRole("list", { name: "Trámites" })

    const revisar1 = screen.getByRole("button", { name: "Revisar trámite #1" })
    revisar1.focus()
    await user.tab()
    expect(screen.getByRole("button", { name: "+3 más: 3 crotales más" })).toHaveFocus()
    await user.tab()
    expect(screen.getByRole("button", { name: "Revisar trámite #2" })).toHaveFocus()
  })

  it("mientras carga: esqueleto en la lista con aria-busy y el estado fuera; vacío con su frase", async () => {
    simularAncho(375)
    let abrir!: () => void
    const puerta = new Promise<void>((r) => (abrir = r))
    server.use(
      http.get(apiUrl("/tramites"), async () => {
        await puerta
        return HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })
      }),
    )
    render(<TramitesPage />)

    const lista = screen.getByRole("list", { name: "Trámites" })
    expect(lista).toHaveAttribute("aria-busy", "true")
    expect(lista.querySelectorAll("[aria-hidden='true'][class*='animate-pulse']").length).toBeGreaterThan(0)
    // Las tarjetas del esqueleto no se anuncian como elementos vacíos de la lista.
    expect(within(lista).queryAllByRole("listitem")).toHaveLength(0)
    expect(screen.getByRole("status").closest("[aria-busy]")).toBeNull()

    abrir()
    expect(await within(lista).findByText("No hay trámites en «Pendiente de revisión».")).toBeInTheDocument()
    expect(lista).toHaveAttribute("aria-busy", "false")
  })

  it("el error con «Reintentar» y la paginación siguen igual", async () => {
    simularAncho(375)
    server.use(http.get(apiUrl("/tramites"), () => new HttpResponse(null, { status: 500 })))
    const user = userEvent.setup()
    render(<TramitesPage />)
    expect(await screen.findByText("No se han podido cargar los trámites")).toBeInTheDocument()

    servir(COLA, 3)
    await user.click(screen.getByRole("button", { name: "Reintentar" }))
    await screen.findByRole("list", { name: "Trámites" })
    expect(screen.getByRole("navigation", { name: "Paginación" })).toHaveTextContent("Página 1 de 3")
  })

  // A 1024 la tabla con 1 crotal no cabe (869 px en 718 tras la barra lateral): el corte es xl.
  it.each([768, 1024, 1279])("a %i px también son tarjetas (el corte es xl)", async (px) => {
    simularAncho(px)
    servir()
    render(<TramitesPage />)
    expect(await screen.findByRole("list", { name: "Trámites" })).toBeInTheDocument()
    expect(screen.queryByRole("table")).not.toBeInTheDocument()
  })
})

describe("TramitesPage: tabla con 1 crotal de 1280 a 1439 px; desde 1440, 2", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it.each([1280, 1439])("a %i px: tabla de 5 columnas, 1 crotal y «+3 más»", async (px) => {
    simularAncho(px)
    servir()
    render(<TramitesPage />)

    const tabla = await screen.findByRole("table")
    expect(within(tabla).getAllByRole("columnheader")).toHaveLength(5)
    expect(screen.queryByRole("list", { name: "Trámites" })).not.toBeInTheDocument()
    expect(within(tabla).getByText("ES000000111111")).toBeInTheDocument()
    expect(within(tabla).queryByText("222222")).not.toBeInTheDocument()
    expect(within(tabla).getByRole("button", { name: "+3 más: 3 crotales más" })).toHaveTextContent(/^\+3 más$/)
    // En la tabla, la explotación sigue como estaba: el nombre solo para lector de pantalla.
    expect(within(tabla).getByText(", Finca La Dehesa")).toHaveClass("sr-only")
  })

  it("a 1440 px: 2 crotales y «+2 más», en singular «+1 más: 1 crotal más» si queda uno", async () => {
    simularAncho(1440)
    servir([
      tramite(1, { crotales: CUATRO_CROTALES }),
      tramite(2, { crotales: CUATRO_CROTALES.slice(0, 3) }),
    ])
    render(<TramitesPage />)

    const tabla = await screen.findByRole("table")
    expect(within(tabla).getAllByText("222222")).toHaveLength(2)
    expect(within(tabla).getByRole("button", { name: "+2 más: 2 crotales más" })).toHaveTextContent(/^\+2 más$/)
    expect(within(tabla).getByRole("button", { name: "+1 más: 1 crotal más" })).toHaveTextContent(/^\+1 más$/)
  })

  it("al cruzar los cortes cambia el marcado y se conservan el filtro y la página", async () => {
    const pantalla = simularAncho(1440)
    servir(COLA, 3)
    const user = userEvent.setup()
    render(<TramitesPage />)
    await screen.findByRole("table")
    await user.click(screen.getByRole("button", { name: "Siguiente" }))
    expect(await screen.findByText("Página 2 de 3")).toBeInTheDocument()

    pantalla.cambiar(375)
    expect(screen.getByRole("list", { name: "Trámites" })).toBeInTheDocument()
    expect(screen.queryByRole("table")).not.toBeInTheDocument()
    expect(screen.getByText("Página 2 de 3")).toBeInTheDocument()
    expect(screen.getByRole("combobox", { name: "Filtrar por estado" })).toHaveTextContent("Pendiente de revisión")

    pantalla.cambiar(1280)
    expect(screen.getByRole("table")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "+3 más: 3 crotales más" })).toBeInTheDocument()
    expect(screen.getByText("Página 2 de 3")).toBeInTheDocument()
  })
})
