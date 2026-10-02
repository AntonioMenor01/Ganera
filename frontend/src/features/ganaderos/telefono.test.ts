import { describe, expect, it } from "vitest"
import { formatearTelefono, hrefTelefono } from "./telefono"

describe("formatearTelefono", () => {
  it.each([
    ["+34612345678", "+34 612 345 678"],
    ["+34912345678", "+34 912 345 678"],
  ])("agrupa un número español %s → %s", (e164, visible) => {
    expect(formatearTelefono(e164)).toBe(visible)
  })

  it.each(["+447911123456", "+351912345678", "", "612345678", "+3461234567"])(
    "cualquier otra forma sale tal cual: %s",
    (valor) => {
      expect(formatearTelefono(valor)).toBe(valor)
    },
  )
})

describe("hrefTelefono", () => {
  it("usa el valor E.164 exacto, sin espacios", () => {
    expect(hrefTelefono("+34612345678")).toBe("tel:+34612345678")
  })

  it("quita espacios si el valor los trajera", () => {
    expect(hrefTelefono(" +34 612 345 678 ")).toBe("tel:+34612345678")
  })
})
