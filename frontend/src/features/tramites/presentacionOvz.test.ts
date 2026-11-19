import { describe, expect, it } from "vitest"
import {
  TEXTO_APROBAR,
  TEXTO_APROBAR_Y_PRESENTAR,
  esErrorIncierto,
  estadoPresentacionOvz,
  fechaDdMmAaaa,
  textoBotonAprobar,
  vistaPreviaPresentacion,
} from "./presentacionOvz"
import type { PresentacionOvz, TramiteCrotal, TramiteDetalle } from "./types"

function crotal(crotal: string, parcial: Partial<TramiteCrotal> = {}): TramiteCrotal {
  return { crotalIndicado: crotal, crotal, animalId: null, enInventario: false, resolucion: "NO_ENCONTRADO", ...parcial }
}

function detalle(parcial: Partial<TramiteDetalle> = {}): TramiteDetalle {
  return {
    id: 1,
    tipoTramite: "BAJA_MUERTE",
    estado: "PENDIENTE_REVISION",
    motivoError: null,
    explotacionId: 3,
    explotacionCodigoRega: "ES123",
    explotacionNombre: "La Dehesa",
    ganaderoNombre: "Ana",
    ganaderoNif: "12345678Z",
    mensajeOriginal: null,
    crotales: [],
    version: 0,
    origen: null,
    estadoExtraccion: null,
    crotalesDescartados: 0,
    hechoEnOvzEn: null,
    hechoEnOvzPorNombre: null,
    whatsapp: null,
    motivoRechazo: null,
    descartable: false,
    historialDescarte: [],
    fechaHecho: null,
    fechaNacimiento: null,
    sexo: null,
    crotalMadreIndicado: null,
    crotalMadre: null,
    crotalMadreResolucion: null,
    numeroMer: null,
    destinoRega: null,
    destinoTexto: null,
    consignatario: null,
    aptitudMovimiento: null,
    fechaIdentificacion: null,
    sePresentaraAutomaticamente: false,
    presentacionOvz: null,
    ...parcial,
  }
}

function presentacion(parcial: Partial<PresentacionOvz> = {}): PresentacionOvz {
  return { estadoTrabajo: "PENDIENTE", claseError: null, actualizadoEn: null, ...parcial }
}

describe("textoBotonAprobar", () => {
  it("dice «Aprobar» cuando no se va a presentar en automático", () => {
    expect(textoBotonAprobar(detalle({ sePresentaraAutomaticamente: false }))).toBe(TEXTO_APROBAR)
  })
  it("dice «Aprobar y presentar en OVZ» cuando sí", () => {
    expect(textoBotonAprobar(detalle({ sePresentaraAutomaticamente: true }))).toBe(TEXTO_APROBAR_Y_PRESENTAR)
  })
})

describe("fechaDdMmAaaa", () => {
  it("convierte una fecha ISO yyyy-MM-dd", () => {
    expect(fechaDdMmAaaa("2026-10-07")).toBe("07/10/2026")
  })
  it("deja cualquier otra cosa tal cual y null/undefined en null", () => {
    expect(fechaDdMmAaaa("ayer")).toBe("ayer")
    expect(fechaDdMmAaaa(null)).toBeNull()
    expect(fechaDdMmAaaa(undefined)).toBeNull()
  })
})

describe("vistaPreviaPresentacion", () => {
  it("toma el crotal completo, la explotación, la fecha de muerte y el MER", () => {
    const previa = vistaPreviaPresentacion(
      detalle({
        crotales: [crotal("1234", { crotal: "ES010000001234", resolucion: "EN_INVENTARIO", enInventario: true })],
        fechaHecho: "2026-10-07",
        numeroMer: "MER-9",
      }),
    )
    expect(previa).toEqual({
      crotal: "ES010000001234",
      explotacion: "ES123 · La Dehesa",
      fechaMuerte: "07/10/2026",
      numeroMer: "MER-9",
    })
  })
  it("sin datos deja cada campo en null (nunca lanza)", () => {
    expect(vistaPreviaPresentacion(detalle({ crotales: [], explotacionCodigoRega: null, explotacionNombre: null }))).toEqual({
      crotal: null,
      explotacion: null,
      fechaMuerte: null,
      numeroMer: null,
    })
  })
})

