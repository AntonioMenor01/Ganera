import { render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { MemoryRouter } from "react-router-dom"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import type { Explotacion } from "@/features/explotaciones/types"
import type { EstadoTramite, Tramite, TramiteCrotal } from "./types"
import { TramiteReviewDialog } from "./TramiteReviewDialog"

const DETALLE = {
  id: 7,
  tipoTramite: "ALTA",
  estado: "PENDIENTE_REVISION",
  motivoError: null,
  explotacionId: 3,
  explotacionCodigoRega: "ES123",
  explotacionNombre: "La Dehesa",
  mensajeOriginal: "alta del 1234",
  crotales: [] as TramiteCrotal[],
  version: 0,
}

type Detalle = typeof DETALLE

const EXPLOTACIONES: Explotacion[] = [
  { id: 3, codigoRega: "ES123", nombre: "La Dehesa", ganaderoId: 1, nombreGanadero: "Ana Martínez" },
  { id: 5, codigoRega: "ES555", nombre: "El Encinar", ganaderoId: 2, nombreGanadero: "Benito Ruiz" },
  { id: 9, codigoRega: "ES999", nombre: "Los Olivos", ganaderoId: 2, nombreGanadero: "Benito Ruiz" },
]

const TEXTO_SUSCRIPCION =
  "Tu suscripción no permite aprobar trámites ahora mismo (trial expirado o suspendida). Actualiza tu suscripción en Facturación."

function crotal(crotalIndicado: string, parcial: Partial<TramiteCrotal> = {}): TramiteCrotal {
  return {
    crotalIndicado,
    crotal: crotalIndicado,
    animalId: null,
    enInventario: false,
    resolucion: "NO_ENCONTRADO",
    ...parcial,
  }
}

function detalle(parcial: Partial<Detalle> = {}): Detalle {
  return { ...DETALLE, ...parcial }
}

/** Lo que devuelven aprobar/rechazar: el DTO del listado (`TramiteResponse`), con la etiqueta de
 * la explotación (m2 de la revisión de T3). */
function respuestaLista(d: Detalle) {
  return {
    id: d.id,
    explotacionId: d.explotacionId,
    explotacionCodigoRega: d.explotacionCodigoRega,
    explotacionNombre: d.explotacionNombre,
    tipoTramite: d.tipoTramite,
    estado: d.estado as EstadoTramite,
    motivoError: d.motivoError,
    crotales: d.crotales,
    version: d.version,
  } satisfies Tramite
}

/** GET /tramites/7 que devuelve, en orden, las respuestas dadas (la última se repite). */
function detallesEnOrden(...respuestas: (() => Response)[]) {
  let llamadas = 0
  server.use(
    http.get(apiUrl("/tramites/7"), () => {
      const respuesta = respuestas[Math.min(llamadas, respuestas.length - 1)]
      llamadas += 1
      return respuesta()
    }),
  )
  return { llamadas: () => llamadas }
}

function puerta() {
  let abrir!: () => void
  const promesa = new Promise<void>((resolve) => {
    abrir = resolve
  })
  return { promesa, abrir }
}

/**
 * GET /explotaciones?q= que se comporta como el backend (T4): `q` recortada, "contiene" sin
 * distinguir mayúsculas sobre código REGA, nombre o ganadero como UNA sola cadena, primera página
 * de `size`, `totalElements` real. Devuelve las URLs pedidas, en orden.
 */
function servidorExplotaciones(lista: Explotacion[] = EXPLOTACIONES) {
  const pedidas: URL[] = []
  server.use(
    http.get(apiUrl("/explotaciones"), ({ request }) => {
      const url = new URL(request.url)
      pedidas.push(url)
      const q = (url.searchParams.get("q") ?? "").trim().toLowerCase()
      const coinciden = lista.filter(
        (e) => !q || [e.codigoRega, e.nombre, e.nombreGanadero].some((campo) => campo.toLowerCase().includes(q)),
      )
      const size = Number(url.searchParams.get("size") ?? 20)
      return HttpResponse.json({
        content: coinciden.slice(0, size),
        totalElements: coinciden.length,
        totalPages: Math.ceil(coinciden.length / size),
        number: 0,
        size,
      })
    }),
  )
  return pedidas
}

// Cualquier test que abra el combobox tiene un backend de explotaciones por defecto.
beforeEach(() => {
  servidorExplotaciones()
})

interface Opciones {
  tramiteId?: number | null
  onClose?: () => void
  onCambiado?: () => void
}

function renderDialog(opciones: Opciones = {}) {
  const props = {
    tramiteId: opciones.tramiteId === undefined ? 7 : opciones.tramiteId,
    onClose: opciones.onClose ?? vi.fn(),
    onCambiado: opciones.onCambiado ?? vi.fn(),
  }
  const utils = render(
    <MemoryRouter>
      <TramiteReviewDialog {...props} />
    </MemoryRouter>,
  )
  return { ...utils, props }
}

/** Espera a que el detalle esté pintado (las columnas existen). */
async function dialogoListo() {
  const dialogo = await screen.findByRole("dialog")
  await within(dialogo).findByRole("heading", { name: "Mensaje de WhatsApp" })
  return dialogo
}

function boton(dialogo: HTMLElement, nombre: string | RegExp) {
  return within(dialogo).getByRole("button", { name: nombre })
}

function filasCrotales(dialogo: HTMLElement) {
  return within(within(dialogo).getByRole("list", { name: "Crotales" })).getAllByRole("listitem")
}

// ---------------------------------------------------------------------------------------------

describe("TramiteReviewDialog: etiquetas legibles", () => {
  it("muestra la etiqueta del estado y del tipo, nunca el enum", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () =>
        HttpResponse.json({ ...DETALLE, estado: "ERROR_OVZ", tipoTramite: "DEMORA" }),
      ),
    )
    renderDialog()
    expect(await screen.findByText("Error en OVZ.net")).toHaveClass("bg-danger")
    expect(screen.getByText("Demora")).toBeInTheDocument()
    expect(screen.queryByText("ERROR_OVZ")).not.toBeInTheDocument()
    expect(screen.queryByText("DEMORA")).not.toBeInTheDocument()
  })

  it("sin tipo todavía, lo dice en vez de dejarlo en blanco", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json({ ...DETALLE, tipoTramite: null })),
    )
    renderDialog()
    expect(await screen.findByText("Sin determinar todavía")).toBeInTheDocument()
    expect(screen.getByText("Pendiente de revisión")).toBeInTheDocument()
  })
})

