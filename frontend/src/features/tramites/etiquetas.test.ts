import { describe, expect, it } from "vitest"
import {
  ESTADOS_TRAMITE,
  RESOLUCIONES_CROTAL,
  ROLES_CONTACTO,
  TIPOS_TRAMITE,
  badgeVarianteDeEstado,
  etiquetaRolContacto,
  etiquetaTipoTramite,
  presentacionEstado,
  presentacionResolucion,
  presentacionRolContacto,
} from "./etiquetas"
import { badgeVarianteDeEstado as badgeVarianteDeEstadoReexportado } from "./types"

describe("TIPOS_TRAMITE", () => {
  it("tiene exactamente los 5 tipos del backend con su etiqueta", () => {
    expect(TIPOS_TRAMITE).toEqual({
      ALTA: "Alta",
      BAJA: "Baja",
      CENSO: "Censo",
      MOVIMIENTO: "Movimiento",
      DEMORA: "Demora",
    })
  })

  it.each([
    ["ALTA", "Alta"],
    ["BAJA", "Baja"],
    ["CENSO", "Censo"],
    ["MOVIMIENTO", "Movimiento"],
    ["DEMORA", "Demora"],
  ])("%s se muestra como %s", (tipo, etiqueta) => {
    expect(etiquetaTipoTramite(tipo)).toBe(etiqueta)
  })

  it("un tipo desconocido (p. ej. tras el prompt B) muestra el valor tal cual", () => {
    expect(etiquetaTipoTramite("TRASLADO_FERIA")).toBe("TRASLADO_FERIA")
  })

  it("no confunde claves heredadas del prototipo con tipos", () => {
    expect(etiquetaTipoTramite("toString")).toBe("toString")
    expect(etiquetaTipoTramite("constructor")).toBe("constructor")
  })

  it("nunca devuelve un texto vacío", () => {
    expect(etiquetaTipoTramite("")).toBe("—")
  })
})

describe("ESTADOS_TRAMITE", () => {
  const esperado = {
    PENDIENTE_EXTRACCION: { etiqueta: "Pendiente de extracción", variante: "warning" },
    PENDIENTE_REVISION: { etiqueta: "Pendiente de revisión", variante: "warning" },
    APROBADO: { etiqueta: "Aprobado", variante: "success" },
    EN_PROCESO: { etiqueta: "En proceso", variante: "warning" },
    EJECUTADO_OVZ: { etiqueta: "Ejecutado en OVZ.net", variante: "success" },
    ERROR_OVZ: { etiqueta: "Error en OVZ.net", variante: "danger" },
    RECHAZADO: { etiqueta: "Rechazado", variante: "danger" },
  }

  it("cubre los 7 estados con su etiqueta y variante", () => {
    expect(ESTADOS_TRAMITE).toEqual(esperado)
  })

  it.each(Object.entries(esperado))("%s → presentación y variante", (estado, presentacion) => {
    expect(presentacionEstado(estado)).toEqual(presentacion)
    expect(badgeVarianteDeEstado(estado)).toBe(presentacion.variante)
  })

  it("un estado desconocido se muestra tal cual con badge neutro", () => {
    expect(presentacionEstado("PAUSADO")).toEqual({ etiqueta: "PAUSADO", variante: "outline" })
    expect(badgeVarianteDeEstado("PAUSADO")).toBe("outline")
    expect(presentacionEstado("hasOwnProperty")).toEqual({
      etiqueta: "hasOwnProperty",
      variante: "outline",
    })
    expect(presentacionEstado("")).toEqual({ etiqueta: "—", variante: "outline" })
  })

  it("badgeVarianteDeEstado sigue exportándose desde types.ts (lo cita DESIGN.md)", () => {
    expect(badgeVarianteDeEstadoReexportado).toBe(badgeVarianteDeEstado)
  })
})

describe("RESOLUCIONES_CROTAL", () => {
  const esperado = {
    EN_INVENTARIO: { etiqueta: "En inventario", variante: "success" },
    AMBIGUO: { etiqueta: "Varios animales coinciden", variante: "warning" },
    NO_ENCONTRADO: { etiqueta: "No está en el inventario", variante: "outline" },
    SIN_EXPLOTACION: { etiqueta: "Falta la explotación", variante: "warning" },
  }

  it("cubre las 4 resoluciones con su etiqueta y variante", () => {
    expect(RESOLUCIONES_CROTAL).toEqual(esperado)
  })

  it.each(Object.entries(esperado))("%s → presentación", (resolucion, presentacion) => {
    expect(presentacionResolucion(resolucion)).toEqual(presentacion)
  })

  it("una resolución desconocida se muestra tal cual con badge neutro", () => {
    expect(presentacionResolucion("DUPLICADO")).toEqual({ etiqueta: "DUPLICADO", variante: "outline" })
  })
})

describe("ROLES_CONTACTO", () => {
  it("tiene los 2 roles del backend", () => {
    expect(ROLES_CONTACTO).toEqual({ TITULAR: "Titular", EMPLEADO: "Empleado" })
  })

  it("etiqueta los conocidos y deja tal cual los desconocidos", () => {
    expect(etiquetaRolContacto("TITULAR")).toBe("Titular")
    expect(etiquetaRolContacto("EMPLEADO")).toBe("Empleado")
    expect(etiquetaRolContacto("VETERINARIO")).toBe("VETERINARIO")
  })
})

describe("presentacionRolContacto", () => {
  it("Titular es éxito y Empleado el neutro, con la etiqueta de ROLES_CONTACTO", () => {
    expect(presentacionRolContacto("TITULAR")).toEqual({ etiqueta: ROLES_CONTACTO.TITULAR, variante: "success" })
    expect(presentacionRolContacto("EMPLEADO")).toEqual({ etiqueta: ROLES_CONTACTO.EMPLEADO, variante: "outline" })
  })

  it("un rol desconocido sale tal cual con badge neutro", () => {
    expect(presentacionRolContacto("VETERINARIO")).toEqual({ etiqueta: "VETERINARIO", variante: "outline" })
  })
})
