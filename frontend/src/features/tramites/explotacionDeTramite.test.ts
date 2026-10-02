import { describe, expect, it } from "vitest"
import { ErrorApi } from "@/shared/api/errores"
import type { Explotacion } from "@/features/explotaciones/types"
import type { CargaTodasLasExplotaciones } from "@/features/explotaciones/useTodasLasExplotaciones"
import { presentarExplotacion } from "./explotacionDeTramite"

const EXPLOTACION: Explotacion = {
  id: 3,
  codigoRega: "ES280790000123",
  nombre: "Finca La Dehesa",
  ganaderoId: 1,
  nombreGanadero: "Ana",
}
const LISTO: CargaTodasLasExplotaciones = {
  estado: "listo",
  explotaciones: [EXPLOTACION],
  porId: new Map([[EXPLOTACION.id, EXPLOTACION]]),
}
const CARGANDO: CargaTodasLasExplotaciones = { estado: "cargando" }
const ERROR: CargaTodasLasExplotaciones = { estado: "error", error: new ErrorApi({ tipo: "red" }) }

describe("presentarExplotacion (H7 / decisión 22: código REGA, nunca el id)", () => {
  it("con la lista completa, traduce el id a su código REGA y nombre", () => {
    expect(presentarExplotacion(3, LISTO)).toEqual({
      tipo: "encontrada",
      codigoRega: "ES280790000123",
      nombre: "Finca La Dehesa",
    })
  })

  it("un id que no está en la lista completa es 'no-encontrada' (nunca el id crudo)", () => {
    const resultado = presentarExplotacion(99, LISTO)
    expect(resultado).toEqual({ tipo: "no-encontrada" })
    expect(JSON.stringify(resultado)).not.toContain("99")
  })

  it("null es 'sin-asignar', se esté cargando, haya fallado o esté lista", () => {
    for (const carga of [LISTO, CARGANDO, ERROR]) {
      expect(presentarExplotacion(null, carga)).toEqual({ tipo: "sin-asignar" })
    }
  })

  it("mientras carga la lista, 'cargando'; si falló, 'no-disponible'", () => {
    expect(presentarExplotacion(3, CARGANDO)).toEqual({ tipo: "cargando" })
    expect(presentarExplotacion(3, ERROR)).toEqual({ tipo: "no-disponible" })
  })
})