describe("TramiteReviewDialog: errores visibles", () => {
  it("si falla la carga del detalle, lo dice y se puede reintentar (antes el modal se quedaba en blanco)", async () => {
    detallesEnOrden(
      () => new HttpResponse(null, { status: 500 }),
      () => HttpResponse.json(DETALLE),
    )
    const user = userEvent.setup()
    renderDialog()
    expect(await screen.findByText("No se ha podido cargar el trámite")).toBeInTheDocument()
    expect(
      screen.getByText("Ha fallado algo en el servidor. Inténtalo de nuevo; si se repite, avísanos."),
    ).toBeInTheDocument()
    await user.click(screen.getByRole("button", { name: "Reintentar" }))
    expect(await dialogoListo()).toBeInTheDocument()
  })

  it("un 404 del detalle da el texto contextual del trámite", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => new HttpResponse(null, { status: 404 })))
    renderDialog()
    expect(
      await screen.findByText("Este trámite ya no existe o no es de tu gestoría."),
    ).toBeInTheDocument()
  })

  it("el 400 de aprobar muestra el motivo del backend, no un texto genérico", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)),
      http.post(apiUrl("/tramites/7/aprobar"), () =>
        HttpResponse.json({ motivo: "La versión es obligatoria." }, { status: 400 }),
      ),
    )
    const user = userEvent.setup()
    renderDialog()
    await screen.findByText("Trámite #7")
    await user.click(await screen.findByRole("button", { name: "Aprobar" }))
    expect(await screen.findByText("La versión es obligatoria.")).toBeInTheDocument()
  })

  it("el 403 de aprobar sigue mostrando el texto de la suscripción", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)),
      http.post(apiUrl("/tramites/7/aprobar"), () => new HttpResponse(null, { status: 403 })),
    )
    const user = userEvent.setup()
    renderDialog()
    await screen.findByText("Trámite #7")
    await user.click(await screen.findByRole("button", { name: "Aprobar" }))
    expect(
      await screen.findByText(/Tu suscripción no permite aprobar trámites ahora mismo/),
    ).toBeInTheDocument()
  })
})

describe("TramiteReviewDialog: aprobar y rechazar envían la versión (Task 9a, mini-prompt tras A2)", () => {
  it("aprobar y rechazar (confirmado) mandan {version} del detalle cargado", async () => {
    const cuerpos: string[] = []
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json({ ...DETALLE, version: 3 })),
      http.post(apiUrl("/tramites/7/aprobar"), async ({ request }) => {
        cuerpos.push(`aprobar:${await request.text()}`)
        return HttpResponse.json({ ...DETALLE, estado: "APROBADO", version: 4 })
      }),
      http.post(apiUrl("/tramites/7/rechazar"), async ({ request }) => {
        cuerpos.push(`rechazar:${await request.text()}`)
        return HttpResponse.json({ ...DETALLE, estado: "RECHAZADO", version: 4 })
      }),
    )
    const onCambiado = vi.fn()
    const user = userEvent.setup()
    const { unmount } = renderDialog({ onCambiado })
    await dialogoListo()
    await user.click(screen.getByRole("button", { name: "Aprobar" }))
    await vi.waitFor(() => expect(onCambiado).toHaveBeenCalledTimes(1))
    unmount()

    renderDialog({ onCambiado })
    await dialogoListo()
    await user.click(screen.getByRole("button", { name: "Rechazar" }))
    await user.click(screen.getByRole("button", { name: "Sí, rechazar" }))
    await vi.waitFor(() => expect(onCambiado).toHaveBeenCalledTimes(2))

    expect(cuerpos).toEqual(['aprobar:{"version":3}', 'rechazar:{"version":3}'])
  })
})

// ---------------------------------------------------------------------------------------------

describe("TramiteReviewDialog: composición (mesa de cotejo)", () => {
  it("dos columnas: el mensaje de WhatsApp primero (izquierda / arriba) y los datos después", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    renderDialog()
    const dialogo = await dialogoListo()
    expect(dialogo).toHaveAccessibleName("Trámite #7")

    const mensaje = within(dialogo).getByRole("region", { name: "Mensaje de WhatsApp" })
    const datos = within(dialogo).getByRole("region", { name: "Datos del trámite" })
    expect(mensaje.querySelector("blockquote")).toHaveTextContent("alta del 1234")
    expect(mensaje.querySelector("blockquote")).toHaveClass("bg-muted")
    // Mismo contenedor, mensaje antes que datos en el DOM: arriba en móvil, izquierda en escritorio.
    const columnas = mensaje.parentElement!
    expect(columnas).toBe(datos.parentElement)
    expect(columnas.firstElementChild).toBe(mensaje)
    expect(columnas).toHaveClass("grid", "grid-cols-1", "md:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]")
  })

  it("sin mensaje, un texto tranquilo y cierto", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ mensajeOriginal: null as unknown as string }))),
    )
    renderDialog()
    const dialogo = await dialogoListo()
    expect(
      within(dialogo).getByText("Aún no hay mensaje: este trámite no llegó por WhatsApp."),
    ).toBeInTheDocument()
  })

  it("móvil: pantalla completa y columnas apiladas; desde md, modal ancho con cabecera y pie fijos", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    renderDialog()
    const dialogo = await dialogoListo()
    // jsdom no calcula layout: se comprueba la estructura de clases.
    expect(dialogo).toHaveClass("top-0", "left-0", "h-dvh", "w-full", "max-w-none", "rounded-none", "flex-col")
    // Finish review #2: arriba fijo (no centrado), para que el panel no salte al crecer.
    expect(dialogo).toHaveClass("md:max-w-4xl", "md:h-auto", "md:rounded-xl", "md:top-[8dvh]", "md:max-h-[84dvh]")
    expect(dialogo).not.toHaveClass("md:top-1/2")
    expect(dialogo).not.toHaveClass("md:-translate-y-1/2")
    expect(dialogo).not.toHaveClass("grid")
    const cuerpo = dialogo.querySelector("[data-cuerpo-revision]")!
    expect(cuerpo).toHaveClass("min-h-0", "flex-1", "overflow-y-auto")
    // El pie está fuera del cuerpo que hace scroll.
    expect(cuerpo.contains(boton(dialogo, "Aprobar"))).toBe(false)
  })

  it("el foco va al título al abrir", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    renderDialog()
    const dialogo = await dialogoListo()
    await waitFor(() => expect(within(dialogo).getByRole("heading", { name: "Trámite #7" })).toHaveFocus())
  })

  it("mientras carga, un esqueleto con aviso para lector de pantalla y el número ya en el título", async () => {
    const espera = puerta()
    server.use(
      http.get(apiUrl("/tramites/7"), async () => {
        await espera.promesa
        return HttpResponse.json(DETALLE)
      }),
    )
    renderDialog()
    const dialogo = await screen.findByRole("dialog")
    expect(within(dialogo).getByText("Cargando trámite…")).toHaveAttribute("role", "status")
    expect(dialogo).toHaveAccessibleName("Trámite #7")
    expect(within(dialogo).queryByRole("button", { name: "Aprobar" })).not.toBeInTheDocument()
    espera.abrir()
    await dialogoListo()
  })
})

