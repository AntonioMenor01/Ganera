import { afterAll, beforeAll, describe, expect, it } from "vitest"
import {
  PISTA_MARCAR_HECHO_EN_OVZ,
  PREGUNTA_QUITAR_MARCA,
  fechaHechoEnOvz,
  textoHechoEnOvz,
} from "./hechoEnOvz"

// La fecha se enseña SIEMPRE en hora de Madrid, sea cual sea la zona del equipo: se fuerza otra zona
// en este fichero para que olvidar `timeZone` se note (en el equipo de desarrollo es Madrid).
const ZONA_ORIGINAL = process.env.TZ

beforeAll(() => {
  process.env.TZ = "America/New_York"
})

afterAll(() => {
  if (ZONA_ORIGINAL === undefined) delete process.env.TZ
  else process.env.TZ = ZONA_ORIGINAL
})

describe("fechaHechoEnOvz (Europe/Madrid)", () => {
  it("la zona del sistema en este test no es Madrid (si no, el test no probaría nada)", () => {
    expect(Intl.DateTimeFormat().resolvedOptions().timeZone).not.toBe("Europe/Madrid")
  })

  it("verano (UTC+2)", () => {
    expect(fechaHechoEnOvz("2026-10-07T10:30:00Z")).toBe("07/10/2026, 12:30")
  })

  it("invierno (UTC+1)", () => {
    expect(fechaHechoEnOvz("2026-01-15T11:30:00Z")).toBe("15/01/2026, 12:30")
  })

  it("la hora repetida de octubre: las dos 02:30 se ven igual", () => {
    expect(fechaHechoEnOvz("2026-10-25T00:30:00Z")).toBe("25/10/2026, 02:30")
    expect(fechaHechoEnOvz("2026-10-25T01:30:00Z")).toBe("25/10/2026, 02:30")
  })

  it("cambia de día con la hora de Madrid, no con la UTC", () => {
    expect(fechaHechoEnOvz("2026-10-07T22:05:00Z")).toBe("08/10/2026, 00:05")
  })

  it("acepta la fracción de microsegundos que manda el backend", () => {
    expect(fechaHechoEnOvz("2026-10-07T20:51:21.498195Z")).toBe("07/10/2026, 22:51")
  })

  it.each([null, undefined, "", "no es una fecha", "2026-13-45T99:99:99Z"])(
    "un valor inválido (%s) no rompe: null",
    (valor) => {
      expect(fechaHechoEnOvz(valor)).toBeNull()
    },
  )
})

describe("textoHechoEnOvz (la franja)", () => {
  it("con nombre y fecha", () => {
    expect(textoHechoEnOvz({ hechoEnOvzPorNombre: "Ana Pérez", hechoEnOvzEn: "2026-10-07T10:30:00Z" })).toBe(
      "Hecho en OVZ por Ana Pérez el 07/10/2026, 12:30",
    )
  })

  it("sin nombre (o en blanco): sin «por»", () => {
    expect(textoHechoEnOvz({ hechoEnOvzPorNombre: null, hechoEnOvzEn: "2026-10-07T10:30:00Z" })).toBe(
      "Hecho en OVZ el 07/10/2026, 12:30",
    )
    expect(textoHechoEnOvz({ hechoEnOvzPorNombre: "  ", hechoEnOvzEn: "2026-10-07T10:30:00Z" })).toBe(
      "Hecho en OVZ el 07/10/2026, 12:30",
    )
  })

  it("sin fecha válida: sin «el»", () => {
    expect(textoHechoEnOvz({ hechoEnOvzPorNombre: "Ana Pérez", hechoEnOvzEn: "basura" })).toBe(
      "Hecho en OVZ por Ana Pérez",
    )
    expect(textoHechoEnOvz({ hechoEnOvzPorNombre: null, hechoEnOvzEn: null })).toBe("Hecho en OVZ")
  })

  it("el nombre se recorta", () => {
    expect(textoHechoEnOvz({ hechoEnOvzPorNombre: " Ana ", hechoEnOvzEn: null })).toBe("Hecho en OVZ por Ana")
  })
})

describe("textos fijos (honestidad: la persona es el sujeto)", () => {
  it("la pista del botón de marcar y la pregunta de quitar la marca", () => {
    expect(PISTA_MARCAR_HECHO_EN_OVZ).toBe("Púlsalo cuando lo hayas terminado tú en OVZ. Ganera no envía nada.")
    expect(PREGUNTA_QUITAR_MARCA).toBe("¿Quitar la marca de hecho en OVZ? En OVZ no cambia nada.")
  })
})
