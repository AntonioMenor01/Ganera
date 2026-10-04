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
    expect(within(resumen).getByText("3 filas · 2 creadas · 1 actualizada")).toBeInTheDocument()
  })

  it("sin hoja Contactos (0/0/0) la línea sigue ahí con ceros, igual que las otras hojas", async () => {
    const resumen = await importar({
      explotaciones: hoja(1, 1, 0),
      animales: hoja(0, 0, 0),
      contactos: hoja(0, 0, 0),
      errores: [],
    })

    expect(within(resumen).getByText("Contactos")).toBeInTheDocument()
    expect(within(resumen).getAllByText("0 filas · 0 creadas · 0 actualizadas")).toHaveLength(2)
  })

  it("la tarjeta usa singular con 1 y plural con 0 y con 2 (fila, creada, actualizada)", async () => {
    const resumen = await importar({
      explotaciones: hoja(1, 1, 1),
      animales: hoja(2, 2, 2),
      contactos: hoja(0, 0, 0),
      errores: [],
    })

    expect(within(resumen).getByText("1 fila · 1 creada · 1 actualizada")).toBeInTheDocument()
    expect(within(resumen).getByText("2 filas · 2 creadas · 2 actualizadas")).toBeInTheDocument()
    expect(within(resumen).getByText("0 filas · 0 creadas · 0 actualizadas")).toBeInTheDocument()
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
      "Importación terminada. Explotaciones: 2 creadas, 5 actualizadas. Animales: 40 creadas, 0 actualizadas. Contactos: 1 creada, 0 actualizadas. 2 filas con error.",
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
      "Importación terminada. Explotaciones: 1 creada, 0 actualizadas. Animales: 0 creadas, 1 actualizada. Contactos: 0 creadas, 0 actualizadas. 1 fila con error.",
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
      "Importación terminada. Explotaciones: 1 creada, 0 actualizadas. Animales: 0 creadas, 0 actualizadas. Contactos: 0 creadas, 0 actualizadas.",
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

    expect(await screen.findByRole("alert")).toBeInTheDocument()
    expect(screen.getByRole("status")).toBeEmptyDOMElement()
  })
})