describe("TramiteReviewDialog: solo lectura fuera de PENDIENTE_REVISION (decisión 7)", () => {
  for (const estado of [
    "PENDIENTE_EXTRACCION",
    "APROBADO",
    "EN_PROCESO",
    "EJECUTADO_OVZ",
    "ERROR_OVZ",
    "RECHAZADO",
  ]) {
    it(`${estado}: mismos datos en texto, sin campos y sin ningún botón de acción`, async () => {
      server.use(
        http.get(apiUrl("/tramites/7"), () =>
          HttpResponse.json(
            detalle({
              estado,
              crotales: [crotal("1234", { crotal: "ES010000001234", resolucion: "EN_INVENTARIO", enInventario: true, animalId: 1 })],
            }),
          ),
        ),
      )
      renderDialog()
      const dialogo = await dialogoListo()
      expect(within(dialogo).getByText("ES123 · La Dehesa")).toBeInTheDocument()
      expect(within(dialogo).getByText("Alta")).toBeInTheDocument()
      expect(within(dialogo).getByText("1234")).toBeInTheDocument()
      expect(within(dialogo).getByText("ES010000001234")).toBeInTheDocument()
      expect(within(dialogo).getByText("En inventario")).toBeInTheDocument()
      expect(within(dialogo).queryByRole("textbox")).not.toBeInTheDocument()
      expect(within(dialogo).queryByRole("combobox")).not.toBeInTheDocument()
      // El único botón es el de cerrar el modal.
      expect(within(dialogo).getAllByRole("button").map((b) => b.getAttribute("aria-label") ?? b.textContent)).toEqual(["Cerrar"])
    })
  }

  it("PENDIENTE_REVISION: campos editables y las tres acciones", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ crotales: [crotal("1234")] }))),
    )
    renderDialog()
    const dialogo = await dialogoListo()
    expect(within(dialogo).getByRole("combobox", { name: "Explotación" })).toHaveValue("ES123 · La Dehesa")
    expect(within(dialogo).getByRole("combobox", { name: "Tipo de trámite" })).toHaveTextContent("Alta")
    expect(within(dialogo).getByRole("textbox", { name: "Crotal 1" })).toHaveValue("1234")
    expect(boton(dialogo, "Rechazar")).toBeEnabled()
    expect(boton(dialogo, "Guardar")).toBeDisabled()
    expect(boton(dialogo, "Aprobar")).toBeEnabled()
    expect(within(dialogo).queryByText("Guarda antes de aprobar")).not.toBeInTheDocument()
  })
})

describe("TramiteReviewDialog: explotación (combobox con búsqueda en el backend, T4)", () => {
  const esperar = (ms: number) => new Promise((r) => setTimeout(r, ms))

  it("abrir el modal no pide explotaciones (D3a): solo el desplegable abierto pide", async () => {
    const pedidas = servidorExplotaciones()
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    renderDialog()
    const dialogo = await dialogoListo()
    await esperar(400)
    expect(pedidas).toHaveLength(0)
    // La guardada se ve igualmente: sale del detalle, no de una lista.
    expect(within(dialogo).getByRole("combobox", { name: "Explotación" })).toHaveValue("ES123 · La Dehesa")
  })

  it("al abrir pide la primera página de 20 sin q (la etiqueta de la elegida cuenta como vacía, D3c/D3d)", async () => {
    const pedidas = servidorExplotaciones()
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(within(dialogo).getByRole("combobox", { name: "Explotación" }))
    expect(await screen.findAllByRole("option")).toHaveLength(EXPLOTACIONES.length)
    expect(pedidas).toHaveLength(1)
    expect(pedidas[0].searchParams.has("q")).toBe(false)
    expect(pedidas[0].searchParams.get("size")).toBe("20")
    expect(pedidas[0].searchParams.get("page")).toBe("0")
    expect(screen.getByRole("option", { name: /ES123 · La Dehesa, ganadero: Ana Martínez/ })).toBeInTheDocument()
  })

  it("teclear lanza UNA petición con lo escrito tras la espera; elegir cambia el campo y queda sin guardar; sin opción para vaciar", async () => {
    const pedidas = servidorExplotaciones()
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    const campo = within(dialogo).getByRole("combobox", { name: "Explotación" })
    await user.click(campo)
    await screen.findAllByRole("option")

    await user.clear(campo)
    await user.type(campo, "benito")
    await waitFor(() =>
      expect(screen.getAllByRole("option").map((o) => o.textContent)).toEqual([
        expect.stringContaining("ES555"),
        expect.stringContaining("ES999"),
      ]),
    )
    // Ni "b", ni "be"…: solo la apertura y la consulta final.
    expect(pedidas.map((u) => u.searchParams.get("q"))).toEqual([null, "benito"])

    await user.click(screen.getByRole("option", { name: /Los Olivos/ }))
    expect(campo).toHaveValue("ES999 · Los Olivos")
    expect(within(dialogo).getByText("Guarda antes de aprobar")).toBeInTheDocument()
    expect(within(dialogo).getByText("Sin guardar")).toBeInTheDocument()
    expect(boton(dialogo, "Guardar")).toBeEnabled()
    expect(boton(dialogo, "Aprobar")).toBeDisabled()

    // Reabrir con la recién elegida (sin guardar): su etiqueta cuenta como consulta vacía (D3d).
    await user.click(campo)
    await screen.findAllByRole("option")
    await waitFor(() => expect(pedidas).toHaveLength(3))
    expect(pedidas[2].searchParams.has("q")).toBe(false)
  })

  it("sin explotación guardada, el campo lo dice (sin predecir si se puede aprobar)", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () =>
        HttpResponse.json(detalle({ explotacionId: null as unknown as number, explotacionCodigoRega: null as unknown as string, explotacionNombre: null as unknown as string })),
      ),
    )
    renderDialog()
    const dialogo = await dialogoListo()
    const campo = within(dialogo).getByRole("combobox", { name: "Explotación" })
    expect(campo).toHaveValue("")
    expect(campo).toHaveAttribute("placeholder", "Sin asignar · busca por código REGA, nombre o ganadero")
  })

  it("mientras busca, un aviso anunciado; sin coincidencias lo dice", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    const campo = within(dialogo).getByRole("combobox", { name: "Explotación" })
    await user.click(campo)
    await screen.findAllByRole("option")
    await user.clear(campo)
    await user.type(campo, "zzz")
    const buscando = await screen.findByText("Buscando explotaciones…")
    expect(buscando.closest("[role=status]")).not.toBeNull()
    expect(await screen.findByText("Ninguna explotación coincide con lo que has escrito.")).toBeInTheDocument()
    expect(screen.queryAllByRole("option")).toHaveLength(0)
    expect(screen.queryByText("Buscando explotaciones…")).not.toBeInTheDocument()
  })

  it("error de búsqueda: dentro del desplegable, con Reintentar que vuelve a pedir; la guardada sigue a la vista (D3f)", async () => {
    let fallar = true
    let peticiones = 0
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)),
      http.get(apiUrl("/explotaciones"), () => {
        peticiones += 1
        return fallar
          ? HttpResponse.error()
          : HttpResponse.json({ content: EXPLOTACIONES, totalElements: 3, totalPages: 1, number: 0, size: 20 })
      }),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    const campo = within(dialogo).getByRole("combobox", { name: "Explotación" })
    await user.click(campo)
    expect(await screen.findByText("No se ha podido conectar con Ganera", { exact: false })).toBeInTheDocument()
    expect(screen.queryByText("Ninguna explotación coincide con lo que has escrito.")).not.toBeInTheDocument()
    expect(campo).toHaveValue("ES123 · La Dehesa")

    fallar = false
    await user.click(screen.getByRole("button", { name: "Reintentar" }))
    expect(await screen.findAllByRole("option")).toHaveLength(EXPLOTACIONES.length)
    expect(peticiones).toBe(2)
    expect(campo).toHaveValue("ES123 · La Dehesa")
    expect(within(dialogo).queryByText("Sin guardar")).not.toBeInTheDocument()
  })

  it("más de 100 caracteres se envían y el 400 enseña su motivo (D3e)", async () => {
    const largo = "x".repeat(101)
    const pedidas: (string | null)[] = []
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)),
      http.get(apiUrl("/explotaciones"), ({ request }) => {
        const q = new URL(request.url).searchParams.get("q")
        pedidas.push(q)
        return q && q.length > 100
          ? HttpResponse.json({ motivo: "La búsqueda no puede tener más de 100 caracteres." }, { status: 400 })
          : HttpResponse.json({ content: EXPLOTACIONES, totalElements: 3, totalPages: 1, number: 0, size: 20 })
      }),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    const campo = within(dialogo).getByRole("combobox", { name: "Explotación" })
    expect(campo).not.toHaveAttribute("maxlength")
    await user.click(campo)
    await screen.findAllByRole("option")
    await user.clear(campo)
    // Pegar (no teclear): llega de una vez.
    await user.paste(largo)
    expect(await screen.findByText("La búsqueda no puede tener más de 100 caracteres.")).toBeInTheDocument()
    expect(pedidas.at(-1)).toBe(largo)
  })

  it("con más coincidencias que las mostradas, «Hay N coincidencias; escribe para acotar.» en una región status (D3c)", async () => {
    const muchas: Explotacion[] = Array.from({ length: 137 }, (_, i) => ({
      id: 1000 + i,
      codigoRega: `ES${String(i).padStart(12, "0")}`,
      nombre: `Finca ${i}`,
      ganaderoId: 1,
      nombreGanadero: "Ana",
    }))
    servidorExplotaciones(muchas)
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    const campo = within(dialogo).getByRole("combobox", { name: "Explotación" })
    await user.click(campo)
    const aviso = await screen.findByText("Hay 137 coincidencias; escribe para acotar.")
    expect(aviso.closest("[role=status]")).not.toBeNull()
    expect(screen.getAllByRole("option")).toHaveLength(20)

    await user.clear(campo)
    await user.type(campo, "Finca 136")
    await waitFor(() => expect(screen.getAllByRole("option")).toHaveLength(1))
    expect(screen.queryByText(/coincidencias; escribe para acotar/)).not.toBeInTheDocument()
  })

  it("borrar el texto no vacía la explotación guardada (H5)", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    const campo = within(dialogo).getByRole("combobox", { name: "Explotación" })
    await user.clear(campo)
    await user.keyboard("{Escape}")
    await user.tab()
    expect(within(dialogo).queryByText("Guarda antes de aprobar")).not.toBeInTheDocument()
    expect(boton(dialogo, "Aprobar")).toBeEnabled()
  })

  it("Esc tras escribir devuelve la etiqueta de la elegida, y reabrir pide la primera página sin q (D3d)", async () => {
    const pedidas = servidorExplotaciones()
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    const campo = within(dialogo).getByRole("combobox", { name: "Explotación" })
    await user.click(campo)
    await screen.findAllByRole("option")
    await user.clear(campo)
    await user.type(campo, "zzz")
    await screen.findByText("Ninguna explotación coincide con lo que has escrito.")
    await user.keyboard("{Escape}")
    expect(campo).toHaveValue("ES123 · La Dehesa")
    await user.click(campo)
    await screen.findAllByRole("option")
    expect(pedidas.map((u) => u.searchParams.get("q"))).toEqual([null, "zzz", null])
  })

  it("Esc con la lista abierta cierra solo la lista, no el modal", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    const onClose = vi.fn()
    const user = userEvent.setup()
    renderDialog({ onClose })
    const dialogo = await dialogoListo()
    await user.click(within(dialogo).getByRole("combobox", { name: "Explotación" }))
    expect((await screen.findAllByRole("option")).length).toBeGreaterThan(0)
    await user.keyboard("{Escape}")
    await waitFor(() => expect(screen.queryAllByRole("option")).toHaveLength(0))
    expect(onClose).not.toHaveBeenCalled()
    expect(screen.getByRole("dialog")).toBeInTheDocument()
  })
})

