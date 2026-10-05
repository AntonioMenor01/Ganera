import { describe, expect, it } from "vitest"
import type { TramiteCrotal, TramiteDetalle } from "./types"
import { ESTADOS_TRAMITE, type EstadoTramite } from "./etiquetas"
import {
  AVISO_CAMBIOS_SIN_GUARDAR,
  construirPatch,
  crotalesParaEnviar,
  esEditable,
  estaSucio,
  formularioDesdeDetalle,
} from "./revisionTramite"

function crotal(crotalIndicado: string): TramiteCrotal {
  return {
    crotalIndicado,
    crotal: crotalIndicado,
    animalId: null,
    enInventario: false,
    resolucion: "NO_ENCONTRADO",
  }
}

function detalle(parcial: Partial<TramiteDetalle> = {}): TramiteDetalle {
  return {
    id: 7,
    tipoTramite: "ALTA",
    estado: "PENDIENTE_REVISION",
    motivoError: null,
    explotacionId: 3,
    explotacionCodigoRega: "ES123",
    explotacionNombre: "La Dehesa",
    mensajeOriginal: null,
    crotales: [crotal("1234"), crotal("ES010000005678")],
    version: 4,
    origen: null,
    estadoExtraccion: null,
    crotalesDescartados: 0,
    ...parcial,
  }
}

describe("AVISO_CAMBIOS_SIN_GUARDAR", () => {
  it("es el texto exacto de la decisión 24", () => {
    expect(AVISO_CAMBIOS_SIN_GUARDAR).toBe("Guarda antes de aprobar")
  })
})

describe("esEditable", () => {
  it("solo PENDIENTE_REVISION es editable", () => {
    for (const estado of Object.keys(ESTADOS_TRAMITE) as EstadoTramite[]) {
      expect(esEditable(estado)).toBe(estado === "PENDIENTE_REVISION")
    }
  })

  it("un estado desconocido no es editable", () => {
    expect(esEditable("OTRO" as EstadoTramite)).toBe(false)
  })
})

describe("formularioDesdeDetalle", () => {
  it("toma explotación, tipo y lo INDICADO de cada crotal (no el completo resuelto)", () => {
    const d = detalle({
      crotales: [{ ...crotal("1234"), crotal: "ES010000001234", resolucion: "EN_INVENTARIO" }],
    })
    expect(formularioDesdeDetalle(d)).toEqual({
      explotacionId: 3,
      tipoTramite: "ALTA",
      crotales: ["1234"],
    })
  })

  it("con explotación y tipo null, el formulario los tiene null", () => {
    const f = formularioDesdeDetalle(detalle({ explotacionId: null, tipoTramite: null, crotales: [] }))
    expect(f).toEqual({ explotacionId: null, tipoTramite: null, crotales: [] })
  })
})

describe("crotalesParaEnviar", () => {
  it("recorta espacios y quita los vacíos, respetando el orden", () => {
    expect(crotalesParaEnviar(["  1234 ", "", "   ", "\t5678\n", "abcd"])).toEqual([
      "1234",
      "5678",
      "abcd",
    ])
  })

  it("no normaliza, no clasifica, no valida ni quita duplicados (lo hace el backend)", () => {
    expect(crotalesParaEnviar(["es-0100 0000.1234", "12", "1234", "1234", "¿?"])).toEqual([
      "es-0100 0000.1234",
      "12",
      "1234",
      "1234",
      "¿?",
    ])
  })
})

describe("estaSucio", () => {
  it("recién cargado no está sucio", () => {
    const d = detalle()
    expect(estaSucio(d, formularioDesdeDetalle(d))).toBe(false)
  })

  it("espacios alrededor y entradas vacías no cuentan como cambio", () => {
    const d = detalle()
    expect(estaSucio(d, { ...formularioDesdeDetalle(d), crotales: [" 1234 ", "", "ES010000005678", "  "] })).toBe(
      false,
    )
  })

  it("cambiar el orden de los crotales sí es un cambio", () => {
    const d = detalle()
    expect(estaSucio(d, { ...formularioDesdeDetalle(d), crotales: ["ES010000005678", "1234"] })).toBe(true)
  })

  it("cambiar explotación, tipo o un crotal es un cambio", () => {
    const d = detalle()
    const base = formularioDesdeDetalle(d)
    expect(estaSucio(d, { ...base, explotacionId: 9 })).toBe(true)
    expect(estaSucio(d, { ...base, tipoTramite: "BAJA" })).toBe(true)
    expect(estaSucio(d, { ...base, crotales: ["1235", "ES010000005678"] })).toBe(true)
    expect(estaSucio(d, { ...base, crotales: ["1234"] })).toBe(true)
  })
})

describe("construirPatch", () => {
  it("sin cambios devuelve null (no hay PATCH)", () => {
    const d = detalle()
    expect(construirPatch(d, formularioDesdeDetalle(d))).toBeNull()
    expect(construirPatch(d, { ...formularioDesdeDetalle(d), crotales: [" 1234", "ES010000005678 ", ""] })).toBeNull()
  })

  it("solo la versión y el campo cambiado: explotación", () => {
    const d = detalle()
    const patch = construirPatch(d, { ...formularioDesdeDetalle(d), explotacionId: 9 })
    expect(patch).toEqual({ version: 4, explotacionId: 9 })
    expect(Object.keys(patch!)).toEqual(["version", "explotacionId"])
  })

  it("solo la versión y el campo cambiado: tipo", () => {
    const d = detalle()
    expect(construirPatch(d, { ...formularioDesdeDetalle(d), tipoTramite: "CENSO" })).toEqual({
      version: 4,
      tipoTramite: "CENSO",
    })
  })

  it("si cambia un crotal, se envía la lista COMPLETA, recortada y sin vacíos", () => {
    const d = detalle()
    expect(
      construirPatch(d, { ...formularioDesdeDetalle(d), crotales: [" 1234 ", "", "9999", "ES010000005678"] }),
    ).toEqual({ version: 4, crotales: ["1234", "9999", "ES010000005678"] })
  })

  it("quitar todos los crotales envía una lista vacía", () => {
    const d = detalle()
    expect(construirPatch(d, { ...formularioDesdeDetalle(d), crotales: [] })).toEqual({
      version: 4,
      crotales: [],
    })
    expect(construirPatch(d, { ...formularioDesdeDetalle(d), crotales: ["", "  "] })).toEqual({
      version: 4,
      crotales: [],
    })
  })

  it("varios cambios a la vez van juntos, con la versión del detalle", () => {
    const d = detalle({ version: 0, explotacionId: null, tipoTramite: null, crotales: [] })
    expect(construirPatch(d, { explotacionId: 5, tipoTramite: "ALTA", crotales: ["1234"] })).toEqual({
      version: 0,
      explotacionId: 5,
      tipoTramite: "ALTA",
      crotales: ["1234"],
    })
  })

  it("nunca envía null para explotación o tipo (PATCH no puede vaciarlos, H5)", () => {
    const d = detalle()
    const patch = construirPatch(d, { explotacionId: null, tipoTramite: null, crotales: ["1234", "ES010000005678"] })
    expect(patch).toBeNull()
  })
})
