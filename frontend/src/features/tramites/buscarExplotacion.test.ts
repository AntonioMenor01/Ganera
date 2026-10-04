import { describe, expect, it } from "vitest"
import type { Explotacion } from "@/features/explotaciones/types"
import { consultaDeBusqueda, etiquetaExplotacion } from "./buscarExplotacion"

const DEHESA: Explotacion = {
  id: 3,
  codigoRega: "ES280790000123",
  nombre: "Finca La Dehesa",
  ganaderoId: 1,
  nombreGanadero: "Ana Martínez",
}

describe("etiquetaExplotacion", () => {
  it("es «REGA · nombre»", () => {
    expect(etiquetaExplotacion(DEHESA)).toBe("ES280790000123 · Finca La Dehesa")
  })

  it("sin nombre, solo el código", () => {
    expect(etiquetaExplotacion({ ...DEHESA, nombre: "" })).toBe("ES280790000123")
    expect(etiquetaExplotacion({ codigoRega: "ES1", nombre: null })).toBe("ES1")
  })

  it("sin código REGA (no debería pasar), el número de la explotación", () => {
    expect(etiquetaExplotacion({ id: 3, codigoRega: null, nombre: null })).toBe("Explotación #3")
  })
})

describe("consultaDeBusqueda (D3b, D3d)", () => {
  it("recorta y por lo demás deja el texto tal cual: ni palabras sueltas ni tildes (D3b)", () => {
    expect(consultaDeBusqueda("  ES12 Pérez ", null)).toBe("ES12 Pérez")
    expect(consultaDeBusqueda("   ", null)).toBe("")
  })

  it("el texto es la etiqueta de la elegida → consulta vacía (D3d)", () => {
    expect(consultaDeBusqueda("ES280790000123 · Finca La Dehesa", DEHESA)).toBe("")
  })

  it("una etiqueta con espacios en los extremos (nombre guardado así) también cuenta como vacía", () => {
    expect(consultaDeBusqueda("ES280790000123 · Finca La Dehesa ", { ...DEHESA, nombre: "Finca La Dehesa " })).toBe("")
  })

  it("un texto distinto de la etiqueta de la elegida se busca", () => {
    expect(consultaDeBusqueda("ES280790000123 · Finca La Dehes", DEHESA)).toBe("ES280790000123 · Finca La Dehes")
    expect(consultaDeBusqueda("Dehesa", DEHESA)).toBe("Dehesa")
  })
})
