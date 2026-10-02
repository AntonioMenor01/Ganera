import { render, screen } from "@testing-library/react"
import { http, HttpResponse } from "msw"
import { describe, expect, it } from "vitest"
import { httpClient } from "@/shared/api/httpClient"
import { apiUrl } from "./apiBaseUrl"
import { server } from "./server"

function Saludo({ nombre }: { nombre: string }) {
  return <h1>Hola, {nombre}</h1>
}

describe("infraestructura de tests (humo)", () => {
  it("renderiza un componente y usa los matchers de jest-dom", () => {
    render(<Saludo nombre="Ganera" />)
    expect(screen.getByRole("heading", { name: "Hola, Ganera" })).toBeInTheDocument()
  })

  it("el httpClient real llega a un handler de MSW en la baseURL", async () => {
    let urlRecibida: string | null = null
    server.use(
      http.get(apiUrl("/ping"), ({ request }) => {
        urlRecibida = request.url
        return HttpResponse.json({ ok: true })
      }),
    )

    const respuesta = await httpClient.get<{ ok: boolean }>("/ping")

    expect(urlRecibida).toBe(apiUrl("/ping"))
    expect(respuesta.status).toBe(200)
    expect(respuesta.data).toEqual({ ok: true })
  })
})
