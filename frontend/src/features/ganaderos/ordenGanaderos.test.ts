import { describe, expect, it } from "vitest"
import { ORDEN_INICIAL, ariaSortDe, parametrosSort, siguienteOrden } from "./ordenGanaderos"

describe("ordenGanaderos", () => {
  it("el orden inicial no manda sort (aplica el del backend: nombre,id)", () => {
    expect(ORDEN_INICIAL).toBeNull()
    expect(parametrosSort(ORDEN_INICIAL)).toEqual([])
  })

  it("sin orden elegido, la cabecera Nombre se muestra ascendente y NIF sin orden", () => {
    expect(ariaSortDe(null, "nombre")).toBe("ascending")
    expect(ariaSortDe(null, "nif")).toBeUndefined()
  })

  it("pulsar Nombre desde el inicial lo invierte (ya se veía ascendente)", () => {
    expect(siguienteOrden(null, "nombre")).toEqual({ campo: "nombre", direccion: "desc" })
  })

  it("pulsar NIF desde el inicial ordena por NIF ascendente", () => {
    expect(siguienteOrden(null, "nif")).toEqual({ campo: "nif", direccion: "asc" })
  })

  it("pulsar la columna activa alterna la dirección", () => {
    expect(siguienteOrden({ campo: "nif", direccion: "asc" }, "nif")).toEqual({
      campo: "nif",
      direccion: "desc",
    })
    expect(siguienteOrden({ campo: "nombre", direccion: "desc" }, "nombre")).toEqual({
      campo: "nombre",
      direccion: "asc",
    })
  })

  it("pulsar otra columna empieza en ascendente", () => {
    expect(siguienteOrden({ campo: "nif", direccion: "desc" }, "nombre")).toEqual({
      campo: "nombre",
      direccion: "asc",
    })
  })

  it("el parámetro sort lleva el campo, la dirección y el id como desempate", () => {
    expect(parametrosSort({ campo: "nombre", direccion: "desc" })).toEqual(["nombre,desc", "id,desc"])
    expect(parametrosSort({ campo: "nif", direccion: "asc" })).toEqual(["nif,asc", "id,asc"])
  })

  it("aria-sort refleja el orden elegido", () => {
    const orden = { campo: "nif", direccion: "desc" } as const
    expect(ariaSortDe(orden, "nif")).toBe("descending")
    expect(ariaSortDe(orden, "nombre")).toBeUndefined()
  })
})
