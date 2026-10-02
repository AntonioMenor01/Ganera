import { afterEach, describe, expect, it, vi } from "vitest"
import { CLAVE_TOKEN, getAuthToken, restaurarTokenGuardado, setAuthToken } from "./authSession"

describe("authSession: token en sessionStorage (decisión 1)", () => {
  // Antes que el afterEach global (orden de pila), que llama a sessionStorage.clear().
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it("la clave es ganera.token", () => {
    expect(CLAVE_TOKEN).toBe("ganera.token")
  })

  it("setAuthToken guarda el token en sessionStorage y en memoria", () => {
    setAuthToken("tok-1")
    expect(sessionStorage.getItem("ganera.token")).toBe("tok-1")
    expect(getAuthToken()).toBe("tok-1")
    expect(localStorage.length).toBe(0)
  })

  it("setAuthToken(null) borra la clave", () => {
    setAuthToken("tok-1")
    setAuthToken(null)
    expect(sessionStorage.getItem("ganera.token")).toBeNull()
    expect(getAuthToken()).toBeNull()
  })

  it("restaurarTokenGuardado lee el token guardado tras una recarga (memoria vacía)", () => {
    sessionStorage.setItem("ganera.token", "tok-guardado")
    expect(getAuthToken()).toBeNull()
    expect(restaurarTokenGuardado()).toBe("tok-guardado")
    expect(getAuthToken()).toBe("tok-guardado")
  })

  it("restaurarTokenGuardado sin nada guardado devuelve null", () => {
    expect(restaurarTokenGuardado()).toBeNull()
  })

  it("un almacenamiento que lanza al escribir no rompe: el token queda en memoria", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("cuota", "QuotaExceededError")
    })
    expect(() => setAuthToken("tok-1")).not.toThrow()
    expect(getAuthToken()).toBe("tok-1")
  })

  it("un almacenamiento que lanza al leer y al borrar no rompe", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new DOMException("bloqueado", "SecurityError")
    })
    vi.spyOn(Storage.prototype, "removeItem").mockImplementation(() => {
      throw new DOMException("bloqueado", "SecurityError")
    })
    expect(restaurarTokenGuardado()).toBeNull()
    setAuthToken("tok-1")
    expect(() => setAuthToken(null)).not.toThrow()
    expect(getAuthToken()).toBeNull()
  })

  it("si el propio acceso a window.sessionStorage lanza (modo privado), sigue en memoria", () => {
    vi.spyOn(window, "sessionStorage", "get").mockImplementation(() => {
      throw new DOMException("bloqueado", "SecurityError")
    })
    expect(restaurarTokenGuardado()).toBeNull()
    expect(() => setAuthToken("tok-1")).not.toThrow()
    expect(getAuthToken()).toBe("tok-1")
    expect(() => setAuthToken(null)).not.toThrow()
  })
})
