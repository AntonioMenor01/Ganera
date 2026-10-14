import { act, renderHook } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import { useConsultaAncho } from "./useConsultaAncho"

/** matchMedia simulado: `coincide` decide qué consultas se cumplen; `cambiar` avisa a los oyentes. */
function simularMatchMedia(coincide: (consulta: string) => boolean) {
  const oyentes = new Map<string, Set<() => void>>()
  let regla = coincide
  vi.stubGlobal(
    "matchMedia",
    vi.fn((media: string) => ({
      media,
      get matches() {
        return regla(media)
      },
      addEventListener: (_tipo: string, oyente: () => void) => {
        if (!oyentes.has(media)) oyentes.set(media, new Set())
        oyentes.get(media)!.add(oyente)
      },
      removeEventListener: (_tipo: string, oyente: () => void) => oyentes.get(media)?.delete(oyente),
    })),
  )
  return {
    oyentes,
    cambiar(nueva: (consulta: string) => boolean) {
      regla = nueva
      act(() => oyentes.forEach((grupo) => grupo.forEach((oyente) => oyente())))
    },
  }
}

describe("useConsultaAncho", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("sin matchMedia (jsdom, navegador antiguo) devuelve true: el escritorio", () => {
    vi.stubGlobal("matchMedia", undefined)
    const { result } = renderHook(() => useConsultaAncho("(min-width: 64rem)"))
    expect(result.current).toBe(true)
  })

  it("devuelve si se cumple la consulta que recibe", () => {
    simularMatchMedia((consulta) => consulta === "(min-width: 64rem)")
    expect(renderHook(() => useConsultaAncho("(min-width: 64rem)")).result.current).toBe(true)
    expect(renderHook(() => useConsultaAncho("(min-width: 90rem)")).result.current).toBe(false)
  })

  it("reacciona al cambio de ancho y se desuscribe al desmontar", () => {
    const pantalla = simularMatchMedia(() => false)
    const { result, unmount } = renderHook(() => useConsultaAncho("(min-width: 90rem)"))
    expect(result.current).toBe(false)

    pantalla.cambiar(() => true)
    expect(result.current).toBe(true)

    unmount()
    expect(pantalla.oyentes.get("(min-width: 90rem)")!.size).toBe(0)
  })
})
