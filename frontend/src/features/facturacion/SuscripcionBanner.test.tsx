import { act, render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { MemoryRouter } from "react-router-dom"
import { describe, expect, it } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { SuscripcionBanner } from "./SuscripcionBanner"
import { useSuscripcionEstado } from "./useSuscripcionEstado"

/** El banner con el hook real, igual que lo monta AppLayout. */
function BannerConHook() {
  const suscripcion = useSuscripcionEstado()
  return <SuscripcionBanner suscripcion={suscripcion} />
}

describe("SuscripcionBanner: errores visibles (M2)", () => {
  it("si no se puede cargar la suscripción, lo dice (antes no se veía nada) y permite reintentar", async () => {
    let llamadas = 0
    server.use(
      http.get(apiUrl("/facturacion/suscripcion"), () => {
        llamadas += 1
        if (llamadas === 1) return HttpResponse.error()
        return HttpResponse.json({
          estado: "SUSPENDIDA",
          puedeAprobarTramites: false,
          explotacionesContratadas: 3,
        })
      }),
    )
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <BannerConHook />
      </MemoryRouter>,
    )

    expect(await screen.findByText("No se ha podido comprobar tu suscripción")).toBeInTheDocument()
    expect(
      screen.getByText("No se ha podido conectar con Ganera. Inténtalo de nuevo en unos segundos."),
    ).toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "Reintentar" }))
    expect(await screen.findByText("No puedes aprobar trámites ahora mismo")).toBeInTheDocument()
    expect(screen.queryByText("No se ha podido comprobar tu suscripción")).not.toBeInTheDocument()
  })
})

/** Monta el banner con /facturacion/suscripcion devolviendo `respuesta`. */
async function montarConRespuesta(respuesta: () => Response) {
  server.use(http.get(apiUrl("/facturacion/suscripcion"), respuesta))
  render(
    <MemoryRouter>
      <BannerConHook />
    </MemoryRouter>,
  )
}

function estadoJson(estado: string, puedeAprobarTramites: boolean) {
  return () => HttpResponse.json({ estado, puedeAprobarTramites, explotacionesContratadas: 3 })
}

/** Ningún banner remite a ninguna parte: sin enlaces, sin botones, sin mailto (D2). */
function sinAcciones(alerta: HTMLElement) {
  expect(within(alerta).queryByRole("link")).not.toBeInTheDocument()
  expect(within(alerta).queryByRole("button")).not.toBeInTheDocument()
  expect(alerta.querySelector("a")).toBeNull()
  expect(alerta.innerHTML).not.toMatch(/mailto:|facturacion/i)
}

describe("SuscripcionBanner: textos sin pago en la app (D2)", () => {
  it("TRIAL_EXPIRADO_SIN_PAGO: bloqueante, con el texto de contacto y sin acciones", async () => {
    await montarConRespuesta(estadoJson("TRIAL_EXPIRADO_SIN_PAGO", false))
    const alerta = await screen.findByRole("alert")
    expect(within(alerta).getByText("No puedes aprobar trámites ahora mismo")).toBeInTheDocument()
    expect(
      within(alerta).getByText(
        "El periodo de prueba ha terminado y no hay ningún pago activo, así que no se pueden aprobar trámites. Ponte en contacto con Ganera para regularizar la suscripción.",
      ),
    ).toBeInTheDocument()
    sinAcciones(alerta)
  })

  it("SUSPENDIDA: bloqueante, con el texto de contacto y sin acciones", async () => {
    await montarConRespuesta(estadoJson("SUSPENDIDA", false))
    const alerta = await screen.findByRole("alert")
    expect(within(alerta).getByText("No puedes aprobar trámites ahora mismo")).toBeInTheDocument()
    expect(
      within(alerta).getByText(
        "La suscripción está suspendida y no se pueden aprobar trámites. Ponte en contacto con Ganera para regularizarla.",
      ),
    ).toBeInTheDocument()
    sinAcciones(alerta)
  })

  it("sin Suscripcion (404): el título no repite la descripción, que pide contactar con Ganera", async () => {
    await montarConRespuesta(() => new HttpResponse(null, { status: 404 }))
    const alerta = await screen.findByRole("alert")
    const descripcion = "Todavía no tienes una suscripción activa. Ponte en contacto con Ganera para activarla."
    expect(within(alerta).getByText(descripcion)).toBeInTheDocument()
    expect(within(alerta).getByText("No puedes aprobar trámites ahora mismo")).toBeInTheDocument()
    expect(within(alerta).queryByText(/^Todavía no tienes una suscripción$/)).not.toBeInTheDocument()
    sinAcciones(alerta)
  })

  it("IMPAGO_GRACIA: «Aviso de pago», con el texto de contacto y sin acciones", async () => {
    await montarConRespuesta(estadoJson("IMPAGO_GRACIA", true))
    const alerta = await screen.findByRole("alert")
    expect(within(alerta).getByText("Aviso de pago")).toBeInTheDocument()
    expect(within(alerta).queryByText("Aviso de facturación")).not.toBeInTheDocument()
    expect(
      within(alerta).getByText(
        "El último pago ha fallado. Sigues teniendo acceso completo durante el periodo de gracia; ponte en contacto con Ganera para regularizarlo cuanto antes.",
      ),
    ).toBeInTheDocument()
    sinAcciones(alerta)
  })

  it.each(["TRIAL", "ACTIVA"])("%s: no hay banner", async (estado) => {
    let respondido = false
    await montarConRespuesta(() => {
      respondido = true
      return HttpResponse.json({ estado, puedeAprobarTramites: true, explotacionesContratadas: 3 })
    })
    await waitFor(() => expect(respondido).toBe(true))
    // Un ciclo más para que el hook aplique la respuesta: sigue sin haber banner.
    await act(async () => {})
    expect(screen.queryByRole("alert")).not.toBeInTheDocument()
  })

  it("error al cargar: la única acción es «Reintentar»", async () => {
    await montarConRespuesta(() => HttpResponse.error())
    const alerta = await screen.findByRole("alert")
    expect(within(alerta).getByText("No se ha podido comprobar tu suscripción")).toBeInTheDocument()
    expect(within(alerta).getAllByRole("button").map((b) => b.textContent)).toEqual(["Reintentar"])
    expect(within(alerta).queryByRole("link")).not.toBeInTheDocument()
  })
})