describe("TramiteReviewDialog: tipo de trámite", () => {
  it("las opciones son etiquetas, nunca enums, y elegir una deja el cambio sin guardar", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    const selector = within(dialogo).getByRole("combobox", { name: "Tipo de trámite" })
    await user.click(selector)
    const opciones = await screen.findAllByRole("option")
    expect(opciones.map((o) => o.textContent)).toEqual(["Alta", "Baja", "Censo", "Movimiento", "Demora"])
    await user.click(screen.getByRole("option", { name: "Censo" }))
    expect(selector).toHaveTextContent("Censo")
    expect(selector).not.toHaveTextContent("CENSO")
    expect(within(dialogo).getByText("Sin guardar")).toBeInTheDocument()
  })
})

describe("TramiteReviewDialog: crotal no encontrado e incompleto (completo, punto 5)", () => {
  const crotales = [
    crotal("4321", { completo: false }),
    crotal("ES010000009999", { completo: true }),
    crotal("1234", { crotal: "ES010000001234", resolucion: "EN_INVENTARIO", enInventario: true, animalId: 1, completo: false }),
  ]

  it("editable: la fila guardada incompleta sale en ámbar con «· incompleto»; las demás, como siempre", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ crotales }))))
    renderDialog()
    const dialogo = await dialogoListo()
    const filas = filasCrotales(dialogo)
    expect(within(filas[0]).getByText("No está en el inventario · incompleto")).toHaveClass("bg-warning")
    expect(within(filas[1]).getByText("No está en el inventario")).toHaveClass("border-border")
    expect(within(filas[2]).getByText("En inventario")).toHaveClass("bg-success")
    expect(within(filas[2]).queryByText(/incompleto/)).not.toBeInTheDocument()
  })

  it("solo lectura: igual", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ estado: "APROBADO", crotales }))),
    )
    renderDialog()
    const dialogo = await dialogoListo()
    const filas = filasCrotales(dialogo)
    expect(within(filas[0]).getByText("No está en el inventario · incompleto")).toHaveClass("bg-warning")
    expect(within(filas[1]).getByText("No está en el inventario")).toHaveClass("border-border")
    expect(within(filas[2]).getByText("En inventario")).toHaveClass("bg-success")
  })
})