describe("estadoPresentacionOvz", () => {
  it("APROBADO con trabajo PENDIENTE → en cola", () => {
    expect(estadoPresentacionOvz(detalle({ estado: "APROBADO", presentacionOvz: presentacion({ estadoTrabajo: "PENDIENTE" }) })))
      .toEqual({ clase: "en-cola" })
  })
  it("APROBADO con trabajo ENSAYO_OK → ensayo", () => {
    expect(estadoPresentacionOvz(detalle({ estado: "APROBADO", presentacionOvz: presentacion({ estadoTrabajo: "ENSAYO_OK" }) })))
      .toEqual({ clase: "ensayo" })
  })
  it("APROBADO sin trabajo (vía manual) → null", () => {
    expect(estadoPresentacionOvz(detalle({ estado: "APROBADO", presentacionOvz: null }))).toBeNull()
  })
  it("EN_PROCESO → presentando", () => {
    expect(estadoPresentacionOvz(detalle({ estado: "EN_PROCESO" }))).toEqual({ clase: "presentando" })
  })
  it("EJECUTADO_OVZ → presentada, con la fecha en hora de Madrid", () => {
    const e = estadoPresentacionOvz(
      detalle({ estado: "EJECUTADO_OVZ", presentacionOvz: presentacion({ estadoTrabajo: "EJECUTADO", actualizadoEn: "2026-10-07T10:30:00Z" }) }),
    )
    expect(e).toEqual({ clase: "presentada", fecha: "07/10/2026, 12:30" })
  })
  it("EJECUTADO_OVZ sin fecha → presentada con fecha null", () => {
    expect(estadoPresentacionOvz(detalle({ estado: "EJECUTADO_OVZ", presentacionOvz: null }))).toEqual({
      clase: "presentada",
      fecha: null,
    })
  })
  it("ERROR_OVZ seguro e incierto por claseError", () => {
    expect(estadoPresentacionOvz(detalle({ estado: "ERROR_OVZ", presentacionOvz: presentacion({ estadoTrabajo: "ERROR", claseError: "SEGURO" }) })))
      .toEqual({ clase: "error", incierto: false })
    expect(estadoPresentacionOvz(detalle({ estado: "ERROR_OVZ", presentacionOvz: presentacion({ estadoTrabajo: "ERROR", claseError: "INCIERTO" }) })))
      .toEqual({ clase: "error", incierto: true })
    // Sin presentacionOvz, un ERROR_OVZ se trata como seguro (no incierto).
    expect(estadoPresentacionOvz(detalle({ estado: "ERROR_OVZ", presentacionOvz: null }))).toEqual({ clase: "error", incierto: false })
  })
  it("un estado sin presentación (PENDIENTE_REVISION, HECHO_EN_OVZ…) → null", () => {
    expect(estadoPresentacionOvz(detalle({ estado: "PENDIENTE_REVISION" }))).toBeNull()
    expect(estadoPresentacionOvz(detalle({ estado: "HECHO_EN_OVZ" }))).toBeNull()
  })
})

describe("esErrorIncierto", () => {
  it("true solo para un ERROR_OVZ con claseError INCIERTO", () => {
    expect(esErrorIncierto(detalle({ estado: "ERROR_OVZ", presentacionOvz: presentacion({ estadoTrabajo: "ERROR", claseError: "INCIERTO" }) }))).toBe(true)
    expect(esErrorIncierto(detalle({ estado: "ERROR_OVZ", presentacionOvz: presentacion({ estadoTrabajo: "ERROR", claseError: "SEGURO" }) }))).toBe(false)
    expect(esErrorIncierto(detalle({ estado: "EN_PROCESO" }))).toBe(false)
  })
})
