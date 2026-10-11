import { afterAll, beforeAll, describe, expect, it } from "vitest"
import {
  HUECO,
  MAX_CARACTERES,
  claveIdempotencia,
  estadoVentana,
  problemaDelTexto,
  textoPropuesto,
} from "./respuestaWhatsapp"
import type { TramiteCrotal, TramiteDetalle, WhatsappTramite } from "./types"

// La hora de la ventana se enseña en Madrid sea cual sea la zona del equipo (como hechoEnOvz.test).
const ZONA_ORIGINAL = process.env.TZ
beforeAll(() => {
  process.env.TZ = "America/New_York"
})
afterAll(() => {
  if (ZONA_ORIGINAL === undefined) delete process.env.TZ
  else process.env.TZ = ZONA_ORIGINAL
})

/** Misma lista que el test de honestidad del modal: no se toca. */
const PROHIBIDAS = /enviad|ejecutad|registrad|en curso en OVZ|tramitad|completad|procesad|presentad/i

const WHATSAPP: WhatsappTramite = {
  destinatarioNombre: "Juan Ganadero",
  telefonoEnmascarado: "+34******001",
  contactoActivo: true,
  ventanaAbiertaHasta: "2031-03-11T06:00:00Z",
  firmaGestoria: "Gestoría Norte",
  motivoNoDisponible: null,
  respuestas: [],
}

function crotal(crotalIndicado: string, completo = crotalIndicado): TramiteCrotal {
  return { crotalIndicado, crotal: completo, animalId: null, enInventario: false, resolucion: "NO_ENCONTRADO" }
}

type Datos = Pick<TramiteDetalle, "estado" | "tipoTramite" | "crotales" | "whatsapp">

function datos(parcial: Partial<Datos> = {}): Datos {
  return { estado: "PENDIENTE_REVISION", tipoTramite: "BAJA_MUERTE", crotales: [], whatsapp: WHATSAPP, ...parcial }
}