describe("TramiteReviewDialog: crotales", () => {
  it("añadir enfoca la fila nueva; editar y añadir marcan «Sin guardar»; la fila intacta conserva su badge", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () =>
        HttpResponse.json(detalle({ crotales: [crotal("1234"), crotal("5678", { resolucion: "AMBIGUO" })] })),
      ),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    let filas = filasCrotales(dialogo)
    expect(within(filas[0]).getByText("No está en el inventario")).toBeInTheDocument()
    expect(within(filas[1]).getByText("Varios animales coinciden")).toBeInTheDocument()

    await user.type(within(dialogo).getByRole("textbox", { name: "Crotal 2" }), "9")
    await user.click(boton(dialogo, "Añadir crotal"))
    const nuevo = within(dialogo).getByRole("textbox", { name: "Crotal 3" })
    expect(nuevo).toHaveFocus()
    await user.type(nuevo, "4321")

    filas = filasCrotales(dialogo)
    expect(within(filas[0]).getByText("No está en el inventario")).toBeInTheDocument()
    expect(within(filas[1]).getByText("Sin guardar")).toBeInTheDocument()
    expect(within(filas[1]).queryByText("Varios animales coinciden")).not.toBeInTheDocument()
    expect(within(filas[2]).getByText("Sin guardar")).toBeInTheDocument()
    expect(within(dialogo).getByText("Guarda antes de aprobar")).toBeInTheDocument()
  })

  it("quitar tiene nombre accesible con el crotal y no marca como editadas a las demás", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () =>
        HttpResponse.json(detalle({ crotales: [crotal("1234"), crotal("5678", { resolucion: "AMBIGUO" })] })),
      ),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Quitar crotal 1234"))
    const filas = filasCrotales(dialogo)
    expect(filas).toHaveLength(1)
    expect(within(filas[0]).getByRole("textbox")).toHaveValue("5678")
    expect(within(filas[0]).getByRole("textbox")).toHaveFocus()
    expect(within(filas[0]).getByText("Varios animales coinciden")).toBeInTheDocument()
    expect(within(dialogo).getByText("Guarda antes de aprobar")).toBeInTheDocument()

    await user.click(boton(dialogo, "Quitar crotal 5678"))
    expect(within(dialogo).getByText("Sin crotales.")).toBeInTheDocument()
    expect(boton(dialogo, "Añadir crotal")).toHaveFocus()
  })

  it("tras Guardar, los badges y el crotal completo salen de la respuesta del backend", async () => {
    const patches: unknown[] = []
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ crotales: [crotal("1234")] }))),
      http.patch(apiUrl("/tramites/7"), async ({ request }) => {
        patches.push(await request.json())
        return HttpResponse.json(
          detalle({
            version: 1,
            crotales: [
              crotal("1234"),
              crotal("5678", { crotal: "ES010000005678", resolucion: "EN_INVENTARIO", enInventario: true, animalId: 4 }),
            ],
          }),
        )
      }),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Añadir crotal"))
    await user.type(within(dialogo).getByRole("textbox", { name: "Crotal 2" }), "5678")
    await user.click(boton(dialogo, "Guardar"))

    await waitFor(() => expect(within(dialogo).queryByText("Sin guardar")).not.toBeInTheDocument())
    expect(patches).toEqual([{ version: 0, crotales: ["1234", "5678"] }])
    const filas = filasCrotales(dialogo)
    expect(within(filas[1]).getByText("En inventario")).toBeInTheDocument()
    expect(within(filas[1]).getByText("ES010000005678")).toHaveClass("tabular-nums")
    expect(boton(dialogo, "Aprobar")).toBeEnabled()
  })
})

describe("TramiteReviewDialog: Guardar", () => {
  it("envía version + solo lo que cambió, y muestra «Guardando…» con las tres acciones desactivadas", async () => {
    const patches: unknown[] = []
    const espera = puerta()
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ version: 2 }))),
      http.patch(apiUrl("/tramites/7"), async ({ request }) => {
        patches.push(await request.json())
        await espera.promesa
        return HttpResponse.json(detalle({ version: 3, explotacionId: 9, explotacionCodigoRega: "ES999", explotacionNombre: "Los Olivos", tipoTramite: "CENSO" }))
      }),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    const campo = within(dialogo).getByRole("combobox", { name: "Explotación" })
    await user.click(campo)
    await user.click(await screen.findByRole("option", { name: /Los Olivos/ }))
    await user.click(within(dialogo).getByRole("combobox", { name: "Tipo de trámite" }))
    await user.click(await screen.findByRole("option", { name: "Censo" }))
    await user.click(boton(dialogo, "Guardar"))

    expect(await within(dialogo).findByRole("button", { name: "Guardando…" })).toBeDisabled()
    expect(boton(dialogo, "Aprobar")).toBeDisabled()
    expect(boton(dialogo, "Rechazar")).toBeDisabled()
    espera.abrir()
    expect(await within(dialogo).findByRole("button", { name: "Guardar" })).toBeDisabled()
    expect(patches).toEqual([{ version: 2, explotacionId: 9, tipoTramite: "CENSO" }])
    expect(boton(dialogo, "Aprobar")).toBeEnabled()
    // Tras guardar, el campo enseña la guardada (la del detalle nuevo), sin «Sin guardar».
    expect(campo).toHaveValue("ES999 · Los Olivos")
    expect(within(dialogo).queryByText("Sin guardar")).not.toBeInTheDocument()
  })
})

describe("TramiteReviewDialog: Aprobar", () => {
  it("con cambios sin guardar: desactivado con el aviso exacto, ligado por aria-describedby; Descartar cambios lo deshace", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ crotales: [crotal("1234")] }))))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.type(within(dialogo).getByRole("textbox", { name: "Crotal 1" }), "5")

    const aviso = within(dialogo).getByText("Guarda antes de aprobar")
    expect(aviso.textContent).toBe("Guarda antes de aprobar")
    for (const nombre of ["Aprobar", "Rechazar"]) {
      expect(boton(dialogo, nombre)).toBeDisabled()
      expect(boton(dialogo, nombre)).toHaveAccessibleDescription("Guarda antes de aprobar")
    }

    await user.click(boton(dialogo, "Descartar cambios"))
    expect(within(dialogo).getByRole("textbox", { name: "Crotal 1" })).toHaveValue("1234")
    expect(boton(dialogo, "Aprobar")).toBeEnabled()
    expect(boton(dialogo, "Aprobar")).not.toHaveAccessibleDescription()
  })

  it("un solo envío: «Aprobando…» y las tres acciones desactivadas mientras está en curso", async () => {
    let posts = 0
    const espera = puerta()
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ version: 3 }))),
      http.post(apiUrl("/tramites/7/aprobar"), async () => {
        posts += 1
        await espera.promesa
        return HttpResponse.json(respuestaLista(detalle({ estado: "APROBADO", version: 4 })))
      }),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Aprobar"))
    const enCurso = await within(dialogo).findByRole("button", { name: "Aprobando…" })
    expect(enCurso).toBeDisabled()
    expect(boton(dialogo, "Guardar")).toBeDisabled()
    expect(boton(dialogo, "Rechazar")).toBeDisabled()
    await user.click(enCurso)
    espera.abrir()
    await within(dialogo).findByText("Trámite aprobado.")
    expect(posts).toBe(1)
  })

  it("tras aprobar, el modal sigue abierto, en solo lectura, con el éxito anunciado y la cola avisada", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle()),
      () => HttpResponse.json(detalle({ estado: "APROBADO", version: 1 })),
    )
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), () =>
        HttpResponse.json(respuestaLista(detalle({ estado: "APROBADO", version: 1 }))),
      ),
    )
    const onCambiado = vi.fn()
    const onClose = vi.fn()
    const user = userEvent.setup()
    renderDialog({ onCambiado, onClose })
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Aprobar"))

    const exito = await within(dialogo).findByText("Trámite aprobado.")
    expect(exito.closest("[role=status]")).not.toBeNull()
    expect(within(dialogo).getByText("Aprobado")).toHaveClass("bg-success")
    expect(onCambiado).toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
    for (const nombre of ["Aprobar", "Rechazar", "Guardar", "Añadir crotal"]) {
      expect(within(dialogo).queryByRole("button", { name: nombre })).not.toBeInTheDocument()
    }
    // Nada sugiere OVZ.net.
    expect(dialogo).not.toHaveTextContent(/OVZ/)
    await waitFor(() => expect(within(dialogo).getByRole("heading", { name: "Trámite #7" })).toHaveFocus())
  })
})

