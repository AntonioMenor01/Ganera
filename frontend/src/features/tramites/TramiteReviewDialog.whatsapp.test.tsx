import { act, render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { MemoryRouter } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import type { RespuestaWhatsapp, TramiteDetalle, WhatsappTramite } from "./types"
import { TramiteReviewDialog } from "./TramiteReviewDialog"
import { TEXTO_VENTANA_CERRADA } from "./SeccionWhatsapp"

/** Tests del panel «Responder por WhatsApp» (B2 reducido, T2) sobre el cliente real y MSW. */

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

function enMinutos(minutos: number): string {
  return new Date(Date.now() + minutos * 60_000).toISOString()
}

function whatsapp(parcial: Partial<WhatsappTramite> = {}): WhatsappTramite {
  return {
    destinatarioNombre: "Juan Ganadero",
    telefonoEnmascarado: "+34******001",
    contactoActivo: true,
    ventanaAbiertaHasta: enMinutos(5 * 60),
    firmaGestoria: "Gestoría Norte",
    motivoNoDisponible: null,
    respuestas: [],
    ...parcial,
  }
}

function detalle(parcial: Partial<TramiteDetalle> = {}): TramiteDetalle {
  return {
    id: 7,
    tipoTramite: "BAJA_MUERTE",
    estado: "PENDIENTE_REVISION",
    motivoError: null,
    explotacionId: 3,
    explotacionCodigoRega: "ES123",
    explotacionNombre: "La Dehesa",
    ganaderoNombre: "Juan Ganadero",
    ganaderoNif: "12345678Z",
    mensajeOriginal: "se ha muerto la 1234",
    crotales: [],
    version: 0,
    origen: "WHATSAPP",
    estadoExtraccion: "COMPLETADA",
    crotalesDescartados: 0,
    hechoEnOvzEn: null,
    hechoEnOvzPorNombre: null,
    whatsapp: whatsapp(),
    motivoRechazo: null,
    descartable: false,
    historialDescarte: [],
    ...parcial,
  }
}

function respuesta(parcial: Partial<RespuestaWhatsapp> = {}): RespuestaWhatsapp {
  return {
    id: 41,
    texto: "Hola, Juan Ganadero. Para tramitar tu baja por muerte necesitamos: la fecha\n— Gestoría Norte",
    estado: "ENVIADO",
    enviadoPorNombre: "Ana Pérez",
    enviadoEn: "2026-10-07T10:30:00Z",
    codigoError: null,
    motivoError: null,
    ...parcial,
  }
}

interface Cuerpo {
  texto: string
  claveIdempotencia: string
}

/** GET /tramites/7 con las respuestas dadas, en orden (la última se repite). */
function detalles(...lista: (() => Response)[]) {
  let llamadas = 0
  server.use(
    http.get(apiUrl("/tramites/7"), () => {
      const r = lista[Math.min(llamadas, lista.length - 1)]
      llamadas += 1
      return r()
    }),
  )
  return { llamadas: () => llamadas }
}

/** POST de respuestas: guarda los cuerpos y responde con lo dado, en orden (el último se repite). */
function posts(...lista: (() => Response | Promise<Response>)[]) {
  const cuerpos: Cuerpo[] = []
  server.use(
    http.post(apiUrl("/tramites/7/respuestas-whatsapp"), async ({ request }) => {
      cuerpos.push((await request.json()) as Cuerpo)
      return lista[Math.min(cuerpos.length - 1, lista.length - 1)]()
    }),
  )
  return cuerpos
}

function puerta() {
  let abrir!: () => void
  const promesa = new Promise<void>((resolve) => {
    abrir = resolve
  })
  return { promesa, abrir }
}

beforeEach(() => {
  server.use(
    http.get(apiUrl("/explotaciones"), () =>
      HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
    ),
  )
})

function renderDialog(onClose = vi.fn(), onCambiado = vi.fn()) {
  render(
    <MemoryRouter>
      <TramiteReviewDialog tramiteId={7} onClose={onClose} onCambiado={onCambiado} />
    </MemoryRouter>,
  )
  return { onClose, onCambiado }
}

async function dialogoListo() {
  const dialogo = await screen.findByRole("dialog")
  await within(dialogo).findByRole("heading", { name: "Mensaje de WhatsApp" })
  return dialogo
}

function seccion(dialogo: HTMLElement) {
  return within(dialogo).getByRole("region", { name: "WhatsApp" })
}

function boton(contenedor: HTMLElement, nombre: string | RegExp) {
  return within(contenedor).getByRole("button", { name: nombre })
}

function campoRespuesta(contenedor: HTMLElement) {
  return within(contenedor).getByRole("textbox", { name: "Respuesta" }) as HTMLTextAreaElement
}

/** Abre el panel y sustituye el hueco (seleccionado al abrir) por `relleno`. */
async function abrirYCompletar(user: ReturnType<typeof userEvent.setup>, dialogo: HTMLElement, relleno = "la fecha") {
  await user.click(boton(dialogo, "Responder por WhatsApp"))
  const campo = campoRespuesta(dialogo)
  await user.keyboard(relleno)
  return campo
}

// ---------------------------------------------------------------------------------------------

describe("Sección WhatsApp: qué se ve", () => {
  it("sin bloque de WhatsApp (whatsapp: null) no hay sección ni botón", async () => {
    detalles(() => HttpResponse.json(detalle({ whatsapp: null, origen: null })))
    renderDialog()
    const dialogo = await dialogoListo()
    expect(within(dialogo).queryByRole("region", { name: "WhatsApp" })).toBeNull()
    expect(within(dialogo).queryByRole("button", { name: "Responder por WhatsApp" })).toBeNull()
  })

  it("destinatario tal como llega, ventana abierta con fecha de Madrid y el botón", async () => {
    detalles(() => HttpResponse.json(detalle()))
    renderDialog()
    const dialogo = await dialogoListo()
    const s = seccion(dialogo)
    expect(s).toHaveTextContent("Juan Ganadero · +34******001")
    expect(within(s).getByText(/^Puedes escribirle hasta el \d{2}\/\d{2}\/\d{4}, \d{2}:\d{2}$/)).toBeInTheDocument()
    expect(boton(s, "Responder por WhatsApp")).toBeEnabled()
    // Sin respuestas, sin bloque de historial.
    expect(within(s).queryByRole("heading", { name: "Respuestas por WhatsApp" })).toBeNull()
  })

  it("con menos de 1 h: aviso ámbar «La ventana se cierra a las hh:mm»", async () => {
    detalles(() => HttpResponse.json(detalle({ whatsapp: whatsapp({ ventanaAbiertaHasta: enMinutos(30) }) })))
    renderDialog()
    const s = seccion(await dialogoListo())
    const aviso = within(s).getByText(/^La ventana se cierra a las \d{2}:\d{2}$/)
    expect(aviso.closest("[data-aviso-whatsapp]")).toHaveClass("bg-warning", "text-warning-foreground")
    expect(aviso.closest("[role=alert], [role=status]")).toBeNull()
    expect(boton(s, "Responder por WhatsApp")).toBeEnabled()
  })

  it("con motivoNoDisponible: el motivo como aviso fijo (sin role) y sin botón", async () => {
    const motivo =
      "Han pasado más de 24 horas desde su último mensaje. WhatsApp no deja escribirle hasta que vuelva a escribir."
    detalles(() =>
      HttpResponse.json(detalle({ whatsapp: whatsapp({ motivoNoDisponible: motivo, ventanaAbiertaHasta: null }) })),
    )
    renderDialog()
    const s = seccion(await dialogoListo())
    const aviso = within(s).getByText(motivo)
    expect(aviso.closest("[data-aviso-whatsapp]")).not.toBeNull()
    expect(aviso.closest("[role=alert], [role=status]")).toBeNull()
    expect(within(s).queryByRole("button", { name: "Responder por WhatsApp" })).toBeNull()
  })

  it("historial en orden: fecha de Madrid, quién, texto con saltos de línea, badge y motivo", async () => {
    detalles(() =>
      HttpResponse.json(
        detalle({
          whatsapp: whatsapp({
            respuestas: [
              respuesta({ id: 1, texto: "Primera\nlínea dos", estado: "LEIDO" }),
              respuesta({
                id: 2,
                texto: "Segunda",
                estado: "FALLIDO",
                enviadoEn: "2026-10-07T11:00:00Z",
                enviadoPorNombre: "Luis Gil",
                codigoError: 63015,
                motivoError: "Este número no se ha unido al sandbox de WhatsApp de pruebas.",
              }),
            ],
          }),
        }),
      ),
    )
    renderDialog()
    const s = seccion(await dialogoListo())
    expect(within(s).getByRole("heading", { name: "Respuestas por WhatsApp" })).toBeInTheDocument()
    const filas = within(within(s).getByRole("list", { name: "Respuestas por WhatsApp" })).getAllByRole("listitem")
    expect(filas).toHaveLength(2)
    expect(filas[0]).toHaveTextContent("07/10/2026, 12:30")
    expect(filas[0]).toHaveTextContent("Ana Pérez")
    expect(within(filas[0]).getByText("Leído")).toBeInTheDocument()
    expect(within(filas[0]).getByText(/Primera/).textContent).toBe("Primera\nlínea dos")
    expect(filas[1]).toHaveTextContent("07/10/2026, 13:00")
    expect(filas[1]).toHaveTextContent("Luis Gil")
    expect(within(filas[1]).getByText("No ha llegado")).toBeInTheDocument()
    expect(filas[1]).toHaveTextContent("Este número no se ha unido al sandbox de WhatsApp de pruebas.")
  })

  it("en PENDIENTE_EXTRACCION se ve la sección pero no se ofrece responder", async () => {
    detalles(() => HttpResponse.json(detalle({ estado: "PENDIENTE_EXTRACCION" })))
    renderDialog()
    const s = seccion(await dialogoListo())
    expect(s).toHaveTextContent("Juan Ganadero")
    expect(within(s).queryByRole("button", { name: "Responder por WhatsApp" })).toBeNull()
  })
})

describe("Sección WhatsApp: el panel", () => {
  it("al abrir: texto propuesto, foco en el texto con el hueco seleccionado, contador y pista; Enviar desactivado", async () => {
    detalles(() => HttpResponse.json(detalle()))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Responder por WhatsApp"))

    const campo = campoRespuesta(dialogo)
    const propuesto = "Hola, Juan Ganadero. Para tramitar tu baja por muerte necesitamos: ___\n— Gestoría Norte"
    expect(campo).toHaveValue(propuesto)
    expect(campo).toHaveFocus()
    expect(campo.selectionStart).toBe(propuesto.indexOf("___"))
    expect(campo.selectionEnd).toBe(propuesto.indexOf("___") + 3)
    expect(within(dialogo).getByText(`${propuesto.length} / 1600`)).toBeInTheDocument()
    expect(within(dialogo).getByText("Completa el hueco ___ antes de enviar.")).toBeInTheDocument()
    expect(boton(dialogo, "Enviar por WhatsApp")).toBeDisabled()
    // El botón de abrir no se duplica mientras el panel está abierto.
    expect(within(dialogo).queryByRole("button", { name: "Responder por WhatsApp" })).toBeNull()

    await user.keyboard("la fecha de la muerte")
    expect(campo).toHaveValue(propuesto.replace("___", "la fecha de la muerte"))
    expect(within(dialogo).queryByText("Completa el hueco ___ antes de enviar.")).toBeNull()
    expect(boton(dialogo, "Enviar por WhatsApp")).toBeEnabled()
  })

  it("APROBADO: sin hueco, el cursor al final y Enviar ya activo", async () => {
    detalles(() => HttpResponse.json(detalle({ estado: "APROBADO" })))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Responder por WhatsApp"))
    const campo = campoRespuesta(dialogo)
    expect(campo.value).toBe(
      "Hola, Juan Ganadero. Hemos revisado tu trámite (baja por muerte) y lo tramitamos en breve.\n— Gestoría Norte",
    )
    expect(campo).toHaveFocus()
    expect(campo.selectionStart).toBe(campo.value.length)
    expect(boton(dialogo, "Enviar por WhatsApp")).toBeEnabled()
  })

  it("contador y límite de 1600: por encima se desactiva Enviar con el motivo", async () => {
    detalles(() => HttpResponse.json(detalle({ estado: "APROBADO" })))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Responder por WhatsApp"))
    const campo = campoRespuesta(dialogo)
    await user.clear(campo)
    await user.paste("a".repeat(1600))
    expect(within(dialogo).getByText("1600 / 1600")).toBeInTheDocument()
    expect(boton(dialogo, "Enviar por WhatsApp")).toBeEnabled()
    await user.paste("b")
    expect(within(dialogo).getByText("1601 / 1600")).toBeInTheDocument()
    expect(within(dialogo).getByText("La respuesta admite como máximo 1600 caracteres.")).toBeInTheDocument()
    expect(campo).toHaveAttribute("aria-invalid", "true")
    expect(boton(dialogo, "Enviar por WhatsApp")).toBeDisabled()
  })

  it("vacío: «Escribe el texto de la respuesta.» y Enviar desactivado", async () => {
    detalles(() => HttpResponse.json(detalle({ estado: "APROBADO" })))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Responder por WhatsApp"))
    await user.clear(campoRespuesta(dialogo))
    expect(within(dialogo).getByText("Escribe el texto de la respuesta.")).toBeInTheDocument()
    expect(boton(dialogo, "Enviar por WhatsApp")).toBeDisabled()
  })

  it("Cancelar cierra el panel y devuelve el foco a «Responder por WhatsApp»", async () => {
    detalles(() => HttpResponse.json(detalle()))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Responder por WhatsApp"))
    await user.click(boton(dialogo, "Cancelar"))
    expect(within(dialogo).queryByRole("textbox", { name: "Respuesta" })).toBeNull()
    expect(boton(dialogo, "Responder por WhatsApp")).toHaveFocus()
  })

  it("Esc con el panel abierto cierra el panel, no el modal", async () => {
    detalles(() => HttpResponse.json(detalle()))
    const user = userEvent.setup()
    const { onClose } = renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Responder por WhatsApp"))
    await user.keyboard("{Escape}")
    expect(onClose).not.toHaveBeenCalled()
    expect(within(dialogo).queryByRole("textbox", { name: "Respuesta" })).toBeNull()
  })
})

describe("Sección WhatsApp: enviar", () => {
  it("201: manda {texto, claveIdempotencia}, cierra el panel, añade la respuesta y lo dice sin «enviado»", async () => {
    const get = detalles(() => HttpResponse.json(detalle()))
    const cuerpos = posts(() => HttpResponse.json(respuesta({ id: 50, texto: "x" }), { status: 201 }))
    const user = userEvent.setup()
    const { onCambiado } = renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))

    expect(await within(dialogo).findByText("Respuesta aceptada por WhatsApp.")).toBeInTheDocument()
    expect(cuerpos).toHaveLength(1)
    expect(cuerpos[0].texto).toBe(
      "Hola, Juan Ganadero. Para tramitar tu baja por muerte necesitamos: la fecha\n— Gestoría Norte",
    )
    expect(cuerpos[0].claveIdempotencia).toMatch(UUID)
    expect(Object.keys(cuerpos[0])).toEqual(["texto", "claveIdempotencia"])
    expect(within(dialogo).queryByRole("textbox", { name: "Respuesta" })).toBeNull()
    const s = seccion(dialogo)
    expect(within(s).getByText("Aceptado por WhatsApp")).toBeInTheDocument()
    expect(boton(s, "Responder por WhatsApp")).toHaveFocus()
    // Ni recarga el trámite ni avisa a la cola: responder no cambia el trámite.
    expect(get.llamadas()).toBe(1)
    expect(onCambiado).not.toHaveBeenCalled()
  })

  it("200 (clave repetida) también es éxito", async () => {
    detalles(() => HttpResponse.json(detalle()))
    posts(() => HttpResponse.json(respuesta({ estado: "ENTREGADO" }), { status: 200 }))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    expect(await within(dialogo).findByText("Respuesta aceptada por WhatsApp.")).toBeInTheDocument()
    expect(within(seccion(dialogo)).getByText("Entregado")).toBeInTheDocument()
  })

  it("doble clic en Enviar → una sola petición; mientras, el botón dice «Enviando…» y está inactivo (aria-disabled)", async () => {
    detalles(() => HttpResponse.json(detalle()))
    const p = puerta()
    const cuerpos = posts(async () => {
      await p.promesa
      return HttpResponse.json(respuesta(), { status: 201 })
    })
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.dblClick(boton(dialogo, "Enviar por WhatsApp"))
    await waitFor(() => expect(cuerpos).toHaveLength(1))
    expect(boton(dialogo, "Enviando…")).toHaveAttribute("aria-disabled", "true")
    p.abrir()
    expect(await within(dialogo).findByText("Respuesta aceptada por WhatsApp.")).toBeInTheDocument()
    expect(cuerpos).toHaveLength(1)
  })

  it("dos clics en el mismo render (antes de que React desactive el botón) → una sola petición", async () => {
    detalles(() => HttpResponse.json(detalle()))
    const p = puerta()
    const cuerpos = posts(async () => {
      await p.promesa
      return HttpResponse.json(respuesta(), { status: 201 })
    })
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    const enviar = boton(dialogo, "Enviar por WhatsApp")
    // Dentro de un mismo act React no vuelve a pintar entre los dos clics: el segundo llega con el
    // botón aún activo y el mismo cierre. Solo la guarda síncrona lo para.
    act(() => {
      enviar.click()
      enviar.click()
    })
    await waitFor(() => expect(cuerpos.length).toBeGreaterThan(0))
    p.abrir()
    expect(await within(dialogo).findByText("Respuesta aceptada por WhatsApp.")).toBeInTheDocument()
    expect(cuerpos).toHaveLength(1)
  })

  it("error de red: aviso de reintento y el reintento lleva la MISMA clave", async () => {
    detalles(() => HttpResponse.json(detalle()))
    const cuerpos = posts(
      () => HttpResponse.error(),
      () => HttpResponse.json(respuesta(), { status: 200 }),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    expect(
      await within(dialogo).findByText(
        "No se ha podido comprobar si ha salido. Puedes reintentar: no se mandará dos veces.",
      ),
    ).toBeInTheDocument()
    // El texto sigue en el panel.
    expect(campoRespuesta(dialogo).value).toContain("necesitamos: la fecha")
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    expect(await within(dialogo).findByText("Respuesta aceptada por WhatsApp.")).toBeInTheDocument()
    expect(cuerpos).toHaveLength(2)
    expect(cuerpos[1].claveIdempotencia).toBe(cuerpos[0].claveIdempotencia)
  })

  it("un 500 sin motivo es igual de incierto: misma clave al reintentar", async () => {
    detalles(() => HttpResponse.json(detalle()))
    const cuerpos = posts(
      () => new HttpResponse(null, { status: 500 }),
      () => HttpResponse.json(respuesta(), { status: 200 }),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    await within(dialogo).findByText(/No se ha podido comprobar si ha salido/)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    await within(dialogo).findByText("Respuesta aceptada por WhatsApp.")
    expect(cuerpos[1].claveIdempotencia).toBe(cuerpos[0].claveIdempotencia)
  })

  it("una nueva apertura tras un éxito genera otra clave", async () => {
    detalles(() => HttpResponse.json(detalle()))
    const cuerpos = posts(
      () => HttpResponse.json(respuesta({ id: 1 }), { status: 201 }),
      () => HttpResponse.json(respuesta({ id: 2 }), { status: 201 }),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    await within(dialogo).findByText("Respuesta aceptada por WhatsApp.")
    await abrirYCompletar(user, dialogo, "otra cosa")
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    await waitFor(() => expect(cuerpos).toHaveLength(2))
    expect(cuerpos[0].claveIdempotencia).toMatch(UUID)
    expect(cuerpos[1].claveIdempotencia).toMatch(UUID)
    expect(cuerpos[1].claveIdempotencia).not.toBe(cuerpos[0].claveIdempotencia)
    await waitFor(() =>
      expect(within(within(seccion(dialogo)).getByRole("list")).getAllByRole("listitem")).toHaveLength(2),
    )
  })

  it("409: enseña el motivo, recarga el bloque, conserva el texto y el siguiente intento lleva otra clave", async () => {
    const motivo = "Esta respuesta ya se envió con otro texto. Escribe otra."
    const get = detalles(() => HttpResponse.json(detalle()))
    const cuerpos = posts(
      () => HttpResponse.json({ motivo }, { status: 409 }),
      () => HttpResponse.json(respuesta(), { status: 201 }),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    expect(await within(dialogo).findByText(motivo)).toBeInTheDocument()
    await waitFor(() => expect(get.llamadas()).toBe(2))
    expect(campoRespuesta(dialogo).value).toContain("necesitamos: la fecha")
    await waitFor(() => expect(boton(dialogo, "Enviar por WhatsApp")).toBeEnabled())
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    await within(dialogo).findByText("Respuesta aceptada por WhatsApp.")
    expect(cuerpos[1].claveIdempotencia).not.toBe(cuerpos[0].claveIdempotencia)
  })

  it("409 de ventana cerrada: la recarga trae el motivo, el texto se conserva y Enviar queda desactivado", async () => {
    const motivo =
      "Han pasado más de 24 horas desde su último mensaje. WhatsApp no deja escribirle hasta que vuelva a escribir."
    detalles(
      () => HttpResponse.json(detalle()),
      () => HttpResponse.json(detalle({ whatsapp: whatsapp({ motivoNoDisponible: motivo, ventanaAbiertaHasta: null }) })),
    )
    posts(() => HttpResponse.json({ motivo }, { status: 409 }))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    await waitFor(() => expect(within(dialogo).getAllByText(motivo)).toHaveLength(2))
    expect(campoRespuesta(dialogo).value).toContain("necesitamos: la fecha")
    await waitFor(() => expect(boton(dialogo, "Enviar por WhatsApp")).toBeDisabled())
  })

  it("400: enseña el motivo y conserva el texto", async () => {
    detalles(() => HttpResponse.json(detalle()))
    posts(() => HttpResponse.json({ motivo: "Escribe el texto de la respuesta." }, { status: 400 }))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    const alerta = await within(dialogo).findByRole("alert")
    expect(alerta).toHaveTextContent("Escribe el texto de la respuesta.")
    expect(campoRespuesta(dialogo).value).toContain("necesitamos: la fecha")
  })

  it("503 de «no configurado»: el motivo", async () => {
    const motivo = "El envío por WhatsApp no está configurado. Ponte en contacto con Ganera."
    detalles(() => HttpResponse.json(detalle()))
    posts(() => HttpResponse.json({ motivo }, { status: 503 }))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    expect(await within(dialogo).findByText(motivo)).toBeInTheDocument()
    expect(within(dialogo).queryByText(/No se ha podido comprobar si ha salido/)).toBeNull()
  })

  it("404 y la recarga también 404: el modal pasa a «Trámite no disponible»", async () => {
    detalles(
      () => HttpResponse.json(detalle()),
      () => new HttpResponse(null, { status: 404 }),
    )
    posts(() => new HttpResponse(null, { status: 404 }))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    expect(await within(dialogo).findByText("Trámite no disponible")).toBeInTheDocument()
    expect(within(dialogo).getByText("Este trámite ya no existe o no es de tu gestoría.")).toBeInTheDocument()
  })

  it("M1: tras el 404 doble, un solo Esc cierra el modal (el panel no se queda abierto por debajo)", async () => {
    detalles(
      () => HttpResponse.json(detalle()),
      () => new HttpResponse(null, { status: 404 }),
    )
    posts(() => new HttpResponse(null, { status: 404 }))
    const user = userEvent.setup()
    const { onClose } = renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    await within(dialogo).findByText("Trámite no disponible")
    await user.keyboard("{Escape}")
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it("FALLIDO: alerta destructiva con el motivo, y el historial lo marca «No ha llegado»", async () => {
    const motivo = "El contacto ha bloqueado los mensajes de este número."
    detalles(() => HttpResponse.json(detalle()))
    posts(() =>
      HttpResponse.json(respuesta({ estado: "FALLIDO", codigoError: 21610, motivoError: motivo }), { status: 201 }),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    const alerta = await within(dialogo).findByRole("alert")
    expect(alerta).toHaveTextContent("La respuesta no ha llegado")
    expect(alerta).toHaveTextContent(motivo)
    expect(alerta.querySelector("svg")).not.toBeNull()
    expect(within(seccion(dialogo)).getByText("No ha llegado")).toBeInTheDocument()
  })

  it("SIN_CONFIRMAR: aviso ámbar con el motivo, anunciado en la región de estado", async () => {
    const motivo = "No sabemos si ha llegado. Compruébalo antes de volver a enviarlo."
    detalles(() => HttpResponse.json(detalle()))
    posts(() => HttpResponse.json(respuesta({ estado: "SIN_CONFIRMAR", motivoError: motivo }), { status: 201 }))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    const estado = await within(seccion(dialogo)).findByRole("status")
    await waitFor(() => expect(estado).toHaveTextContent(motivo))
    expect(within(estado).getByText(motivo).closest("[data-aviso-whatsapp]")).toHaveClass("bg-warning")
    expect(within(seccion(dialogo)).getByText("Sin confirmar")).toBeInTheDocument()
  })
})

describe("Sección WhatsApp: independiente del formulario del trámite", () => {
  it("responder con cambios sin guardar no los descarta, y Guardar sigue mandando la misma versión", async () => {
    detalles(() => HttpResponse.json(detalle({ version: 3 })))
    posts(() => HttpResponse.json(respuesta(), { status: 201 }))
    let cuerpoPatch: unknown
    server.use(
      http.patch(apiUrl("/tramites/7"), async ({ request }) => {
        cuerpoPatch = await request.json()
        return HttpResponse.json(detalle({ version: 4, crotales: [] }))
      }),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Añadir crotal"))
    await user.type(within(dialogo).getByRole("textbox", { name: "Crotal 1" }), "ES010000001234")
    expect(boton(dialogo, "Guardar")).toBeEnabled()

    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    await within(dialogo).findByText("Respuesta aceptada por WhatsApp.")

    expect(within(dialogo).getByRole("textbox", { name: "Crotal 1" })).toHaveValue("ES010000001234")
    expect(boton(dialogo, "Guardar")).toBeEnabled()
    await user.click(boton(dialogo, "Guardar"))
    await waitFor(() => expect(cuerpoPatch).toEqual({ version: 3, crotales: ["ES010000001234"] }))
  })
})

// ---------------------------------------------------------------------------------------------

describe("Sección WhatsApp: la ventana se cierra con el panel abierto (N1)", () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it("si la hora del equipo pasa el final de la ventana, Enviar queda desactivado y se avisa", async () => {
    vi.useFakeTimers({ toFake: ["Date"] })
    detalles(() => HttpResponse.json(detalle({ whatsapp: whatsapp({ ventanaAbiertaHasta: enMinutos(30) }) })))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    expect(boton(dialogo, "Enviar por WhatsApp")).toBeEnabled()

    vi.setSystemTime(Date.now() + 31 * 60_000)
    await user.keyboard(" ")
    expect(within(seccion(dialogo)).getByText(TEXTO_VENTANA_CERRADA)).toBeInTheDocument()
    expect(boton(dialogo, "Enviar por WhatsApp")).toBeDisabled()
  })
})

// ---------------------------------------------------------------------------------------------

/** La misma lista que TramiteReviewDialog.test.tsx y FichaOvzPage.test.tsx (no se toca). */
const PROHIBIDAS = /enviad|ejecutad|registrad|en curso en OVZ|tramitad|completad|procesad|presentad/i

describe("Sección WhatsApp: honestidad con los avisos del panel a la vista (N7)", () => {
  const casos: { nombre: string; respuestaPost: () => Response; esperado: string | RegExp }[] = [
    {
      nombre: "aceptada",
      respuestaPost: () => HttpResponse.json(respuesta(), { status: 201 }),
      esperado: "Respuesta aceptada por WhatsApp.",
    },
    {
      nombre: "saliendo (ENVIANDO en un 200)",
      respuestaPost: () => HttpResponse.json(respuesta({ estado: "ENVIANDO" }), { status: 200 }),
      esperado: /La respuesta está saliendo/,
    },
    {
      nombre: "FALLIDO",
      respuestaPost: () =>
        HttpResponse.json(
          respuesta({
            estado: "FALLIDO",
            codigoError: 63016,
            motivoError:
              "Han pasado más de 24 horas desde su último mensaje. WhatsApp no deja escribirle hasta que vuelva a escribir.",
          }),
          { status: 201 },
        ),
      esperado: "La respuesta no ha llegado",
    },
    {
      nombre: "SIN_CONFIRMAR",
      respuestaPost: () =>
        HttpResponse.json(
          respuesta({
            estado: "SIN_CONFIRMAR",
            motivoError: "No sabemos si ha llegado. Compruébalo antes de volver a enviarlo.",
          }),
          { status: 201 },
        ),
      esperado: /No sabemos si ha llegado/,
    },
    { nombre: "error de red", respuestaPost: () => HttpResponse.error(), esperado: /No se ha podido comprobar si ha salido/ },
    {
      nombre: "500 sin motivo",
      respuestaPost: () => new HttpResponse(null, { status: 500 }),
      esperado: /No se ha podido comprobar si ha salido/,
    },
    {
      nombre: "409",
      respuestaPost: () =>
        HttpResponse.json({ motivo: "Esta respuesta ya se envió con otro texto. Escribe otra." }, { status: 409 }),
      esperado: "Esta respuesta ya se envió con otro texto. Escribe otra.",
    },
    {
      nombre: "503",
      respuestaPost: () =>
        HttpResponse.json(
          { motivo: "El envío por WhatsApp no está configurado. Ponte en contacto con Ganera." },
          { status: 503 },
        ),
      esperado: "El envío por WhatsApp no está configurado. Ponte en contacto con Ganera.",
    },
  ]

  for (const caso of casos) {
    it(`${caso.nombre}: nada dice que algo se haya enviado, registrado o presentado`, async () => {
      detalles(() => HttpResponse.json(detalle()))
      posts(caso.respuestaPost)
      const user = userEvent.setup()
      renderDialog()
      const dialogo = await dialogoListo()
      await abrirYCompletar(user, dialogo)
      await user.click(boton(dialogo, "Enviar por WhatsApp"))
      expect((await within(dialogo).findAllByText(caso.esperado)).length).toBeGreaterThan(0)
      expect(dialogo).not.toHaveTextContent(PROHIBIDAS)
    })
  }
})

// ---------------------------------------------------------------------------------------------

describe("Sección WhatsApp: cerrar el modal con una respuesta a medias (N8)", () => {
  const PREGUNTA = "Tienes una respuesta sin enviar."

  it("con el texto editado, la X pregunta en línea; «Seguir escribiendo» vuelve al texto, que se conserva", async () => {
    detalles(() => HttpResponse.json(detalle()))
    const user = userEvent.setup()
    const { onClose } = renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Cerrar"))

    expect(onClose).not.toHaveBeenCalled()
    const panel = within(dialogo).getByText(PREGUNTA).closest("[data-panel-whatsapp]") as HTMLElement
    expect(panel).not.toBeNull()
    expect(boton(panel, "Seguir escribiendo")).toHaveFocus()
    expect(within(panel).queryByRole("button", { name: "Enviar por WhatsApp" })).toBeNull()

    await user.click(boton(panel, "Seguir escribiendo"))
    expect(within(dialogo).queryByText(PREGUNTA)).toBeNull()
    expect(campoRespuesta(dialogo)).toHaveFocus()
    expect(campoRespuesta(dialogo).value).toContain("necesitamos: la fecha")
  })

  it("«Descartar y cerrar» cierra el modal", async () => {
    detalles(() => HttpResponse.json(detalle()))
    const user = userEvent.setup()
    const { onClose } = renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Cerrar"))
    await user.click(boton(dialogo, "Descartar y cerrar"))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it("sin editar el texto propuesto, la X cierra sin preguntar", async () => {
    detalles(() => HttpResponse.json(detalle({ estado: "APROBADO" })))
    const user = userEvent.setup()
    const { onClose } = renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Responder por WhatsApp"))
    await user.click(boton(dialogo, "Cerrar"))
    expect(onClose).toHaveBeenCalledTimes(1)
    expect(within(dialogo).queryByText(PREGUNTA)).toBeNull()
  })

  it("con el texto editado, Esc pregunta en vez de descartar; un segundo Esc retira la pregunta y el texto sigue", async () => {
    detalles(() => HttpResponse.json(detalle()))
    const user = userEvent.setup()
    const { onClose } = renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.keyboard("{Escape}")
    expect(within(dialogo).getByText(PREGUNTA)).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()

    await user.keyboard("{Escape}")
    expect(within(dialogo).queryByText(PREGUNTA)).toBeNull()
    expect(campoRespuesta(dialogo).value).toContain("necesitamos: la fecha")
    expect(onClose).not.toHaveBeenCalled()
  })

  it("con cambios sin guardar además, tras descartar la respuesta sigue la pregunta de los cambios", async () => {
    detalles(() => HttpResponse.json(detalle()))
    const user = userEvent.setup()
    const { onClose } = renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Añadir crotal"))
    await user.type(within(dialogo).getByRole("textbox", { name: "Crotal 1" }), "ES010000001234")
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Cerrar"))
    await user.click(boton(dialogo, "Descartar y cerrar"))

    expect(onClose).not.toHaveBeenCalled()
    expect(within(dialogo).getByText("Tienes cambios sin guardar.")).toBeInTheDocument()
  })

  it("la pregunta tampoco dice nada prohibido", async () => {
    detalles(() => HttpResponse.json(detalle()))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Cerrar"))
    expect(within(dialogo).getByText(PREGUNTA)).toBeInTheDocument()
    expect(dialogo).not.toHaveTextContent(PROHIBIDAS)
  })
})

// ---------------------------------------------------------------------------------------------

describe("Sección WhatsApp: el foco nunca se pierde (N2, smoke de la T3)", () => {
  it("mientras se envía, el foco sigue en «Enviando…» (aria-disabled, no disabled) y un Enter más no manda otra", async () => {
    detalles(() => HttpResponse.json(detalle()))
    const p = puerta()
    const cuerpos = posts(async () => {
      await p.promesa
      return HttpResponse.json(respuesta(), { status: 201 })
    })
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    boton(dialogo, "Enviar por WhatsApp").focus()
    await user.keyboard("{Enter}")
    const enviando = await within(dialogo).findByRole("button", { name: "Enviando…" })
    expect(enviando).not.toBeDisabled()
    expect(enviando).toHaveAttribute("aria-disabled", "true")
    expect(enviando).toHaveFocus()
    await user.keyboard("{Enter}")
    p.abrir()
    expect(await within(dialogo).findByText("Respuesta aceptada por WhatsApp.")).toBeInTheDocument()
    expect(cuerpos).toHaveLength(1)
    expect(boton(dialogo, "Responder por WhatsApp")).toHaveFocus()
  })

  it("un segundo clic en «Enviando…» (aria-disabled, admite clics) mientras se envía no lanza otra petición", async () => {
    detalles(() => HttpResponse.json(detalle()))
    const p = puerta()
    const cuerpos = posts(async () => {
      await p.promesa
      return HttpResponse.json(respuesta(), { status: 201 })
    })
    const user = userEvent.setup({ pointerEventsCheck: 0 })
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    const enviando = await within(dialogo).findByRole("button", { name: "Enviando…" })
    await waitFor(() => expect(cuerpos).toHaveLength(1))
    await user.click(enviando)
    await user.click(enviando)
    await new Promise((r) => setTimeout(r, 50))
    expect(cuerpos).toHaveLength(1)
    p.abrir()
    expect(await within(dialogo).findByText("Respuesta aceptada por WhatsApp.")).toBeInTheDocument()
    expect(cuerpos).toHaveLength(1)
  })

  it("tras un 409 de ventana cerrada, el foco va a la alerta del panel (que queda a la vista)", async () => {
    const motivo =
      "Han pasado más de 24 horas desde su último mensaje. WhatsApp no deja escribirle hasta que vuelva a escribir."
    detalles(
      () => HttpResponse.json(detalle()),
      () => HttpResponse.json(detalle({ whatsapp: whatsapp({ motivoNoDisponible: motivo, ventanaAbiertaHasta: null }) })),
    )
    posts(() => HttpResponse.json({ motivo }, { status: 409 }))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    boton(dialogo, "Enviar por WhatsApp").focus()
    await user.keyboard("{Enter}")
    const alerta = await within(dialogo).findByRole("alert")
    expect(alerta).toHaveTextContent(motivo)
    await waitFor(() => expect(alerta).toHaveFocus())
    await waitFor(() => expect(boton(dialogo, "Enviar por WhatsApp")).toBeDisabled())
    expect(alerta).toHaveFocus()
  })

  it("tras un error, la alerta además se desplaza a la vista (Firefox no lo hace al enfocar: T3b)", async () => {
    const desplazar = vi.fn()
    const original = Element.prototype.scrollIntoView
    Element.prototype.scrollIntoView = desplazar
    try {
      detalles(() => HttpResponse.json(detalle()))
      posts(() => HttpResponse.error())
      const user = userEvent.setup()
      renderDialog()
      const dialogo = await dialogoListo()
      await abrirYCompletar(user, dialogo)
      await user.click(boton(dialogo, "Enviar por WhatsApp"))
      const alerta = await within(dialogo).findByRole("alert")
      await waitFor(() => expect(desplazar.mock.contexts).toContain(alerta))
      expect(desplazar).toHaveBeenCalledWith({ block: "nearest" })
    } finally {
      Element.prototype.scrollIntoView = original
    }
  })

  it("tras un error de red, el foco va a la alerta; reintentar sigue a un Tab", async () => {
    detalles(() => HttpResponse.json(detalle()))
    posts(() => HttpResponse.error())
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    const alerta = await within(dialogo).findByRole("alert")
    await waitFor(() => expect(alerta).toHaveFocus())
  })
})

// ---------------------------------------------------------------------------------------------

describe("Sección WhatsApp: el modal se cierra aunque acabe en «no encontrado» (M1b, N-d)", () => {
  it("Guardar da 404 y la recarga también, con el panel abierto y el texto editado: un Esc cierra el modal", async () => {
    detalles(
      () => HttpResponse.json(detalle()),
      () => new HttpResponse(null, { status: 404 }),
    )
    server.use(http.patch(apiUrl("/tramites/7"), () => new HttpResponse(null, { status: 404 })))
    const user = userEvent.setup()
    const { onClose } = renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Añadir crotal"))
    await user.type(within(dialogo).getByRole("textbox", { name: "Crotal 1" }), "ES010000001234")
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Guardar"))
    await within(dialogo).findByText("Trámite no disponible")
    await user.keyboard("{Escape}")
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it("lo mismo con la X", async () => {
    detalles(
      () => HttpResponse.json(detalle()),
      () => new HttpResponse(null, { status: 404 }),
    )
    server.use(http.patch(apiUrl("/tramites/7"), () => new HttpResponse(null, { status: 404 })))
    const user = userEvent.setup()
    const { onClose } = renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Añadir crotal"))
    await user.type(within(dialogo).getByRole("textbox", { name: "Crotal 1" }), "ES010000001234")
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Guardar"))
    await within(dialogo).findByText("Trámite no disponible")
    await user.click(boton(dialogo, "Cerrar"))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it("un 409 al responder cuya recarga da 404: un Esc cierra el modal", async () => {
    detalles(
      () => HttpResponse.json(detalle()),
      () => new HttpResponse(null, { status: 404 }),
    )
    posts(() => HttpResponse.json({ motivo: "En este estado no se puede responder por WhatsApp." }, { status: 409 }))
    const user = userEvent.setup()
    const { onClose } = renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    await user.click(boton(dialogo, "Enviar por WhatsApp"))
    await within(dialogo).findByText("Trámite no disponible")
    await user.keyboard("{Escape}")
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})

describe("Sección WhatsApp: un mensaje descartado como sin trámite (SIN_TRAMITE, D9)", () => {
  const MOTIVO_DESCARTADO = "Este mensaje se descartó como sin trámite; no se responde desde Ganera."
  const SIN_TRAMITE: Partial<TramiteDetalle> = {
    mensajeOriginal: "¿abrís el lunes?",
    tipoTramite: null,
    estadoExtraccion: "SIN_TRAMITE",
    descartable: true,
  }
  const DESCARTADO: Partial<TramiteDetalle> = {
    ...SIN_TRAMITE,
    estado: "RECHAZADO",
    motivoRechazo: "SIN_TRAMITE",
    descartable: false,
    historialDescarte: [{ tipo: "DESCARTADO", usuarioNombre: "Ana Pérez", en: "2026-10-12T08:00:00Z" }],
    whatsapp: whatsapp({ motivoNoDisponible: MOTIVO_DESCARTADO }),
  }

  it("descartado: el motivo del backend como aviso fijo y ningún «Responder»", async () => {
    detalles(() => HttpResponse.json(detalle(DESCARTADO)))
    renderDialog()
    const s = seccion(await dialogoListo())
    const aviso = within(s).getByText(MOTIVO_DESCARTADO)
    expect(aviso.closest("[data-aviso-whatsapp]")).not.toBeNull()
    expect(within(s).queryByRole("button", { name: /Responder/ })).toBeNull()
    expect(within(s).queryByRole("textbox")).toBeNull()
  })

  it("antes de descartar se puede responder; al descartar desaparece «Responder» y sale el motivo", async () => {
    detalles(() => HttpResponse.json(detalle(SIN_TRAMITE)))
    server.use(http.post(apiUrl("/tramites/7/descartar"), () => HttpResponse.json(detalle({ ...DESCARTADO, version: 1 }))))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    expect(boton(seccion(dialogo), "Responder por WhatsApp")).toBeEnabled()

    await user.click(boton(dialogo, "Descartar"))
    expect(await within(seccion(dialogo)).findByText(MOTIVO_DESCARTADO)).toBeInTheDocument()
    expect(within(seccion(dialogo)).queryByRole("button", { name: "Responder por WhatsApp" })).toBeNull()
  })

  it("tras deshacer el descarte vuelve «Responder por WhatsApp»", async () => {
    detalles(() => HttpResponse.json(detalle(DESCARTADO)))
    server.use(
      http.post(apiUrl("/tramites/7/deshacer-descarte"), () =>
        HttpResponse.json(detalle({ ...SIN_TRAMITE, version: 1 })),
      ),
    )
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await user.click(boton(dialogo, "Deshacer"))

    expect(await within(seccion(dialogo)).findByRole("button", { name: "Responder por WhatsApp" })).toBeEnabled()
    expect(within(seccion(dialogo)).queryByText(MOTIVO_DESCARTADO)).toBeNull()
  })
})

describe("Sección WhatsApp: «cierra pronto» no bloquea (N-c)", () => {
  it("con menos de 1 h de ventana, el aviso sale y «Enviar por WhatsApp» sigue activo", async () => {
    detalles(() => HttpResponse.json(detalle({ whatsapp: whatsapp({ ventanaAbiertaHasta: enMinutos(30) }) })))
    const user = userEvent.setup()
    renderDialog()
    const dialogo = await dialogoListo()
    await abrirYCompletar(user, dialogo)
    expect(within(seccion(dialogo)).getByText(/^La ventana se cierra a las \d{2}:\d{2}$/)).toBeInTheDocument()
    expect(boton(dialogo, "Enviar por WhatsApp")).toBeEnabled()
  })
})
