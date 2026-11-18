import { render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { describe, expect, it } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { GanaderoDetallePage } from "./GanaderoDetallePage"

function ganadero(extra: Record<string, unknown> = {}) {
  return {
    id: 7,
    nombre: "Ana Ruiz Gómez",
    nif: "12345678Z",
    telefono: null,
    tieneCredencialesOvz: false,
    usaCertificadoOvz: false,
    explotaciones: [],
    ...extra,
  }
}

function montar() {
  const router = createMemoryRouter(
    [
      { path: "/ganaderos", element: <p>Listado de ganaderos</p> },
      { path: "/ganaderos/:id", element: <GanaderoDetallePage /> },
    ],
    { initialEntries: ["/ganaderos/7"] },
  )
  render(<RouterProvider router={router} />)
  return router
}

async function seccionOvz() {
  return await screen.findByRole("region", { name: "Acceso a OVZ.net" })
}

describe("GanaderoDetallePage: Acceso a OVZ", () => {
  it("sin credenciales muestra «No configuradas» y el botón de añadir", async () => {
    server.use(http.get(apiUrl("/ganaderos/:id"), () => HttpResponse.json(ganadero())))
    montar()

    const seccion = await seccionOvz()
    expect(within(seccion).getByText("No configuradas")).toBeInTheDocument()
    expect(within(seccion).getByRole("button", { name: "Añadir credenciales" })).toBeInTheDocument()
    expect(within(seccion).queryByRole("button", { name: "Borrar credenciales" })).not.toBeInTheDocument()
  })

  it("con credenciales guardadas muestra el estado, «Cambiar» y «Borrar»", async () => {
    server.use(
      http.get(apiUrl("/ganaderos/:id"), () =>
        HttpResponse.json(ganadero({ tieneCredencialesOvz: true })),
      ),
    )
    montar()

    const seccion = await seccionOvz()
    expect(within(seccion).getByText("Credenciales guardadas")).toBeInTheDocument()
    expect(within(seccion).getByRole("button", { name: "Cambiar credenciales" })).toBeInTheDocument()
    expect(within(seccion).getByRole("button", { name: "Borrar credenciales" })).toBeInTheDocument()
  })

  it("el campo contraseña es type=password y arranca vacío, y guardar envía usuario+password", async () => {
    let cuerpoRecibido: unknown = null
    server.use(
      http.get(apiUrl("/ganaderos/:id"), () => HttpResponse.json(ganadero())),
      http.put(apiUrl("/ganaderos/:id/credenciales-ovz"), async ({ request }) => {
        cuerpoRecibido = await request.json()
        return HttpResponse.json(ganadero({ tieneCredencialesOvz: true }))
      }),
    )
    const usuario = userEvent.setup()
    montar()

    const seccion = await seccionOvz()
    await usuario.click(within(seccion).getByRole("button", { name: "Añadir credenciales" }))

    const password = within(seccion).getByLabelText("Contraseña de OVZ.net")
    expect(password).toHaveAttribute("type", "password")
    expect(password).toHaveValue("")

    await usuario.type(within(seccion).getByLabelText("Usuario de OVZ.net"), "mi-usuario-ovz")
    await usuario.type(password, "mi-password-ovz")
    await usuario.click(within(seccion).getByRole("button", { name: "Guardar" }))

    await waitFor(() => expect(within(seccion).getByText("Credenciales guardadas")).toBeInTheDocument())
    expect(cuerpoRecibido).toEqual({
      usuario: "mi-usuario-ovz",
      password: "mi-password-ovz",
      usaCertificado: false,
    })
  })

  it("«Guardar» está deshabilitado hasta que hay usuario y contraseña (sin certificado)", async () => {
    server.use(http.get(apiUrl("/ganaderos/:id"), () => HttpResponse.json(ganadero())))
    const usuario = userEvent.setup()
    montar()

    const seccion = await seccionOvz()
    await usuario.click(within(seccion).getByRole("button", { name: "Añadir credenciales" }))
    const guardar = within(seccion).getByRole("button", { name: "Guardar" })
    expect(guardar).toBeDisabled()

    await usuario.type(within(seccion).getByLabelText("Usuario de OVZ.net"), "u")
    expect(guardar).toBeDisabled()
    await usuario.type(within(seccion).getByLabelText("Contraseña de OVZ.net"), "p")
    expect(guardar).toBeEnabled()
  })

  it("marcar certificado oculta usuario/contraseña y envía usaCertificado:true", async () => {
    let cuerpoRecibido: unknown = null
    server.use(
      http.get(apiUrl("/ganaderos/:id"), () => HttpResponse.json(ganadero())),
      http.put(apiUrl("/ganaderos/:id/credenciales-ovz"), async ({ request }) => {
        cuerpoRecibido = await request.json()
        return HttpResponse.json(ganadero({ usaCertificadoOvz: true }))
      }),
    )
    const usuario = userEvent.setup()
    montar()

    const seccion = await seccionOvz()
    await usuario.click(within(seccion).getByRole("button", { name: "Añadir credenciales" }))
    await usuario.click(
      within(seccion).getByRole("checkbox", { name: /certificado digital/i }),
    )
    expect(within(seccion).queryByLabelText("Usuario de OVZ.net")).not.toBeInTheDocument()
    expect(within(seccion).queryByLabelText("Contraseña de OVZ.net")).not.toBeInTheDocument()

    await usuario.click(within(seccion).getByRole("button", { name: "Guardar" }))
    await waitFor(() =>
      expect(within(seccion).getByText("Entra con certificado digital")).toBeInTheDocument(),
    )
    expect(cuerpoRecibido).toEqual({ usaCertificado: true })
  })

  it("borrar las credenciales deja la sección en «No configuradas»", async () => {
    server.use(
      http.get(apiUrl("/ganaderos/:id"), () =>
        HttpResponse.json(ganadero({ tieneCredencialesOvz: true })),
      ),
      http.delete(apiUrl("/ganaderos/:id/credenciales-ovz"), () => HttpResponse.json(ganadero())),
    )
    const usuario = userEvent.setup()
    montar()

    const seccion = await seccionOvz()
    await usuario.click(within(seccion).getByRole("button", { name: "Borrar credenciales" }))
    await waitFor(() => expect(within(seccion).getByText("No configuradas")).toBeInTheDocument())
  })

  it("un 400 del backend conserva el formulario y muestra el motivo", async () => {
    server.use(
      http.get(apiUrl("/ganaderos/:id"), () => HttpResponse.json(ganadero())),
      http.put(apiUrl("/ganaderos/:id/credenciales-ovz"), () =>
        HttpResponse.json({ motivo: "Indica el usuario y la contraseña de OVZ.net." }, { status: 400 }),
      ),
    )
    const usuario = userEvent.setup()
    montar()

    const seccion = await seccionOvz()
    await usuario.click(within(seccion).getByRole("button", { name: "Añadir credenciales" }))
    await usuario.type(within(seccion).getByLabelText("Usuario de OVZ.net"), "u")
    await usuario.type(within(seccion).getByLabelText("Contraseña de OVZ.net"), "p")
    await usuario.click(within(seccion).getByRole("button", { name: "Guardar" }))

    await waitFor(() =>
      expect(
        within(seccion).getByText("Indica el usuario y la contraseña de OVZ.net."),
      ).toBeInTheDocument(),
    )
    // El formulario sigue abierto (el usuario conserva lo escrito).
    expect(within(seccion).getByLabelText("Usuario de OVZ.net")).toHaveValue("u")
  })
})