describe("TramiteReviewDialog: Rechazar se confirma en línea", () => {
  it("pide confirmación en la barra; Cancelar vuelve atrás con el foco en Rechazar", async () => {
    let posts = 0
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)),
      http.post(apiUrl("/tramites/7/rechazar"), () => {
        posts += 1
        return HttpResponse.json(respuestaLista(detalle({ estado: "RECHAZADO", version: 1 })))
      }),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Rechazar"))

    const grupo = within(dialogo).getByRole("group", { name: "¿Rechazar el trámite #7? No se puede deshacer." })
    expect(within(grupo).getByRole("button", { name: "Cancelar" })).toHaveFocus()
    expect(within(dialogo).queryByRole("button", { name: "Aprobar" })).not.toBeInTheDocument()

    await user.click(within(grupo).getByRole("button", { name: "Cancelar" }))
    expect(within(dialogo).queryByRole("group", { name: /Rechazar el trámite/ })).not.toBeInTheDocument()
    expect(boton(dialogo, "Rechazar")).toHaveFocus()
    expect(posts).toBe(0)
  })

  it("Esc cancela la confirmación sin cerrar el modal", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    const onClose = vi.fn()
    const user = userEvent.setup()
    renderDialog({ onClose })
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Rechazar"))
    expect(within(dialogo).getByRole("group", { name: /Rechazar el trámite/ })).toBeInTheDocument()
    await user.keyboard("{Escape}")
    expect(within(dialogo).queryByRole("group", { name: /Rechazar el trámite/ })).not.toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
    expect(boton(dialogo, "Rechazar")).toHaveFocus()
  })

  it("«Sí, rechazar» envía {version} del detalle mostrado; el modal queda abierto en solo lectura con «Trámite rechazado.»", async () => {
    const cuerpos: string[] = []
    detallesEnOrden(
      () => HttpResponse.json(detalle()),
      () => HttpResponse.json(detalle({ estado: "RECHAZADO", version: 1 })),
    )
    server.use(
      http.post(apiUrl("/tramites/7/rechazar"), async ({ request }) => {
        cuerpos.push(await request.text())
        return HttpResponse.json(respuestaLista(detalle({ estado: "RECHAZADO", version: 1 })))
      }),
    )
    const onCambiado = vi.fn()
    const onClose = vi.fn()
    const user = userEvent.setup()
    renderDialog({ onCambiado, onClose })
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Rechazar"))
    await user.click(boton(dialogo, "Sí, rechazar"))

    expect(await within(dialogo).findByText("Trámite rechazado.")).toBeInTheDocument()
    expect(cuerpos).toEqual(['{"version":0}'])
    expect(within(dialogo).getByText("Rechazado")).toHaveClass("bg-danger")
    expect(onCambiado).toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
    expect(within(dialogo).queryByRole("button", { name: "Rechazar" })).not.toBeInTheDocument()
  })
})

describe("TramiteReviewDialog: avisos", () => {
  it("409 al guardar: el motivo tal cual, persistente, sobre los datos frescos; se puede cerrar el aviso", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle({ crotales: [crotal("1234")] })),
      () => HttpResponse.json(detalle({ version: 1, tipoTramite: "BAJA", crotales: [crotal("1234")] })),
    )
    server.use(
      http.patch(apiUrl("/tramites/7"), () =>
        HttpResponse.json({ motivo: "Dos crotales apuntan al mismo animal." }, { status: 409 }),
      ),
    )
    const onCambiado = vi.fn()
    const user = userEvent.setup()
    renderDialog({ onCambiado })
    const dialogo = await dialogoListo()
    await user.type(within(dialogo).getByRole("textbox", { name: "Crotal 1" }), "9")
    await user.click(boton(dialogo, "Guardar"))

    const alerta = (await within(dialogo).findByText("Dos crotales apuntan al mismo animal.")).closest("[role=alert]")!
    expect(alerta).toHaveTextContent("No se han guardado los cambios")
    // Datos frescos, edición descartada.
    await waitFor(() =>
      expect(within(dialogo).getByRole("combobox", { name: "Tipo de trámite" })).toHaveTextContent("Baja"),
    )
    expect(within(dialogo).getByRole("textbox", { name: "Crotal 1" })).toHaveValue("1234")
    expect(onCambiado).toHaveBeenCalled()

    await user.click(within(alerta as HTMLElement).getByRole("button", { name: "Cerrar aviso" }))
    expect(within(dialogo).queryByText("Dos crotales apuntan al mismo animal.")).not.toBeInTheDocument()
  })

  it("409 al aprobar: el motivo tal cual sobre los datos frescos (resolución cambiada)", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle({ crotales: [crotal("1234", { resolucion: "EN_INVENTARIO", crotal: "ES010000001234", enInventario: true, animalId: 1 })] })),
      () => HttpResponse.json(detalle({ version: 1, crotales: [crotal("1234", { resolucion: "AMBIGUO" })] })),
    )
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), () =>
        HttpResponse.json(
          { motivo: "La resolución de los crotales ha cambiado. Revisa el trámite antes de aprobarlo." },
          { status: 409 },
        ),
      ),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Aprobar"))
    expect(
      await within(dialogo).findByText("La resolución de los crotales ha cambiado. Revisa el trámite antes de aprobarlo."),
    ).toBeInTheDocument()
    expect(within(dialogo).getByText("No se ha aprobado el trámite")).toBeInTheDocument()
    await within(dialogo).findByText("Varios animales coinciden")
    expect(within(dialogo).queryByText("En inventario")).not.toBeInTheDocument()
  })

  it("403: el texto de la suscripción y un enlace a Facturación", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)),
      http.post(apiUrl("/tramites/7/aprobar"), () => new HttpResponse(null, { status: 403 })),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Aprobar"))
    const alerta = (await within(dialogo).findByText(TEXTO_SUSCRIPCION)).closest("[role=alert]")!
    expect(within(alerta as HTMLElement).getByRole("link", { name: "Ir a Facturación" })).toHaveAttribute(
      "href",
      "/facturacion",
    )
  })

  it("403 con {motivo}: ese texto tal cual y el enlace a Facturación", async () => {
    const motivo = "Tu suscripción está suspendida: actualízala en Facturación para aprobar."
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)),
      http.post(apiUrl("/tramites/7/aprobar"), () => HttpResponse.json({ motivo }, { status: 403 })),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Aprobar"))
    const alerta = (await within(dialogo).findByText(motivo)).closest("[role=alert]")!
    expect(within(alerta as HTMLElement).getByRole("link", { name: "Ir a Facturación" })).toHaveAttribute(
      "href",
      "/facturacion",
    )
    expect(within(dialogo).queryByText(TEXTO_SUSCRIPCION)).not.toBeInTheDocument()
  })

  it("400 al guardar: el motivo, y la edición se conserva para corregirla", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ crotales: [crotal("1234")] }))),
      http.patch(apiUrl("/tramites/7"), () =>
        HttpResponse.json({ motivo: "Indica al menos los últimos 4 dígitos del crotal." }, { status: 400 }),
      ),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Añadir crotal"))
    await user.type(within(dialogo).getByRole("textbox", { name: "Crotal 2" }), "12")
    await user.click(boton(dialogo, "Guardar"))
    expect(await within(dialogo).findByText("Indica al menos los últimos 4 dígitos del crotal.")).toBeInTheDocument()
    expect(within(dialogo).getByRole("textbox", { name: "Crotal 2" })).toHaveValue("12")
    expect(boton(dialogo, "Guardar")).toBeEnabled()
  })

  it("404 al aprobar y la recarga también 404: no encontrado, sin acciones", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle()),
      () => new HttpResponse(null, { status: 404 }),
    )
    server.use(http.post(apiUrl("/tramites/7/aprobar"), () => new HttpResponse(null, { status: 404 })))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Aprobar"))
    expect(await within(dialogo).findByText("Este trámite ya no existe o no es de tu gestoría.")).toBeInTheDocument()
    expect(within(dialogo).queryByRole("button", { name: "Aprobar" })).not.toBeInTheDocument()
  })

  it("si falla la recarga tras aprobar: éxito más una línea secundaria con Reintentar", async () => {
    const get = detallesEnOrden(
      () => HttpResponse.json(detalle()),
      () => HttpResponse.error(),
      () => HttpResponse.json(detalle({ estado: "APROBADO", version: 1 })),
    )
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), () =>
        HttpResponse.json(respuestaLista(detalle({ estado: "APROBADO", version: 1 }))),
      ),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Aprobar"))
    await within(dialogo).findByText("Trámite aprobado.")
    const linea = await within(dialogo).findByText(
      "No se ha podido conectar con Ganera. Inténtalo de nuevo en unos segundos.",
    )
    expect(linea.closest("[role=alert]")).not.toBeNull()
    await user.click(boton(dialogo, "Reintentar"))
    await waitFor(() => expect(get.llamadas()).toBe(3))
    await waitFor(() =>
      expect(within(dialogo).queryByText(/No se ha podido conectar/)).not.toBeInTheDocument(),
    )
    expect(within(dialogo).getByText("Trámite aprobado.")).toBeInTheDocument()
  })

  it("N1: guardar 404 y la recarga trae otra versión: se descartan las ediciones con un aviso", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle({ crotales: [crotal("1234")] })),
      () => HttpResponse.json(detalle({ version: 1, tipoTramite: "CENSO", crotales: [crotal("1234")] })),
    )
    server.use(http.patch(apiUrl("/tramites/7"), () => new HttpResponse(null, { status: 404 })))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.type(within(dialogo).getByRole("textbox", { name: "Crotal 1" }), "9")
    await user.click(boton(dialogo, "Guardar"))
    expect(
      await within(dialogo).findByText(
        "El trámite ha cambiado mientras lo editabas. Se han descartado tus cambios; revisa los datos actuales.",
      ),
    ).toBeInTheDocument()
    expect(within(dialogo).getByRole("textbox", { name: "Crotal 1" })).toHaveValue("1234")
    expect(within(dialogo).getByRole("combobox", { name: "Tipo de trámite" })).toHaveTextContent("Censo")
  })

  it("N2: sin respuesta al aprobar, pero la recarga lo muestra aprobado: se dice eso, no «Inténtalo de nuevo»", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle()),
      () => HttpResponse.json(detalle({ estado: "APROBADO", version: 1 })),
    )
    server.use(http.post(apiUrl("/tramites/7/aprobar"), () => HttpResponse.error()))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Aprobar"))
    const texto = await within(dialogo).findByText(
      "No hubo respuesta a tiempo, pero el trámite consta como aprobado.",
    )
    expect(texto.closest("[role=status]")).not.toBeNull()
    expect(dialogo).not.toHaveTextContent(/Inténtalo de nuevo/)
  })
})

