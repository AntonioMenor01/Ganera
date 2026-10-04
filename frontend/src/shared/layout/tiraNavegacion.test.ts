import { describe, expect, it } from "vitest"
import { scrollLeftParaMostrar } from "./tiraNavegacion"

// Tira de 375 px de ancho visible con 16 px de margen interior: la zona "cómoda" es
// [scrollLeft + 16, scrollLeft + 375 - 16].
const base = { anchoVisible: 375, margen: 16 }

describe("scrollLeftParaMostrar", () => {
  it("activo ya visible del todo: no mueve la tira", () => {
    expect(scrollLeftParaMostrar({ ...base, scrollLeft: 0, inicio: 16, fin: 90 })).toBe(0)
    expect(scrollLeftParaMostrar({ ...base, scrollLeft: 40, inicio: 100, fin: 200 })).toBe(40)
  })

  it("activo cortado por la derecha: desplaza lo justo para verlo entero con el margen", () => {
    // fin 420 → scrollLeft = 420 - 375 + 16 = 61
    expect(scrollLeftParaMostrar({ ...base, scrollLeft: 0, inicio: 330, fin: 420 })).toBe(61)
  })

  it("activo fuera por la izquierda: lo deja a 16 px del borde", () => {
    expect(scrollLeftParaMostrar({ ...base, scrollLeft: 120, inicio: 16, fin: 90 })).toBe(0)
    expect(scrollLeftParaMostrar({ ...base, scrollLeft: 200, inicio: 100, fin: 180 })).toBe(84)
  })

  it("respeta el margen: un activo pegado al borde (dentro del margen) también se ajusta", () => {
    // Visible pero a 6 px del borde derecho: se corre hasta dejar los 16 px.
    expect(scrollLeftParaMostrar({ ...base, scrollLeft: 0, inicio: 300, fin: 369 })).toBe(10)
  })

  it("nunca devuelve un scrollLeft negativo", () => {
    expect(scrollLeftParaMostrar({ ...base, scrollLeft: 50, inicio: 4, fin: 60 })).toBe(0)
  })

  it("un activo más ancho que la zona visible se alinea por su inicio", () => {
    expect(scrollLeftParaMostrar({ anchoVisible: 100, margen: 16, scrollLeft: 0, inicio: 200, fin: 400 })).toBe(184)
  })
})
