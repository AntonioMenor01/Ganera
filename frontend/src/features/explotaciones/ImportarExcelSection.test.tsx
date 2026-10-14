import { render, screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http, HttpResponse } from "msw"
import { describe, expect, it, vi } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { ImportarExcelSection } from "./ImportarExcelSection"
import type { ImportResumen } from "./types"

function hoja(filasProcesadas: number, creadas: number, actualizadas: number) {
  return { filasProcesadas, creadas, actualizadas }
}

async function importar(resumen: ImportResumen) {
  server.use(http.post(apiUrl("/explotaciones/importar"), () => HttpResponse.json(resumen)))
  const user = userEvent.setup()
  const { container } = render(<ImportarExcelSection onImportado={vi.fn()} />)
  const input = container.querySelector<HTMLInputElement>('input[type="file"]')!
  await user.upload(input, new File(["x"], "inventario.xlsx"))
  return screen.findByTestId("resumen-importacion")
}

describe("ImportarExcelSection: resumen (H10)", () => {
  it("muestra la línea de Contactos junto a Explotaciones y Animales, con el mismo formato", async () => {
    const resumen = await importar({
      explotaciones: hoja(2, 1, 1),
      animales: hoja(5, 5, 0),
      contactos: hoja(3, 2, 1),
      errores: [],
    })

    expect(within(resumen).getByText("Explotaciones")).toBeInTheDocument()
    expect(within(resumen).getByText("Animales")).toBeInTheDocument()
    expect(within(resumen).getByText("Contactos")).toBeInTheDocument()
    expect(within(resumen).getByText("3 filas · 2 creados · 1 actualizado")).toBeInTheDocument()
  })

  it("sin hoja Contactos (0/0/0) la línea sigue ahí con ceros, igual que las otras hojas", async () => {
    const resumen = await importar({
      explotaciones: hoja(1, 1, 0),
      animales: hoja(0, 0, 0),
      contactos: hoja(0, 0, 0),
      errores: [],
    })

    expect(within(resumen).getByText("Contactos")).toBeInTheDocument()
    expect(within(resumen).getAllByText("0 filas · 0 creados · 0 actualizados")).toHaveLength(2)
  })

  it("la tarjeta usa singular con 1 y plural con 0 y con 2 (fila, creada/creado, actualizada/actualizado)", async () => {
    const resumen = await importar({
      explotaciones: hoja(1, 1, 1),
      animales: hoja(2, 2, 2),
      contactos: hoja(0, 0, 0),
      errores: [],
    })

    expect(within(resumen).getByText("1 fila · 1 creada · 1 actualizada")).toBeInTheDocument()
    expect(within(resumen).getByText("2 filas · 2 creados · 2 actualizados")).toBeInTheDocument()
    expect(within(resumen).getByText("0 filas · 0 creados · 0 actualizados")).toBeInTheDocument()
  })
})

describe("ImportarExcelSection: género por hoja (Explotaciones en femenino; Animales y Contactos en masculino)", () => {
  function lineaVisible(resumen: HTMLElement, titulo: string) {
    return within(resumen).getByText(titulo).nextElementSibling
  }

  it.each([
    [0, "0 creadas", "0 actualizadas", "0 creados", "0 actualizados", "0 filas"],
    [1, "1 creada", "1 actualizada", "1 creado", "1 actualizado", "1 fila"],
    [2, "2 creadas", "2 actualizadas", "2 creados", "2 actualizados", "2 filas"],
  ])(
    "con %i creados y actualizados en cada hoja, la frase anunciada y la visible concuerdan en género",
    async (n, creadasF, actualizadasF, creadosM, actualizadosM, filas) => {
      const resumen = await importar({
        explotaciones: hoja(n, n, n),
        animales: hoja(n, n, n),
        contactos: hoja(n, n, n),
        errores: [],
      })

      expect(screen.getByRole("status")).toHaveTextContent(
        `Importación terminada. Explotaciones: ${creadasF}, ${actualizadasF}. Animales: ${creadosM}, ${actualizadosM}. Contactos: ${creadosM}, ${actualizadosM}.`,
      )
      expect(lineaVisible(resumen, "Explotaciones")).toHaveTextContent(
        new RegExp(`^${filas} · ${creadasF} · ${actualizadasF}$`),
      )
      expect(lineaVisible(resumen, "Animales")).toHaveTextContent(
        new RegExp(`^${filas} · ${creadosM} · ${actualizadosM}$`),
      )
      expect(lineaVisible(resumen, "Contactos")).toHaveTextContent(
        new RegExp(`^${filas} · ${creadosM} · ${actualizadosM}$`),
      )
    },
  )

  it("el recuadro del resumen es recto, con el borde del token", async () => {
    const resumen = await importar({
      explotaciones: hoja(1, 1, 0),
      animales: hoja(0, 0, 0),
      contactos: hoja(0, 0, 0),
      errores: [],
    })

    expect(resumen).toHaveClass("border", "border-border", "p-4")
    expect(resumen.className).not.toMatch(/rounded/)
  })
})