describe("TramiteReviewDialog: cerrar con cambios sin guardar", () => {
  it("pregunta en línea; «Seguir editando» conserva la edición y «Descartar y cerrar» cierra", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ crotales: [crotal("1234")] }))))
    const onClose = vi.fn()
    const user = userEvent.setup()
    renderDialog({ onClose })
    const dialogo = await dialogoListo()
    await user.type(within(dialogo).getByRole("textbox", { name: "Crotal 1" }), "5")

    await user.click(boton(dialogo, "Cerrar"))
    const grupo = within(dialogo).getByRole("group", { name: "Tienes cambios sin guardar." })
    expect(within(grupo).getByRole("button", { name: "Seguir editando" })).toHaveFocus()
    expect(onClose).not.toHaveBeenCalled()

    await user.click(within(grupo).getByRole("button", { name: "Seguir editando" }))
    expect(within(dialogo).getByRole("textbox", { name: "Crotal 1" })).toHaveValue("12345")

    // Esc también pregunta en vez de cerrar.
    await user.keyboard("{Escape}")
    expect(within(dialogo).getByRole("group", { name: "Tienes cambios sin guardar." })).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()

    await user.click(boton(dialogo, "Descartar y cerrar"))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it("sin cambios, Esc cierra directamente", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    const onClose = vi.fn()
    const user = userEvent.setup()
    renderDialog({ onClose })
    await dialogoListo()
    await user.keyboard("{Escape}")
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})

