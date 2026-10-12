import { afterAll, beforeAll, describe, expect, it } from "vitest"
import { lineasHistorialDescarte, textoBandaDescartado, textoEventoDescarte } from "./descarte"

// Como en hechoEnOvz.test.ts: otra zona en este fichero, para que olvidar `timeZone` se note.
const ZONA_ORIGINAL = process.env.TZ

beforeAll(() => {
  process.env.TZ = "America/New_York"
})

afterAll(() => {
  if (ZONA_ORIGINAL === undefined) delete process.env.TZ
  else process.env.TZ = ZONA_ORIGINAL
})

const VERANO = "2026-10-07T10:30:00Z" // 12:30 en Madrid (UTC+2)
const INVIERNO = "2026-01-15T11:30:00.123456Z" // 12:30 en Madrid (UTC+1)

describe("textoEventoDescarte (una línea del historial, hora de Madrid)", () => {
  it("la zona del sistema en este test no es Madrid (si no, el test no probaría nada)", () => {
    expect(Intl.DateTimeFormat().resolvedOptions().timeZone).not.toBe("Europe/Madrid")
  })

  it("descartado, en verano", () => {
    expect(textoEventoDescarte({ tipo: "DESCARTADO", usuarioNombre: "Ana Pérez", en: VERANO })).toBe(
      "Descartado por Ana Pérez el 07/10/2026, 12:30",
    )
  })

  it("descarte deshecho, en invierno (con microsegundos)", () => {
    expect(textoEventoDescarte({ tipo: "DESCARTE_DESHECHO", usuarioNombre: "Luis", en: INVIERNO })).toBe(
      "Descarte deshecho por Luis el 15/01/2026, 12:30",
    )
  })

  it("sin nombre o sin fecha válida se omite esa parte; nunca «Invalid Date»", () => {
    expect(textoEventoDescarte({ tipo: "DESCARTADO", usuarioNombre: "  ", en: VERANO })).toBe(
      "Descartado el 07/10/2026, 12:30",
    )
    expect(textoEventoDescarte({ tipo: "DESCARTE_DESHECHO", usuarioNombre: "Ana", en: "no es una fecha" })).toBe(
      "Descarte deshecho por Ana",
    )
    expect(textoEventoDescarte({ tipo: "DESCARTADO", usuarioNombre: null, en: null })).toBe("Descartado")
  })

  it("un tipo desconocido no se enseña (null)", () => {
    expect(textoEventoDescarte({ tipo: "ARCHIVADO", usuarioNombre: "Ana", en: VERANO })).toBeNull()
    expect(textoEventoDescarte({ tipo: "toString", usuarioNombre: "Ana", en: VERANO })).toBeNull()
  })
})

describe("lineasHistorialDescarte", () => {
  it("una línea por evento conocido, en el orden que llega (cronológico); nunca lanza", () => {
    expect(
      lineasHistorialDescarte([
        { tipo: "DESCARTADO", usuarioNombre: "Ana", en: VERANO },
        { tipo: "RARO", usuarioNombre: "X", en: VERANO },
        { tipo: "DESCARTE_DESHECHO", usuarioNombre: "Luis", en: "2026-10-07T11:00:00Z" },
      ]),
    ).toEqual(["Descartado por Ana el 07/10/2026, 12:30", "Descarte deshecho por Luis el 07/10/2026, 13:00"])
    expect(lineasHistorialDescarte([])).toEqual([])
    expect(lineasHistorialDescarte(undefined)).toEqual([])
    expect(lineasHistorialDescarte(null)).toEqual([])
  })
})

describe("textoBandaDescartado (la banda de un descartado)", () => {
  it("con el último descarte: quién y cuándo, con punto final", () => {
    expect(
      textoBandaDescartado([
        { tipo: "DESCARTADO", usuarioNombre: "Luis", en: "2026-10-06T08:00:00Z" },
        { tipo: "DESCARTE_DESHECHO", usuarioNombre: "Luis", en: "2026-10-06T09:00:00Z" },
        { tipo: "DESCARTADO", usuarioNombre: "Ana Pérez", en: VERANO },
      ]),
    ).toBe("Descartado como mensaje sin trámite por Ana Pérez el 07/10/2026, 12:30.")
  })

  it("sin nombre, sin fecha o sin ningún evento de descarte, se omite esa parte", () => {
    expect(textoBandaDescartado([{ tipo: "DESCARTADO", usuarioNombre: null, en: INVIERNO }])).toBe(
      "Descartado como mensaje sin trámite el 15/01/2026, 12:30.",
    )
    expect(textoBandaDescartado([{ tipo: "DESCARTADO", usuarioNombre: "Ana", en: "" }])).toBe(
      "Descartado como mensaje sin trámite por Ana.",
    )
    expect(textoBandaDescartado([])).toBe("Descartado como mensaje sin trámite.")
    expect(textoBandaDescartado(undefined)).toBe("Descartado como mensaje sin trámite.")
  })
})
