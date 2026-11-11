import { render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { describe, expect, it, vi } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { setAuthToken, setUnauthorizedHandler } from "@/shared/api/authSession"
import {
  PersonasDeExplotacion,
  TEXTO_PARCIAL,
  TEXTO_TELEFONO_409,
  TEXTO_VINCULO_404,
  type PersonaExplotacion,
} from "./PersonasDeExplotacion"

const EXPLOTACION_ID = 10

const PERSONAS: PersonaExplotacion[] = [
  { contactoId: 1, nombre: "Ana Ruiz", telefono: "+34612345678", rol: "TITULAR" },
  { contactoId: 2, nombre: "Luis Peón", telefono: "+34699111222", rol: "EMPLEADO" },
]

function montar(contactos: PersonaExplotacion[] = PERSONAS) {
  const onCambio = vi.fn()
  render(
    <PersonasDeExplotacion
      explotacionId={EXPLOTACION_ID}
      contactos={contactos}
      onCambio={onCambio}
      idEtiquetaLista="contactos-test"
    />,
  )
  return { onCambio }
}

/** Página de Spring Data con todos los contactos en una sola página. */
function pagina(content: unknown[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 100 }
}

function contactoListado(id: number, telefono: string, nombre = `Contacto ${id}`) {
  return { id, telefono, nombre, activo: true, explotaciones: [] }
}

async function abrirFormulario(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: "Añadir persona" }))
  return screen.getByRole("form", { name: "Añadir persona a la explotación" })
}

