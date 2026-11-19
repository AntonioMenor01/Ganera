import { describe, expect, it } from "vitest"
import { etiquetaTipoDocumento, formatearTamano } from "./documentosOvz"

describe("etiquetaTipoDocumento", () => {
  it("traduce BAJA_ANIMAL", () => {
    expect(etiquetaTipoDocumento("BAJA_ANIMAL")).toBe("Baja de animal")
  })
  it("un tipo desconocido se muestra tal cual, y uno vacío como «Documento»", () => {
    expect(etiquetaTipoDocumento("OTRO")).toBe("OTRO")
    expect(etiquetaTipoDocumento("")).toBe("Documento")
  })
})

describe("formatearTamano", () => {
  it("B, KB y MB", () => {
    expect(formatearTamano(0)).toBe("0 B")
    expect(formatearTamano(500)).toBe("500 B")
    expect(formatearTamano(1536)).toBe("1.5 KB")
    expect(formatearTamano(46080)).toBe("45 KB")
    expect(formatearTamano(1572864)).toBe("1.5 MB")
  })
  it("un valor no finito o negativo da «—»", () => {
    expect(formatearTamano(-1)).toBe("—")
    expect(formatearTamano(NaN)).toBe("—")
    expect(formatearTamano(Infinity)).toBe("—")
  })
})
