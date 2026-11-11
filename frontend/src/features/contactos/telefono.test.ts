import { describe, expect, it } from "vitest"
import { mismoTelefono, telefonoCanonico } from "./telefono"

describe("telefonoCanonico", () => {
  it("un E.164 español se queda en sus 11 dígitos", () => {
    expect(telefonoCanonico("+34612345678")).toBe("34612345678")
  })

  it("quita espacios, puntos, guiones y el +", () => {
    expect(telefonoCanonico("+34 612-345.678")).toBe("34612345678")
  })

  it("un móvil español de 9 cifras recibe el prefijo 34", () => {
    expect(telefonoCanonico("612345678")).toBe("34612345678")
    expect(telefonoCanonico("612 345 678")).toBe("34612345678")
  })

  it("el prefijo internacional 0034 se reduce a 34", () => {
    expect(telefonoCanonico("0034612345678")).toBe("34612345678")
    expect(telefonoCanonico("0034 612 345 678")).toBe("34612345678")
  })

  it("un número de otro país se deja en dígitos tal cual", () => {
    expect(telefonoCanonico("+447911123456")).toBe("447911123456")
  })

  it("cadena sin dígitos → vacío", () => {
    expect(telefonoCanonico("   ")).toBe("")
    expect(telefonoCanonico("abc")).toBe("")
  })
})

describe("mismoTelefono", () => {
  it("reconoce el mismo número escrito de varias formas", () => {
    expect(mismoTelefono("612345678", "+34612345678")).toBe(true)
    expect(mismoTelefono("+34 612 345 678", "0034612345678")).toBe(true)
  })

  it("distingue números distintos", () => {
    expect(mismoTelefono("612345678", "612345679")).toBe(false)
  })

  it("dos vacíos no son el mismo número (no coinciden)", () => {
    expect(mismoTelefono("", "")).toBe(false)
  })
})
