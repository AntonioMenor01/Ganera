import { http, HttpResponse } from "msw"
import { afterEach, describe, expect, it, vi } from "vitest"
import { httpClient } from "@/shared/api/httpClient"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { listarAnimalesDeExplotacion } from "./api"

afterEach(() => {
  vi.restoreAllMocks()
})

describe("listarAnimalesDeExplotacion: cancelación (pendiente de las Tasks 6 y 7)", () => {
  it("pasa el AbortSignal a axios, con la ruta y la página pedidas", async () => {
    server.use(
      http.get(apiUrl("/explotaciones/:id/animales"), () =>
        HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
      ),
    )
    // Espía sobre el cliente real: la petición sigue saliendo (y MSW la contesta).
    const get = vi.spyOn(httpClient, "get")
    const controlador = new AbortController()

    await listarAnimalesDeExplotacion(7, 2, 20, controlador.signal)

    expect(get).toHaveBeenCalledTimes(1)
    const [ruta, config] = get.mock.calls[0]
    expect(ruta).toBe("/explotaciones/7/animales")
    expect(String(config?.params)).toContain("page=2&size=20")
    expect(config?.signal).toBe(controlador.signal)
  })
})
