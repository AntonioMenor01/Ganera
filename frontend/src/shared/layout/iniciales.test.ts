import { describe, expect, it } from "vitest"
import { iniciales } from "./iniciales"

describe("iniciales", () => {
  it.each([
    ["Ana María Pérez", "AP"],
    ["lucía", "L"],
    ["  José   Luis  ", "JL"],
    ["Álvaro Núñez", "ÁN"],
    ["ñandú", "Ñ"],
  ])("«%s» → %s (primera letra de la primera y de la última palabra)", (nombre, esperado) => {
    expect(iniciales(nombre, "ana@gestoria.es")).toBe(esperado)
  })

  it("conserva la tilde aunque el nombre llegue descompuesto (NFD)", () => {
    expect(iniciales("Álvaro Núñez", "x@y.es")).toBe("ÁN")
  })

  it("ignora las palabras sin letras (signos sueltos, números)", () => {
    expect(iniciales("Ana - Pérez", "x@y.es")).toBe("AP")
    expect(iniciales("Ana Pérez -", "x@y.es")).toBe("AP")
    expect(iniciales("- Ana", "x@y.es")).toBe("A")
    expect(iniciales("(Luis) 2", "x@y.es")).toBe("L")
  })

  it("con el nombre vacío, solo espacios o solo signos, usa la primera letra del email", () => {
    expect(iniciales("", "lucia@gestoria.es")).toBe("L")
    expect(iniciales("   ", "lucia@gestoria.es")).toBe("L")
    expect(iniciales(" - ", "ñu@gestoria.es")).toBe("Ñ")
  })

  it("sin nombre ni email, «?»", () => {
    expect(iniciales("", "")).toBe("?")
    expect(iniciales("  ", "  ")).toBe("?")
  })
})