describe("textoPropuesto", () => {
  it("PENDIENTE_REVISION: pide lo que falta con el hueco y firma", () => {
    expect(textoPropuesto(datos())).toBe(
      "Hola, Juan Ganadero. Para tramitar tu baja por muerte necesitamos: ___\n— Gestoría Norte",
    )
  })

  it("PENDIENTE_REVISION sin tipo: «tu mensaje»", () => {
    expect(textoPropuesto(datos({ tipoTramite: null }))).toBe(
      "Hola, Juan Ganadero. Para tramitar tu mensaje necesitamos: ___\n— Gestoría Norte",
    )
  })

  it("APROBADO: tipo y crotales (el resuelto, o el indicado), sin mencionar OVZ", () => {
    const texto = textoPropuesto(
      datos({ estado: "APROBADO", crotales: [crotal("1234", "ES010000001234"), crotal("ES020000005678")] }),
    )
    expect(texto).toBe(
      "Hola, Juan Ganadero. Hemos revisado tu trámite (baja por muerte de ES010000001234 y ES020000005678) y lo tramitamos en breve.\n— Gestoría Norte",
    )
    expect(texto).not.toMatch(/OVZ/)
  })

  it("hasta 3 crotales y «y N más»", () => {
    const crotales = ["ES1", "ES2", "ES3", "ES4", "ES5"].map((c) => crotal(c))
    expect(textoPropuesto(datos({ estado: "APROBADO", crotales }))).toContain(
      "(baja por muerte de ES1, ES2, ES3 y 2 más)",
    )
    expect(textoPropuesto(datos({ estado: "APROBADO", crotales: crotales.slice(0, 3) }))).toContain(
      "(baja por muerte de ES1, ES2 y ES3)",
    )
    expect(textoPropuesto(datos({ estado: "APROBADO", crotales: crotales.slice(0, 4) }))).toContain(
      "(baja por muerte de ES1, ES2, ES3 y 1 más)",
    )
  })

  it("si el crotal resuelto viniera vacío, usa el indicado", () => {
    expect(textoPropuesto(datos({ estado: "APROBADO", crotales: [crotal("1234", "")] }))).toContain("de 1234)")
  })

  it("APROBADO sin crotales: solo el tipo; sin nada, sin paréntesis", () => {
    expect(textoPropuesto(datos({ estado: "APROBADO" }))).toContain("Hemos revisado tu trámite (baja por muerte) y")
    expect(textoPropuesto(datos({ estado: "APROBADO", tipoTramite: null }))).toContain(
      "Hemos revisado tu trámite y lo tramitamos en breve.",
    )
  })

  it("HECHO_EN_OVZ: «ya está hecho en OVZ»", () => {
    expect(textoPropuesto(datos({ estado: "HECHO_EN_OVZ", crotales: [crotal("ES010000001234")] }))).toBe(
      "Hola, Juan Ganadero. Tu trámite (baja por muerte de ES010000001234) ya está hecho en OVZ.\n— Gestoría Norte",
    )
  })

  it("RECHAZADO: con hueco obligatorio para el motivo", () => {
    expect(textoPropuesto(datos({ estado: "RECHAZADO" }))).toBe(
      "Hola, Juan Ganadero. No hemos podido tramitar tu mensaje. ___\n— Gestoría Norte",
    )
  })

  it("un tipo desconocido se escribe tal cual (sin romper)", () => {
    expect(textoPropuesto(datos({ tipoTramite: "TIPO_NUEVO" }))).toContain("Para tramitar tu TIPO_NUEVO necesitamos")
  })

  it.each(["PENDIENTE_EXTRACCION", "EN_PROCESO", "EJECUTADO_OVZ", "ERROR_OVZ"] as const)(
    "%s: cadena vacía (no se ofrece responder)",
    (estado) => {
      expect(textoPropuesto(datos({ estado }))).toBe("")
    },
  )

  it("sin bloque de WhatsApp: cadena vacía", () => {
    expect(textoPropuesto(datos({ whatsapp: null }))).toBe("")
  })

  it("sin nombre ni firma: «Hola.» y sin línea de firma", () => {
    expect(
      textoPropuesto(datos({ whatsapp: { ...WHATSAPP, destinatarioNombre: " ", firmaGestoria: "" } })),
    ).toBe("Hola. Para tramitar tu baja por muerte necesitamos: ___")
  })

  it("honestidad: ninguna plantilla casa con las palabras prohibidas y solo la de HECHO_EN_OVZ dice OVZ", () => {
    const crotales = [crotal("ES010000001234"), crotal("1234")]
    const estados = ["PENDIENTE_REVISION", "APROBADO", "HECHO_EN_OVZ", "RECHAZADO"] as const
    const conOvz: string[] = []
    for (const estado of estados) {
      for (const tipoTramite of [null, "ALTA_NACIMIENTO", "BAJA_MUERTE", "SOLICITUD_MOVIMIENTO",
        "CONFIRMACION_MOVIMIENTO", "DECLARACION_CENSO", "DEMORA_CROTALIZACION"]) {
        const texto = textoPropuesto(datos({ estado, tipoTramite, crotales }))
        expect(texto).not.toBe("")
        expect(texto).not.toMatch(PROHIBIDAS)
        if (/OVZ/.test(texto)) conOvz.push(estado)
      }
    }
    expect([...new Set(conOvz)]).toEqual(["HECHO_EN_OVZ"])
  })

  it("el hueco está en pendiente y rechazado, y no en aprobado ni hecho", () => {
    expect(textoPropuesto(datos({ estado: "PENDIENTE_REVISION" }))).toContain(HUECO)
    expect(textoPropuesto(datos({ estado: "RECHAZADO" }))).toContain(HUECO)
    expect(textoPropuesto(datos({ estado: "APROBADO" }))).not.toContain(HUECO)
    expect(textoPropuesto(datos({ estado: "HECHO_EN_OVZ" }))).not.toContain(HUECO)
  })
})

