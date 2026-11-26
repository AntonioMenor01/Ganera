import { afterEach, describe, expect, it, vi } from "vitest"
import type { PreferenciaOvz } from "@/features/configuracion/preferenciasOvzApi"
import {
  abrirPrimerosPasos,
  CAMPOS_OVZ_MINIMOS,
  configurarAutoAperturaPrimerosPasos,
  debeAutoabrir,
  guardarProgreso,
  hayProgresoGuardado,
  leerProgreso,
  ovzConfigurado,
  PASOS_GUIA,
  suscribirAperturaPrimerosPasos,
  TOTAL_PASOS,
} from "./primerosPasos"

const PREFS_VACIAS: PreferenciaOvz = {
  oficinaDi: null,
  oficinaMer: null,
  emisionDi: null,
  tipoIdentificador: null,
  telefonoMovimiento: null,
  medioTransporte: null,
  matricula: null,
  transportistaSirentra: null,
  tipoResponsable: null,
}

afterEach(() => {
  configurarAutoAperturaPrimerosPasos(true)
})

describe("PASOS_GUIA", () => {
  it("tiene 8 pasos con ids únicos y el paso ovz es requisito y detectable", () => {
    expect(PASOS_GUIA).toHaveLength(8)
    expect(TOTAL_PASOS).toBe(8)
    const ids = PASOS_GUIA.map((p) => p.id)
    expect(new Set(ids).size).toBe(ids.length)
    const ovz = PASOS_GUIA.find((p) => p.id === "ovz")!
    expect(ovz.requisito).toBe(true)
    expect(ovz.deteccion).toBe("ovz")
    expect(ovz.coach?.pantalla).toBe("/configuracion")
  })

  it("los tres pasos con dato detectable tienen coachmark en su pantalla", () => {
    const auto = PASOS_GUIA.filter((p) => p.deteccion)
    expect(auto.map((p) => p.id)).toEqual(["ovz", "ganadero", "inventario"])
    for (const p of auto) expect(p.coach).toBeTruthy()
  })
})

describe("ovzConfigurado", () => {
  it("es false sin preferencias o sin los campos mínimos; true cuando están rellenos", () => {
    expect(ovzConfigurado(null)).toBe(false)
    expect(ovzConfigurado(PREFS_VACIAS)).toBe(false)
    // Falta uno de los mínimos.
    expect(ovzConfigurado({ ...PREFS_VACIAS, oficinaDi: "DI-01" })).toBe(false)
    // Todos los mínimos rellenos.
    const completas = { ...PREFS_VACIAS }
    for (const campo of CAMPOS_OVZ_MINIMOS) completas[campo] = "x"
    expect(ovzConfigurado(completas)).toBe(true)
    // Espacios en blanco no cuentan.
    expect(ovzConfigurado({ ...completas, [CAMPOS_OVZ_MINIMOS[0]]: "   " })).toBe(false)
  })
})

describe("progreso por usuario", () => {
  it("por defecto está vacío; guardar y leer conserva confirmados/coach/terminada por usuario", () => {
    expect(leerProgreso(1)).toEqual({ confirmados: [], coachDescartados: [], terminada: false })
    expect(hayProgresoGuardado(1)).toBe(false)
    guardarProgreso(1, { confirmados: ["bienvenida"], coachDescartados: ["ovz"], terminada: false })
    expect(hayProgresoGuardado(1)).toBe(true)
    expect(leerProgreso(1)).toEqual({ confirmados: ["bienvenida"], coachDescartados: ["ovz"], terminada: false })
    // Otro usuario no se ve afectado.
    expect(leerProgreso(2).confirmados).toEqual([])
  })
})

describe("debeAutoabrir", () => {
  it("se autoabre solo sin progreso; no si ya hay progreso, ni con la marca 'visto' antigua, ni con autoapertura apagada", () => {
    configurarAutoAperturaPrimerosPasos(true) // el setup global de tests la deja apagada
    expect(debeAutoabrir(7)).toBe(true)
    guardarProgreso(7, { confirmados: [], coachDescartados: [], terminada: false })
    expect(debeAutoabrir(7)).toBe(false)

    // Usuario "visto" del onboarding antiguo: no se reabre sola.
    localStorage.setItem("ganera.primerosPasos.visto.8", "true")
    expect(debeAutoabrir(8)).toBe(false)

    // Con la autoapertura apagada (tests del layout), nunca.
    configurarAutoAperturaPrimerosPasos(false)
    expect(debeAutoabrir(9)).toBe(false)
  })
})

describe("reapertura manual (pub/sub)", () => {
  it("avisa a los suscritos y deja de hacerlo tras desuscribir", () => {
    const oyente = vi.fn()
    const desuscribir = suscribirAperturaPrimerosPasos(oyente)
    abrirPrimerosPasos()
    expect(oyente).toHaveBeenCalledTimes(1)
    desuscribir()
    abrirPrimerosPasos()
    expect(oyente).toHaveBeenCalledTimes(1)
  })
})
