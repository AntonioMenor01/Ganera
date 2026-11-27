import { describe, expect, it } from "vitest"
import type { Alerta } from "@/features/alertas/api"
import { construirNovedades, textoContador, varianteDeSeveridad } from "./novedades"

function alerta(extra: Partial<Alerta> = {}): Alerta {
  return {
    id: "a1",
    tipo: "BAJA_PLAZO_7D",
    titulo: "Plazo de baja por muerte",
    detalle: "Vence el 2026-12-01.",
    venceEl: "2026-12-01",
    tramiteId: 7,
    severidad: "warning",
    ...extra,
  }
}

describe("varianteDeSeveridad", () => {
  it("critical → danger; cualquier otra → warning", () => {
    expect(varianteDeSeveridad("critical")).toBe("danger")
    expect(varianteDeSeveridad("warning")).toBe("warning")
    expect(varianteDeSeveridad("loquesea")).toBe("warning")
  })
})

describe("construirNovedades", () => {
  it("mapea título/detalle/severidad y lleva a su trámite si hay tramiteId, si no al calendario", () => {
    const items = construirNovedades([
      alerta({ id: "con-tramite", tramiteId: 7, severidad: "critical" }),
      alerta({ id: "sin-tramite", tramiteId: null, titulo: "Aviso general" }),
    ])
    expect(items).toHaveLength(2)
    expect(items[0]).toMatchObject({ id: "con-tramite", variante: "danger", to: "/tramites", tramiteId: 7 })
    expect(items[1]).toMatchObject({ id: "sin-tramite", to: "/calendario", tramiteId: null })
  })

  it("sin alertas devuelve lista vacía (estado 'al día' honesto)", () => {
    expect(construirNovedades([])).toEqual([])
  })
})

describe("textoContador", () => {
  it("null si 0, el número hasta 9, y '9+' por encima", () => {
    expect(textoContador(0)).toBeNull()
    expect(textoContador(3)).toBe("3")
    expect(textoContador(9)).toBe("9")
    expect(textoContador(12)).toBe("9+")
  })
})
