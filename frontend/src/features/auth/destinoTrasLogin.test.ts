import { describe, expect, it } from "vitest"
import { destinoTrasLogin } from "./destinoTrasLogin"

const POR_DEFECTO = "/tramites"

describe("destinoTrasLogin (M5): solo rutas internas del mismo origen", () => {
  it.each([
    ["/ganaderos/5?x=1", "/ganaderos/5?x=1"],
    ["/tramites?estado=APROBADO", "/tramites?estado=APROBADO"],
    ["/explotaciones#arriba", "/explotaciones#arriba"],
  ])("acepta %s", (from, esperado) => {
    expect(destinoTrasLogin({ from })).toBe(esperado)
  })

  it.each([
    "//evil.com",
    "/\\evil.com",
    "\\\\evil.com",
    "/ganaderos\\..\\..\\evil.com",
    "https://evil.com",
    "javascript:alert(1)",
    "/\t/evil.com",
    // Con ruta: sin ella, la exclusión de "/" enmascararía el cambio de origen.
    "/\\evil.com/ganaderos",
    "/\t/evil.com/ganaderos",
    "/\n/evil.com/ganaderos",
    "https://evil.com/ganaderos",
    "/.//evil.com",
    "/a/..//evil.com",
    "/%2e//evil.com",
    "/login",
    "/login?x=1",
    "/login#a",
    "/login/",
    "/registro",
    "/registro?x=1",
    "/",
    "",
    "ganaderos",
  ])("rechaza %j → /tramites", (from) => {
    expect(destinoTrasLogin({ from })).toBe(POR_DEFECTO)
  })

  it.each([null, undefined, {}, { from: 42 }, "texto"])("sin from válido (%j) → /tramites", (state) => {
    expect(destinoTrasLogin(state)).toBe(POR_DEFECTO)
  })
})
