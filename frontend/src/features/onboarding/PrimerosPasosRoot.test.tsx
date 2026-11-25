import { act, render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { createMemoryRouter, RouterProvider, type RouteObject } from "react-router-dom"
import { afterEach, beforeEach, describe, expect, it } from "vitest"
import { AuthProvider } from "@/shared/auth/AuthContext"
import { PrimerosPasosRoot } from "./PrimerosPasosRoot"
import {
  abrirPrimerosPasos,
  configurarAutoAperturaPrimerosPasos,
  guiaYaVista,
  PASOS_GUIA,
} from "./primerosPasos"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"

const USUARIO = { id: 42, email: "nuevo@gestoria.es", nombre: "Nuevo", gestoriaId: 3, activo: true }
const PRIMER_TITULO = PASOS_GUIA[0].titulo

function rutas(): RouteObject[] {
  return [
    { path: "/", element: <PrimerosPasosRoot /> },
    { path: "/configuracion", element: <h1>CONFIG</h1> },
  ]
}

function montar() {
  const router = createMemoryRouter(rutas(), { initialEntries: ["/"] })
  render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  )
  return router
}

beforeEach(() => {
  // Este sí prueba la autoapertura (el setup global la desactiva por defecto).
  configurarAutoAperturaPrimerosPasos(true)
  sessionStorage.setItem("ganera.token", "tok")
  server.use(http.get(apiUrl("/auth/me"), () => HttpResponse.json(USUARIO)))
})

afterEach(() => {
  sessionStorage.clear()
})

describe("PrimerosPasosRoot", () => {
  it("se abre sola la primera vez de un usuario que no la ha visto", async () => {
    montar()
    expect(await screen.findByRole("heading", { name: PRIMER_TITULO })).toBeInTheDocument()
  })

  it("al cerrarla queda marcada como vista y no se vuelve a abrir sola", async () => {
    montar()
    await screen.findByRole("heading", { name: PRIMER_TITULO })

    await userEvent.click(screen.getByRole("button", { name: "Cerrar la guía" }))
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument())
    expect(guiaYaVista(USUARIO.id)).toBe(true)

    // Un remontaje (otra carga de la app) ya no la abre sola.
    montar()
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument())
  })

  it("se puede reabrir a mano con abrirPrimerosPasos()", async () => {
    montar()
    await screen.findByRole("heading", { name: PRIMER_TITULO })
    await userEvent.click(screen.getByRole("button", { name: "Cerrar la guía" }))
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument())

    act(() => abrirPrimerosPasos())
    expect(await screen.findByRole("heading", { name: PRIMER_TITULO })).toBeInTheDocument()
  })

  it("una CTA cierra la guía y navega a la pantalla real", async () => {
    montar()
    await screen.findByRole("heading", { name: PRIMER_TITULO })
    // Avanzar al paso de OVZ (su CTA va a /configuracion).
    await userEvent.click(screen.getByRole("button", { name: /Siguiente/ }))
    const cta = PASOS_GUIA[1].cta!
    await userEvent.click(screen.getByRole("button", { name: new RegExp(cta.label) }))

    expect(await screen.findByRole("heading", { name: "CONFIG" })).toBeInTheDocument()
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
  })
})