describe("problemaDelTexto (mismos textos que los 400 del backend)", () => {
  it("vacío o en blanco", () => {
    expect(problemaDelTexto("")).toBe("Escribe el texto de la respuesta.")
    expect(problemaDelTexto("  \n ")).toBe("Escribe el texto de la respuesta.")
  })

  it("más de 1600 unidades UTF-16 (como String.length del backend)", () => {
    expect(MAX_CARACTERES).toBe(1600)
    expect(problemaDelTexto("a".repeat(1600))).toBeNull()
    expect(problemaDelTexto("a".repeat(1601))).toBe("La respuesta admite como máximo 1600 caracteres.")
  })

  it("un emoji cuenta 2: 1599 letras + 1 emoji se pasan", () => {
    expect("🐄".length).toBe(2)
    expect(problemaDelTexto("a".repeat(1598) + "🐄")).toBeNull()
    expect(problemaDelTexto("a".repeat(1599) + "🐄")).toBe("La respuesta admite como máximo 1600 caracteres.")
  })

  it("con el hueco sin completar", () => {
    expect(problemaDelTexto("Necesitamos: ___")).toBe("Completa el hueco ___ antes de enviar.")
  })

  it("un texto correcto no tiene problema", () => {
    expect(problemaDelTexto("Necesitamos la fecha de la muerte.")).toBeNull()
  })
})

describe("estadoVentana", () => {
  const ahora = new Date("2031-03-10T09:00:00Z")

  it("sin ventana: cerrada", () => {
    expect(estadoVentana(null, ahora)).toEqual({ tipo: "cerrada" })
    expect(estadoVentana("no es una fecha", ahora)).toEqual({ tipo: "cerrada" })
  })

  it("ya pasada (o justo ahora): cerrada", () => {
    expect(estadoVentana("2031-03-10T08:59:59Z", ahora)).toEqual({ tipo: "cerrada" })
    expect(estadoVentana("2031-03-10T09:00:00Z", ahora)).toEqual({ tipo: "cerrada" })
  })

  it("abierta: fecha y hora de Madrid (invierno, UTC+1)", () => {
    expect(estadoVentana("2031-03-11T06:00:00Z", ahora)).toEqual({ tipo: "abierta", hasta: "11/03/2031, 07:00" })
  })

  it("con exactamente 1 h: abierta todavía", () => {
    expect(estadoVentana("2031-03-10T10:00:00Z", ahora)).toEqual({ tipo: "abierta", hasta: "10/03/2031, 11:00" })
  })

  it("con menos de 1 h: se cierra pronto, con la hora de Madrid", () => {
    expect(estadoVentana("2031-03-10T09:45:00Z", ahora)).toEqual({ tipo: "cierra-pronto", hora: "10:45" })
  })

  it("verano (UTC+2)", () => {
    expect(estadoVentana("2031-07-10T09:30:00Z", new Date("2031-07-10T09:00:00Z"))).toEqual({
      tipo: "cierra-pronto",
      hora: "11:30",
    })
  })
})

describe("claveIdempotencia (N5: también fuera de un contexto seguro)", () => {
  const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

  it("usa crypto.randomUUID cuando existe", () => {
    const c = {
      randomUUID: () => "11111111-2222-4333-8444-555555555555",
      getRandomValues: (() => {
        throw new Error("no")
      }) as Crypto["getRandomValues"],
    }
    expect(claveIdempotencia(c)).toBe("11111111-2222-4333-8444-555555555555")
  })

  it("sin randomUUID (http con la IP de la LAN) genera un UUID v4 con getRandomValues", () => {
    const c = { getRandomValues: crypto.getRandomValues.bind(crypto) }
    const claves = new Set(Array.from({ length: 50 }, () => claveIdempotencia(c)))
    expect(claves.size).toBe(50)
    for (const clave of claves) expect(clave).toMatch(UUID_V4)
  })

  it("los bits de versión y variante salen bien aunque los bytes sean todos 0xff o 0x00", () => {
    for (const relleno of [0xff, 0x00]) {
      const c = {
        getRandomValues: ((a: Uint8Array) => a.fill(relleno)) as Crypto["getRandomValues"],
      }
      expect(claveIdempotencia(c)).toMatch(UUID_V4)
    }
  })
})
