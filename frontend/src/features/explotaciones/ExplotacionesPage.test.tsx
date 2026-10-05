import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { createMemoryRouter, RouterProvider, useParams } from "react-router-dom"
import { describe, expect, it } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { ExplotacionesPage } from "./ExplotacionesPage"

function DestinoGanadero() {
  const { id } = useParams()
  return <p>Ficha del ganadero {id}</p>
}

/** La página tiene enlaces de router (nombre del ganadero), así que se monta dentro de uno. */
function montar() {
  const router = createMemoryRouter(
    [
      { path: "/explotaciones", element: <ExplotacionesPage /> },
      { path: "/ganaderos/:id", element: <DestinoGanadero /> },
    ],
    { initialEntries: ["/explotaciones"] },
  )
  render(<RouterProvider router={router} />)
}

function paginaDe(numero: number, totalPages: number) {
  return {
    content: [
      {
        id: numero + 1,
        codigoRega: `ES00${numero}`,
        nombre: `Explotación ${numero}`,
        ganaderoId: 1,
        nombreGanadero: "Ganadero",
      },
    ],
    totalElements: totalPages,
    totalPages,
    number: numero,
    size: 20,
  }
}

describe("ExplotacionesPage: errores visibles", () => {
  it("si falla la carga del listado, lo dice y Reintentar vuelve a pedirlo (M2)", async () => {
    let llamadas = 0
    server.use(
      http.get(apiUrl("/explotaciones"), () => {
        llamadas += 1
        if (llamadas === 1) return new HttpResponse(null, { status: 500 })
        return HttpResponse.json(paginaDe(0, 1))
      }),
    )
    const user = userEvent.setup()
    montar()

    expect(await screen.findByText("No se han podido cargar las explotaciones")).toBeInTheDocument()
    expect(screen.getByText("No se han podido cargar las explotaciones").closest("[role=alert]")!.firstElementChild?.matches('svg[aria-hidden="true"]')).toBe(true)
    expect(
      screen.getByText("Ha fallado algo en el servidor. Inténtalo de nuevo; si se repite, avísanos."),
    ).toBeInTheDocument()

    await user.click(screen.getByRole("button", { name: "Reintentar" }))
    expect(await screen.findByText("ES000")).toBeInTheDocument()
    expect(screen.queryByText("No se han podido cargar las explotaciones")).not.toBeInTheDocument()
  })

  it("si falla una página intermedia, la paginación sigue ahí para salir de ella (M3)", async () => {
    server.use(
      http.get(apiUrl("/explotaciones"), ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get("page"))
        if (page === 1) return HttpResponse.error()
        return HttpResponse.json(paginaDe(page, 3))
      }),
    )
    const user = userEvent.setup()
    montar()

    await screen.findByText("ES000")
    await user.click(screen.getByRole("button", { name: "Siguiente" }))
    expect(await screen.findByText("No se han podido cargar las explotaciones")).toBeInTheDocument()

    const anterior = screen.getByRole("button", { name: "Anterior" })
    expect(anterior).toBeEnabled()
    expect(screen.getByRole("button", { name: "Siguiente" })).toBeEnabled()
    expect(screen.getByText("Página 2 de 3")).toBeInTheDocument()

    await user.click(anterior)
    expect(await screen.findByText("ES000")).toBeInTheDocument()
    expect(screen.queryByText("No se han podido cargar las explotaciones")).not.toBeInTheDocument()
  })
})

describe("ExplotacionesPage: enlace al ganadero (decisión 14)", () => {
  it("el nombre del ganadero enlaza con su ficha", async () => {
    server.use(
      http.get(apiUrl("/explotaciones"), () =>
        HttpResponse.json({
          content: [
            { id: 1, codigoRega: "ES001", nombre: "Finca", ganaderoId: 42, nombreGanadero: "Pedro Sanz" },
          ],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 20,
        }),
      ),
    )
    const user = userEvent.setup()
    montar()

    const enlace = await screen.findByRole("link", { name: "Pedro Sanz" })
    expect(enlace).toHaveAttribute("href", "/ganaderos/42")
    // Enlace en columna de tabla: Tinta en reposo; Rojo 700 solo al apuntar o con foco.
    expect(enlace).toHaveClass("text-foreground", "hover:text-enlace", "focus-visible:text-enlace", "focus-visible:ring-ring")
    expect(enlace).not.toHaveClass("text-enlace")
    await user.click(enlace)
    expect(await screen.findByText("Ficha del ganadero 42")).toBeInTheDocument()
  })
})
