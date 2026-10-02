import { render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { describe, expect, it } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { GanaderoDetallePage } from "./GanaderoDetallePage"

// m1 (Task 7), ahora con el componente real de la Task 8: el panel de animales solo se monta al
// desplegar, así que la petición de animales sale al abrirlo (y solo la de esa sección), no al
// cargar la ficha. Se comprueba por las peticiones reales y por lo que pinta, no por un doble.
describe("GanaderoDetallePage: animales solo al desplegar (m1)", () => {
  it("no se pide nada hasta pulsar 'Ver animales', y solo lo de esa sección; plegar lo desmonta", async () => {
    const peticiones: string[] = []
    const escuchar = ({ request }: { request: Request }) => {
      peticiones.push(new URL(request.url).pathname)
    }
    server.events.on("request:start", escuchar)
    server.use(
      http.get(apiUrl("/ganaderos/7"), () =>
        HttpResponse.json({
          id: 7,
          nombre: "Ana",
          nif: null,
          explotaciones: [
            { id: 1, codigoRega: "ES1", nombre: "Uno", contactos: [] },
            { id: 2, codigoRega: "ES2", nombre: "Dos", contactos: [] },
          ],
        }),
      ),
      http.get(apiUrl("/explotaciones/:id/animales"), ({ params }) =>
        HttpResponse.json({
          content: [{ id: 1, crotal: `ES01000000000${params.id}`, crotalUltimosDigitos: `00000${params.id}` }],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 20,
        }),
      ),
    )
    const router = createMemoryRouter([{ path: "/ganaderos/:id", element: <GanaderoDetallePage /> }], {
      initialEntries: ["/ganaderos/7"],
    })
    const user = userEvent.setup()
    try {
      render(<RouterProvider router={router} />)

      const secciones = await screen.findAllByRole("region", { name: /^ES/ })
      expect(screen.queryByRole("list", { name: "Crotales" })).not.toBeInTheDocument()
      expect(peticiones).toEqual(["/ganaderos/7"])

      await user.click(within(secciones[1]).getByRole("button", { name: "Ver animales" }))
      const lista = await within(secciones[1]).findByRole("list", { name: "Crotales" })
      expect(lista).toHaveTextContent("ES010000000002")
      expect(within(secciones[0]).queryByRole("list", { name: "Crotales" })).not.toBeInTheDocument()
      expect(peticiones).toEqual(["/ganaderos/7", "/explotaciones/2/animales"])

      // Plegar lo desmonta: volver a abrir es una carga nueva.
      await user.click(within(secciones[1]).getByRole("button", { name: "Ver animales" }))
      expect(within(secciones[1]).queryByRole("list", { name: "Crotales" })).not.toBeInTheDocument()
      await user.click(within(secciones[1]).getByRole("button", { name: "Ver animales" }))
      await within(secciones[1]).findByRole("list", { name: "Crotales" })
      expect(peticiones).toEqual(["/ganaderos/7", "/explotaciones/2/animales", "/explotaciones/2/animales"])
    } finally {
      server.events.removeListener("request:start", escuchar)
    }
  })
})