function errorFila(fila: number) {
  return { hoja: "Animales", fila, motivo: "Crotal no válido." }
}

describe("ImportarExcelSection: estado anunciado (Task 10, 4.1.3)", () => {
  it("el estado está montado desde el principio: vacío, «Importando el Excel…» y luego el resumen", async () => {
    let abrir!: () => void
    const puerta = new Promise<void>((resolve) => {
      abrir = resolve
    })
    server.use(
      http.post(apiUrl("/explotaciones/importar"), async () => {
        await puerta
        return HttpResponse.json({
          explotaciones: hoja(7, 2, 5),
          animales: hoja(40, 40, 0),
          contactos: hoja(1, 1, 0),
          errores: [errorFila(3), errorFila(9)],
        })
      }),
    )
    const user = userEvent.setup()
    const { container } = render(<ImportarExcelSection onImportado={vi.fn()} />)
    const estado = screen.getByRole("status")
    expect(estado).toBeEmptyDOMElement()
    expect(estado).toHaveClass("sr-only")

    const input = container.querySelector<HTMLInputElement>('input[type="file"]')!
    await user.upload(input, new File(["x"], "inventario.xlsx"))
    expect(screen.getByRole("status")).toBe(estado)
    expect(estado).toHaveTextContent("Importando el Excel…")

    abrir()
    await screen.findByTestId("resumen-importacion")
    expect(screen.getByRole("status")).toBe(estado)
    expect(estado).toHaveTextContent(
      "Importación terminada. Explotaciones: 2 creadas, 5 actualizadas. Animales: 40 creados, 0 actualizados. Contactos: 1 creado, 0 actualizados. 2 filas con error.",
    )
  })

  it("con una sola fila con error, en singular, también en la línea visible", async () => {
    const resumen = await importar({
      explotaciones: hoja(1, 1, 0),
      animales: hoja(1, 0, 1),
      contactos: hoja(0, 0, 0),
      errores: [errorFila(4)],
    })

    expect(screen.getByRole("status")).toHaveTextContent(
      "Importación terminada. Explotaciones: 1 creada, 0 actualizadas. Animales: 0 creados, 1 actualizado. Contactos: 0 creados, 0 actualizados. 1 fila con error.",
    )
    expect(within(resumen).getByText("1 fila con error:")).toBeInTheDocument()
  })

  it("con varias filas con error, la línea visible va en plural", async () => {
    const resumen = await importar({
      explotaciones: hoja(1, 1, 0),
      animales: hoja(0, 0, 0),
      contactos: hoja(0, 0, 0),
      errores: [errorFila(4), errorFila(5)],
    })

    expect(within(resumen).getByText("2 filas con error:")).toBeInTheDocument()
  })

  it("sin errores, el resumen anunciado no menciona errores", async () => {
    await importar({
      explotaciones: hoja(1, 1, 0),
      animales: hoja(0, 0, 0),
      contactos: hoja(0, 0, 0),
      errores: [],
    })

    const estado = screen.getByRole("status")
    expect(estado).toHaveTextContent(
      "Importación terminada. Explotaciones: 1 creada, 0 actualizadas. Animales: 0 creados, 0 actualizados. Contactos: 0 creados, 0 actualizados.",
    )
    expect(estado.textContent).not.toMatch(/error/)
  })

  it("si falla, el estado queda vacío: el error lo dice su alerta", async () => {
    server.use(
      http.post(apiUrl("/explotaciones/importar"), () => new HttpResponse(null, { status: 500 })),
    )
    const user = userEvent.setup()
    const { container } = render(<ImportarExcelSection onImportado={vi.fn()} />)
    const input = container.querySelector<HTMLInputElement>('input[type="file"]')!
    await user.upload(input, new File(["x"], "inventario.xlsx"))

    const alerta = await screen.findByRole("alert")
    expect(alerta.firstElementChild?.matches('svg[aria-hidden="true"]')).toBe(true)
    expect(screen.getByRole("status")).toBeEmptyDOMElement()
  })
})
