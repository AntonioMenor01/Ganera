import { render, screen } from "@testing-library/react"
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
