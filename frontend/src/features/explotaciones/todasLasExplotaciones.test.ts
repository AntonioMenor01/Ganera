import { http, HttpResponse } from "msw"
import { afterEach, describe, expect, it, vi } from "vitest"
import { ErrorApi } from "@/shared/api/errores"
import { httpClient } from "@/shared/api/httpClient"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import type { Explotacion } from "./types"
import {
  cargarTodasLasExplotaciones,
  ORDEN_TODAS_LAS_EXPLOTACIONES,
  TAMANIO_PAGINA_TODAS_LAS_EXPLOTACIONES,
} from "./todasLasExplotaciones"

function explotacion(id: number): Explotacion {
  return {
    id,
    codigoRega: `ES0000000${id}`,
    nombre: `Explotación ${id}`,
    ganaderoId: 1,
    nombreGanadero: "Ganadero",
  }
}

interface PaginaFalsa {
  ids: number[]
  totalElements: number
  totalPages: number
}

/** Sirve /explotaciones desde una tabla de páginas y apunta los parámetros de cada petición. */
function servirPaginas(paginas: Record<number, PaginaFalsa | "falla">) {
  const peticiones: URLSearchParams[] = []
  server.use(
    http.get(apiUrl("/explotaciones"), ({ request }) => {
      const params = new URL(request.url).searchParams
      peticiones.push(params)
      const numero = Number(params.get("page"))
      const pagina = paginas[numero]
      if (pagina === undefined) return HttpResponse.json({ motivo: "página inesperada" }, { status: 400 })
      if (pagina === "falla") return new HttpResponse(null, { status: 500 })
      return HttpResponse.json({
        content: pagina.ids.map(explotacion),
        totalElements: pagina.totalElements,
        totalPages: pagina.totalPages,
        number: numero,
        size: TAMANIO_PAGINA_TODAS_LAS_EXPLOTACIONES,
      })
    }),
  )
  return peticiones
}

describe("cargarTodasLasExplotaciones (decisión 20: carga completa o error, nunca parcial)", () => {
  it("pide páginas de 500 ordenadas por código REGA", () => {
    expect(TAMANIO_PAGINA_TODAS_LAS_EXPLOTACIONES).toBe(500)
    expect(ORDEN_TODAS_LAS_EXPLOTACIONES).toBe("codigoRega,asc")
  })

  it("recorre TODAS las páginas hasta totalPages y devuelve la lista entera en orden", async () => {
    const peticiones = servirPaginas({
      0: { ids: [1, 2], totalElements: 5, totalPages: 3 },
      1: { ids: [3, 4], totalElements: 5, totalPages: 3 },
      2: { ids: [5], totalElements: 5, totalPages: 3 },
    })

    const todas = await cargarTodasLasExplotaciones()

    expect(todas.map((e) => e.id)).toEqual([1, 2, 3, 4, 5])
    expect(peticiones.map((p) => p.get("page"))).toEqual(["0", "1", "2"])
    for (const p of peticiones) {
      expect(p.get("size")).toBe("500")
      expect(p.get("sort")).toBe("codigoRega,asc")
    }
  })

  it("sin explotaciones: una sola petición y lista vacía", async () => {
    const peticiones = servirPaginas({ 0: { ids: [], totalElements: 0, totalPages: 0 } })

    await expect(cargarTodasLasExplotaciones()).resolves.toEqual([])
    expect(peticiones).toHaveLength(1)
  })

  it("si falla la página N, es un error y no devuelve ninguna lista", async () => {
    const peticiones = servirPaginas({
      0: { ids: [1, 2], totalElements: 5, totalPages: 3 },
      1: "falla",
      2: { ids: [5], totalElements: 5, totalPages: 3 },
    })

    const error = await cargarTodasLasExplotaciones().then(
      () => null,
      (e: unknown) => e,
    )

    expect(error).toBeInstanceOf(ErrorApi)
    expect((error as ErrorApi).tipo).toBe("servidor")
    // No sigue pidiendo páginas tras el fallo: no hay nada que completar.
    expect(peticiones.map((p) => p.get("page"))).toEqual(["0", "1"])
  })

  it("si el número recibido no cuadra con totalElements, es un error (nunca una lista parcial)", async () => {
    servirPaginas({
      0: { ids: [1, 2], totalElements: 5, totalPages: 2 },
      1: { ids: [3, 4], totalElements: 5, totalPages: 2 },
    })

    await expect(cargarTodasLasExplotaciones()).rejects.toBeInstanceOf(ErrorApi)
  })

  it("si llegan ids repetidos (la lista se movió entre páginas), es un error aunque el número cuadre", async () => {
    servirPaginas({
      0: { ids: [1, 2], totalElements: 4, totalPages: 2 },
      1: { ids: [2, 3], totalElements: 4, totalPages: 2 },
    })

    await expect(cargarTodasLasExplotaciones()).rejects.toBeInstanceOf(ErrorApi)
  })

  // I1 de la revisión de Task 6: estos dos casos solo los caza la comprobación "el total cambió
  // entre páginas". Lo recibido cuadra con el total de referencia (página 0) y no hay ids
  // repetidos, así que ni el recuento ni el control de duplicados los detectan.
  it("si totalElements cambia entre páginas, es un error aunque lo recibido cuadre con la página 0", async () => {
    // Una importación a la vez añade la explotación 5 entre la página 0 y la 1.
    servirPaginas({
      0: { ids: [1, 2], totalElements: 4, totalPages: 2 },
      1: { ids: [3, 4], totalElements: 5, totalPages: 2 },
    })

    await expect(cargarTodasLasExplotaciones()).rejects.toBeInstanceOf(ErrorApi)
  })

  it("si solo totalPages cambia entre páginas, también es un error", async () => {
    servirPaginas({
      0: { ids: [1, 2], totalElements: 4, totalPages: 2 },
      1: { ids: [3, 4], totalElements: 4, totalPages: 3 },
    })

    await expect(cargarTodasLasExplotaciones()).rejects.toBeInstanceOf(ErrorApi)
  })
})

describe("cargarTodasLasExplotaciones: cancelación (pendiente de las Tasks 6 y 7)", () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it("el AbortSignal llega a axios en TODAS las páginas", async () => {
    servirPaginas({
      0: { ids: [1, 2], totalElements: 3, totalPages: 2 },
      1: { ids: [3], totalElements: 3, totalPages: 2 },
    })
    // Espía sobre el cliente real: la petición sigue saliendo (y MSW la contesta).
    const get = vi.spyOn(httpClient, "get")
    const controlador = new AbortController()

    await cargarTodasLasExplotaciones(controlador.signal)

    expect(get).toHaveBeenCalledTimes(2)
    for (const [ruta, config] of get.mock.calls) {
      expect(ruta).toBe("/explotaciones")
      expect(config?.signal).toBe(controlador.signal)
    }
  })
})
