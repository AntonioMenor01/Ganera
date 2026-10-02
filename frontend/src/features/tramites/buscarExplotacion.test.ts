import { describe, expect, it } from "vitest"
import type { Explotacion } from "@/features/explotaciones/types"
import { coincideExplotacion, etiquetaExplotacion } from "./buscarExplotacion"

const DEHESA: Explotacion = {
  id: 3,
  codigoRega: "ES280790000123",
  nombre: "Finca La Dehesa",
  ganaderoId: 1,
  nombreGanadero: "Ana Martínez",
}

describe("coincideExplotacion (filtro de lo que se muestra, no una regla de negocio)", () => {
  it("una consulta vacía o en blanco muestra todas", () => {
    expect(coincideExplotacion(DEHESA, "")).toBe(true)
    expect(coincideExplotacion(DEHESA, "   ")).toBe(true)
  })

  it("busca por código REGA, también por un trozo del medio", () => {
    expect(coincideExplotacion(DEHESA, "ES2807")).toBe(true)
    expect(coincideExplotacion(DEHESA, "0123")).toBe(true)
    expect(coincideExplotacion(DEHESA, "es2807")).toBe(true)
    expect(coincideExplotacion(DEHESA, "9999")).toBe(false)
  })

  it("busca por nombre y por ganadero, sin distinguir mayúsculas ni tildes", () => {
    expect(coincideExplotacion(DEHESA, "dehesa")).toBe(true)
    expect(coincideExplotacion(DEHESA, "martinez")).toBe(true)
    expect(coincideExplotacion(DEHESA, "MARTÍNEZ")).toBe(true)
    expect(coincideExplotacion(DEHESA, "encinar")).toBe(false)
  })

  it("con varias palabras, todas deben aparecer (en cualquier campo y orden)", () => {
    expect(coincideExplotacion(DEHESA, "ana dehesa")).toBe(true)
    expect(coincideExplotacion(DEHESA, "dehesa 0123")).toBe(true)
    expect(coincideExplotacion(DEHESA, "ana encinar")).toBe(false)
  })

  it("la etiqueta que se ve en el campo (REGA · nombre) también coincide consigo misma", () => {
    expect(coincideExplotacion(DEHESA, etiquetaExplotacion(DEHESA))).toBe(true)
  })

  it("nunca rompe con datos incompletos", () => {
    const rara = { ...DEHESA, nombre: null, nombreGanadero: undefined } as unknown as Explotacion
    expect(coincideExplotacion(rara, "ES28")).toBe(true)
    expect(coincideExplotacion(rara, "dehesa")).toBe(false)
  })
})

describe("etiquetaExplotacion", () => {
  it("es «REGA · nombre»", () => {
    expect(etiquetaExplotacion(DEHESA)).toBe("ES280790000123 · Finca La Dehesa")
  })

  it("sin nombre, solo el código", () => {
    expect(etiquetaExplotacion({ ...DEHESA, nombre: "" })).toBe("ES280790000123")
  })
})