describe("PersonasDeExplotacion: pintado", () => {
  it("una persona por fila con teléfono tel:, selector de rol y botón de quitar", () => {
    montar()
    const items = screen.getAllByRole("listitem")
    expect(items).toHaveLength(2)

    const ana = items[0]
    expect(within(ana).getByText("Ana Ruiz")).toBeInTheDocument()
    expect(within(ana).getByRole("link", { name: /612 345 678/ })).toHaveAttribute(
      "href",
      "tel:+34612345678",
    )
    expect(within(ana).getByRole("combobox", { name: "Rol de Ana Ruiz" })).toHaveValue("TITULAR")
    expect(
      within(ana).getByRole("button", { name: "Quitar a Ana Ruiz de la explotación" }),
    ).toBeInTheDocument()
  })

  it("sin contactos lo dice y aun así deja añadir", () => {
    montar([])
    expect(screen.getByText("Sin contactos activos en esta explotación.")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Añadir persona" })).toBeInTheDocument()
    expect(screen.queryByRole("list")).not.toBeInTheDocument()
  })
})

describe("PersonasDeExplotacion: cambiar rol y quitar", () => {
  it("cambiar el rol manda POST {explotacionId, rol} y recarga la ficha", async () => {
    let cuerpo: unknown
    let ruta = ""
    server.use(
      http.post(apiUrl("/contactos/:id/explotaciones"), async ({ request, params }) => {
        ruta = String(params.id)
        cuerpo = await request.json()
        return new HttpResponse(null, { status: 200 })
      }),
    )
    const { onCambio } = montar()
    const user = userEvent.setup()

    await user.selectOptions(screen.getByRole("combobox", { name: "Rol de Ana Ruiz" }), "EMPLEADO")

    await waitFor(() => expect(onCambio).toHaveBeenCalledTimes(1))
    expect(ruta).toBe("1")
    expect(cuerpo).toEqual({ explotacionId: EXPLOTACION_ID, rol: "EMPLEADO" })
  })

  it("quitar manda DELETE y recarga la ficha", async () => {
    let ruta = ""
    server.use(
      http.delete(apiUrl("/contactos/:id/explotaciones/:explotacionId"), ({ params }) => {
        ruta = `${params.id}/${params.explotacionId}`
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { onCambio } = montar()
    const user = userEvent.setup()

    await user.click(screen.getByRole("button", { name: "Quitar a Luis Peón de la explotación" }))

    await waitFor(() => expect(onCambio).toHaveBeenCalledTimes(1))
    expect(ruta).toBe(`2/${EXPLOTACION_ID}`)
  })

  it("vínculo 404 al cambiar rol → «No se ha podido, vuelve a cargar», sin recargar", async () => {
    server.use(
      http.post(apiUrl("/contactos/:id/explotaciones"), () => new HttpResponse(null, { status: 404 })),
    )
    const { onCambio } = montar()
    const user = userEvent.setup()

    await user.selectOptions(screen.getByRole("combobox", { name: "Rol de Ana Ruiz" }), "EMPLEADO")

    expect(await screen.findByText(TEXTO_VINCULO_404)).toBeInTheDocument()
    expect(onCambio).not.toHaveBeenCalled()
  })

  it("vínculo 409 (contacto inactivo) → muestra el motivo del backend", async () => {
    server.use(
      http.post(apiUrl("/contactos/:id/explotaciones"), () =>
        HttpResponse.json({ motivo: "El contacto está inactivo. Reactívalo primero." }, { status: 409 }),
      ),
    )
    montar()
    const user = userEvent.setup()

    await user.selectOptions(screen.getByRole("combobox", { name: "Rol de Ana Ruiz" }), "EMPLEADO")

    expect(
      await screen.findByText("El contacto está inactivo. Reactívalo primero."),
    ).toBeInTheDocument()
  })

  it("un 5xx al quitar enseña «Reintentar», que repite la operación con éxito", async () => {
    let intentos = 0
    server.use(
      http.delete(apiUrl("/contactos/:id/explotaciones/:explotacionId"), () => {
        intentos += 1
        return intentos === 1
          ? new HttpResponse(null, { status: 500 })
          : new HttpResponse(null, { status: 204 })
      }),
    )
    const { onCambio } = montar()
    const user = userEvent.setup()

    await user.click(screen.getByRole("button", { name: "Quitar a Ana Ruiz de la explotación" }))
    await user.click(await screen.findByRole("button", { name: "Reintentar" }))

    await waitFor(() => expect(onCambio).toHaveBeenCalledTimes(1))
    expect(intentos).toBe(2)
  })

  it("un 401 al cambiar rol cierra la sesión y no enseña error", async () => {
    setAuthToken("tok")
    const cerrarSesion = vi.fn()
    setUnauthorizedHandler(cerrarSesion)
    server.use(
      http.post(apiUrl("/contactos/:id/explotaciones"), () => new HttpResponse(null, { status: 401 })),
    )
    montar()
    const user = userEvent.setup()

    await user.selectOptions(screen.getByRole("combobox", { name: "Rol de Ana Ruiz" }), "EMPLEADO")

    await waitFor(() => expect(cerrarSesion).toHaveBeenCalledTimes(1))
    expect(screen.queryByRole("alert")).not.toBeInTheDocument()
  })

  it("una petición a la vez: mientras una está en vuelo, los controles se deshabilitan", async () => {
    let soltar!: () => void
    const espera = new Promise<void>((resolve) => {
      soltar = resolve
    })
    server.use(
      http.delete(apiUrl("/contactos/:id/explotaciones/:explotacionId"), async () => {
        await espera
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { onCambio } = montar()
    const user = userEvent.setup()

    await user.click(screen.getByRole("button", { name: "Quitar a Ana Ruiz de la explotación" }))

    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Quitar a Luis Peón de la explotación" })).toBeDisabled(),
    )
    expect(screen.getByRole("combobox", { name: "Rol de Ana Ruiz" })).toBeDisabled()

    soltar()
    await waitFor(() => expect(onCambio).toHaveBeenCalledTimes(1))
  })
})

describe("PersonasDeExplotacion: añadir una persona nueva", () => {
  it("teléfono que no es contacto → crea el contacto y lo vincula", async () => {
    let creado: unknown
    let vinculo: unknown
    let vinculoId = ""
    server.use(
      http.get(apiUrl("/contactos"), () => HttpResponse.json(pagina([]))),
      http.post(apiUrl("/contactos"), async ({ request }) => {
        creado = await request.json()
        return HttpResponse.json(contactoListado(99, "+34600111222", "Marta Nueva"), { status: 201 })
      }),
      http.post(apiUrl("/contactos/:id/explotaciones"), async ({ request, params }) => {
        vinculoId = String(params.id)
        vinculo = await request.json()
        return new HttpResponse(null, { status: 200 })
      }),
    )
    const { onCambio } = montar()
    const user = userEvent.setup()

    const form = await abrirFormulario(user)
    await user.type(within(form).getByLabelText("Teléfono"), "600111222")
    await user.type(within(form).getByLabelText("Nombre"), "Marta Nueva")
    await user.click(within(form).getByRole("button", { name: "Añadir" }))

    await waitFor(() => expect(onCambio).toHaveBeenCalledTimes(1))
    expect(creado).toEqual({ telefono: "600111222", nombre: "Marta Nueva" })
    expect(vinculoId).toBe("99")
    expect(vinculo).toEqual({ explotacionId: EXPLOTACION_ID, rol: "TITULAR" })
    // El formulario se cierra tras el éxito.
    expect(screen.queryByRole("form")).not.toBeInTheDocument()
  })

  it("un teléfono nuevo sin nombre no llama al backend y lo pide", async () => {
    let creaciones = 0
    server.use(
      http.get(apiUrl("/contactos"), () => HttpResponse.json(pagina([]))),
      http.post(apiUrl("/contactos"), () => {
        creaciones += 1
        return HttpResponse.json(contactoListado(99, "+34600111222"), { status: 201 })
      }),
    )
    montar()
    const user = userEvent.setup()

    const form = await abrirFormulario(user)
    await user.type(within(form).getByLabelText("Teléfono"), "600111222")
    await user.click(within(form).getByRole("button", { name: "Añadir" }))

    expect(
      await screen.findByText("El nombre es obligatorio para una persona nueva."),
    ).toBeInTheDocument()
    expect(creaciones).toBe(0)
  })

  it("POST /contactos 409 → texto exacto, nunca el motivo del backend", async () => {
    server.use(
      http.get(apiUrl("/contactos"), () => HttpResponse.json(pagina([]))),
      http.post(apiUrl("/contactos"), () =>
        HttpResponse.json({ motivo: "No se puede usar ese teléfono para un contacto." }, { status: 409 }),
      ),
    )
    montar()
    const user = userEvent.setup()

    const form = await abrirFormulario(user)
    await user.type(within(form).getByLabelText("Teléfono"), "600111222")
    await user.type(within(form).getByLabelText("Nombre"), "Marta Nueva")
    await user.click(within(form).getByRole("button", { name: "Añadir" }))

    expect(await screen.findByText(TEXTO_TELEFONO_409)).toBeInTheDocument()
    expect(
      screen.queryByText("No se puede usar ese teléfono para un contacto."),
    ).not.toBeInTheDocument()
  })

  it("POST /contactos 400 → muestra el motivo del backend", async () => {
    server.use(
      http.get(apiUrl("/contactos"), () => HttpResponse.json(pagina([]))),
      http.post(apiUrl("/contactos"), () =>
        HttpResponse.json({ motivo: "El teléfono no es válido." }, { status: 400 }),
      ),
    )
    montar()
    const user = userEvent.setup()

    const form = await abrirFormulario(user)
    await user.type(within(form).getByLabelText("Teléfono"), "123")
    await user.type(within(form).getByLabelText("Nombre"), "Marta Nueva")
    await user.click(within(form).getByRole("button", { name: "Añadir" }))

    expect(await screen.findByText("El teléfono no es válido.")).toBeInTheDocument()
  })
})

describe("PersonasDeExplotacion: añadir a alguien que ya es contacto", () => {
  it("teléfono ya en el listado → solo lo vincula (no crea)", async () => {
    let creaciones = 0
    let vinculoId = ""
    server.use(
      http.get(apiUrl("/contactos"), () =>
        HttpResponse.json(pagina([contactoListado(42, "+34600111222", "Ya Existe")])),
      ),
      http.post(apiUrl("/contactos"), () => {
        creaciones += 1
        return new HttpResponse(null, { status: 201 })
      }),
      http.post(apiUrl("/contactos/:id/explotaciones"), ({ params }) => {
        vinculoId = String(params.id)
        return new HttpResponse(null, { status: 200 })
      }),
    )
    const { onCambio } = montar()
    const user = userEvent.setup()

    const form = await abrirFormulario(user)
    // Se teclea el mismo número en forma local: debe reconocerse como el E.164 del listado.
    await user.type(within(form).getByLabelText("Teléfono"), "600 111 222")
    await user.click(within(form).getByRole("button", { name: "Añadir" }))

    await waitFor(() => expect(onCambio).toHaveBeenCalledTimes(1))
    expect(vinculoId).toBe("42")
    expect(creaciones).toBe(0)
  })
})

describe("PersonasDeExplotacion: fallo a medias al añadir", () => {
  it("contacto creado pero el vínculo falla → aviso + «Reintentar vínculo» que repite solo el vínculo", async () => {
    let creaciones = 0
    let vinculos = 0
    server.use(
      http.get(apiUrl("/contactos"), () => HttpResponse.json(pagina([]))),
      http.post(apiUrl("/contactos"), () => {
        creaciones += 1
        return HttpResponse.json(contactoListado(99, "+34600111222", "Marta Nueva"), { status: 201 })
      }),
      http.post(apiUrl("/contactos/:id/explotaciones"), () => {
        vinculos += 1
        return vinculos === 1
          ? new HttpResponse(null, { status: 500 })
          : new HttpResponse(null, { status: 200 })
      }),
    )
    const { onCambio } = montar()
    const user = userEvent.setup()

    const form = await abrirFormulario(user)
    await user.type(within(form).getByLabelText("Teléfono"), "600111222")
    await user.type(within(form).getByLabelText("Nombre"), "Marta Nueva")
    await user.click(within(form).getByRole("button", { name: "Añadir" }))

    expect(await screen.findByText(TEXTO_PARCIAL)).toBeInTheDocument()
    expect(onCambio).not.toHaveBeenCalled()

    await user.click(screen.getByRole("button", { name: "Reintentar vínculo" }))

    await waitFor(() => expect(onCambio).toHaveBeenCalledTimes(1))
    // El contacto NO se vuelve a crear (sería un 409): solo se repite el vínculo.
    expect(creaciones).toBe(1)
    expect(vinculos).toBe(2)
  })
})