describe("TramiteReviewDialog: correcciones tras la revisión de 9b", () => {
  it("foco: tras un 409 al aprobar (resolución cambiada), el foco NO vuelve a Aprobar sino al título", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle({ crotales: [crotal("1234", { resolucion: "EN_INVENTARIO", crotal: "ES010000001234", enInventario: true, animalId: 1 })] })),
      () => HttpResponse.json(detalle({ version: 1, crotales: [crotal("1234", { resolucion: "AMBIGUO" })] })),
    )
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), () =>
        HttpResponse.json({ motivo: "La resolución de los crotales ha cambiado." }, { status: 409 }),
      ),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    boton(dialogo, "Aprobar").focus()
    await user.keyboard("{Enter}")
    await within(dialogo).findByText("Varios animales coinciden")
    await waitFor(() => expect(boton(dialogo, "Aprobar")).toBeEnabled())
    await waitFor(() => expect(within(dialogo).getByRole("heading", { name: "Trámite #7" })).toHaveFocus())
    expect(document.activeElement).not.toBe(boton(dialogo, "Aprobar"))
  })

  it("I1 (A): una pregunta de cierre que se ocultó no vuelve a aparecer ni roba el foco al seguir editando", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    const onClose = vi.fn()
    const user = userEvent.setup()
    renderDialog({ onClose })
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Añadir crotal"))
    await user.type(within(dialogo).getByRole("textbox", { name: "Crotal 1" }), "1234")
    await user.keyboard("{Escape}")
    expect(within(dialogo).getByRole("group", { name: "Tienes cambios sin guardar." })).toBeInTheDocument()

    // Deshacer la edición: el formulario queda limpio y la pregunta desaparece.
    await user.click(boton(dialogo, "Quitar crotal 1234"))
    expect(within(dialogo).queryByRole("group", { name: "Tienes cambios sin guardar." })).not.toBeInTheDocument()

    // Volver a editar: la pregunta no reaparece y el foco sigue en el campo.
    await user.click(boton(dialogo, "Añadir crotal"))
    const campo = within(dialogo).getByRole("textbox", { name: "Crotal 1" })
    await user.type(campo, "9")
    expect(within(dialogo).queryByRole("group", { name: "Tienes cambios sin guardar." })).not.toBeInTheDocument()
    expect(campo).toHaveFocus()
    expect(campo).toHaveValue("9")
    expect(onClose).not.toHaveBeenCalled()
  })

  it("I1 (B): Esc mientras se guarda no hace nada ni deja la pregunta armada para después", async () => {
    const espera = puerta()
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ crotales: [crotal("1234")] }))),
      http.patch(apiUrl("/tramites/7"), async () => {
        await espera.promesa
        return HttpResponse.json(detalle({ version: 1, crotales: [crotal("12345")] }))
      }),
    )
    const onClose = vi.fn()
    const user = userEvent.setup()
    renderDialog({ onClose })
    const dialogo = await dialogoListo()
    await user.type(within(dialogo).getByRole("textbox", { name: "Crotal 1" }), "5")
    await user.click(boton(dialogo, "Guardar"))
    await within(dialogo).findByRole("button", { name: "Guardando…" })
    await user.keyboard("{Escape}")
    expect(onClose).not.toHaveBeenCalled()
    espera.abrir()
    await within(dialogo).findByRole("button", { name: "Guardar" })

    const campo = within(dialogo).getByRole("textbox", { name: "Crotal 1" })
    await user.click(campo)
    await user.type(campo, "6")
    expect(within(dialogo).queryByRole("group", { name: "Tienes cambios sin guardar." })).not.toBeInTheDocument()
    expect(campo).toHaveFocus()
    expect(campo).toHaveValue("123456")
    expect(onClose).not.toHaveBeenCalled()
  })

  it("I2: si se edita con la confirmación de rechazo abierta, se retira (nunca un «Sí, rechazar» que no hace nada)", async () => {
    let posts = 0
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ crotales: [crotal("1234")] }))),
      http.post(apiUrl("/tramites/7/rechazar"), () => {
        posts += 1
        return HttpResponse.json(respuestaLista(detalle({ estado: "RECHAZADO", version: 1 })))
      }),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Rechazar"))
    expect(boton(dialogo, "Sí, rechazar")).toBeInTheDocument()

    const campo = within(dialogo).getByRole("textbox", { name: "Crotal 1" })
    await user.click(campo)
    await user.type(campo, "9")

    expect(within(dialogo).queryByRole("button", { name: "Sí, rechazar" })).not.toBeInTheDocument()
    expect(boton(dialogo, "Rechazar")).toBeDisabled()
    expect(boton(dialogo, "Rechazar")).toHaveAccessibleDescription("Guarda antes de aprobar")
    expect(campo).toHaveFocus()

    // Deshacer la edición no resucita la confirmación: hay que volver a pedirla.
    await user.clear(campo)
    await user.type(campo, "1234")
    expect(within(dialogo).queryByRole("button", { name: "Sí, rechazar" })).not.toBeInTheDocument()
    expect(boton(dialogo, "Rechazar")).toBeEnabled()
    expect(posts).toBe(0)
  })

  it("m2: cambiar la explotación marca «Sin guardar» en todas las filas; volver a la guardada devuelve los badges", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () =>
        HttpResponse.json(
          detalle({
            crotales: [
              crotal("1234", { crotal: "ES010000001234", resolucion: "EN_INVENTARIO", enInventario: true, animalId: 1 }),
              crotal("5678", { resolucion: "AMBIGUO" }),
            ],
          }),
        ),
      ),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    const campo = within(dialogo).getByRole("combobox", { name: "Explotación" })
    await user.click(campo)
    await user.click(await screen.findByRole("option", { name: /Los Olivos/ }))

    for (const fila of filasCrotales(dialogo)) {
      expect(within(fila).getByText("Sin guardar")).toBeInTheDocument()
    }
    expect(within(dialogo).queryByText("En inventario")).not.toBeInTheDocument()
    expect(within(dialogo).queryByText("Varios animales coinciden")).not.toBeInTheDocument()
    expect(within(dialogo).queryByText("ES010000001234")).not.toBeInTheDocument()

    await user.click(campo)
    await user.click(await screen.findByRole("option", { name: /La Dehesa/ }))
    const filas = filasCrotales(dialogo)
    expect(within(filas[0]).getByText("En inventario")).toBeInTheDocument()
    expect(within(filas[0]).getByText("ES010000001234")).toBeInTheDocument()
    expect(within(filas[1]).getByText("Varios animales coinciden")).toBeInTheDocument()
  })

  it("m4: sin respuesta al aprobar y la recarga lo muestra en otro estado: título neutro, nunca «No se ha aprobado»", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle()),
      () => HttpResponse.json(detalle({ estado: "EN_PROCESO", version: 2 })),
    )
    server.use(http.post(apiUrl("/tramites/7/aprobar"), () => HttpResponse.error()))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Aprobar"))
    const texto = await within(dialogo).findByText(
      "No hubo respuesta a tiempo. El trámite consta ahora como en proceso.",
    )
    const alerta = texto.closest("[role=alert]") as HTMLElement
    expect(alerta).not.toBeNull()
    expect(within(alerta).getByText("El trámite ha cambiado de estado")).toBeInTheDocument()
    expect(dialogo).not.toHaveTextContent("No se ha aprobado el trámite")
  })

  it("n1 (re-revisión 9b): el aviso «estado-cambiado» es el neutro, no el destructivo", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle()),
      () => HttpResponse.json(detalle({ estado: "EN_PROCESO", version: 2 })),
    )
    server.use(http.post(apiUrl("/tramites/7/aprobar"), () => HttpResponse.error()))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Aprobar"))
    const texto = await within(dialogo).findByText(
      "No hubo respuesta a tiempo. El trámite consta ahora como en proceso.",
    )
    const alerta = texto.closest("[role=alert]") as HTMLElement
    expect(alerta).toHaveAttribute("data-aviso", "estado-cambiado")
    expect(alerta).toHaveClass("text-card-foreground")
    expect(alerta).not.toHaveClass("text-destructive")
  })

  it("m5: un ERROR_OVZ con motivo lo muestra en la columna de datos", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () =>
        HttpResponse.json(detalle({ estado: "ERROR_OVZ", motivoError: "Credenciales rechazadas." as unknown as null })),
      ),
    )
    renderDialog()
    const dialogo = await dialogoListo()
    const datos = within(dialogo).getByRole("region", { name: "Datos del trámite" })
    const alerta = within(datos).getByText("Credenciales rechazadas.").closest("[role=alert]") as HTMLElement
    expect(within(alerta).getByText("Motivo del error")).toBeInTheDocument()
  })

  it("finish #5: «Sin guardar» se distingue de un resultado guardado (borde discontinuo)", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ crotales: [crotal("1234")] }))))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Añadir crotal"))
    expect(within(filasCrotales(dialogo)[1]).getByText("Sin guardar")).toHaveClass("border-dashed")
    expect(within(filasCrotales(dialogo)[0]).getByText("No está en el inventario")).not.toHaveClass("border-dashed")
  })
})
