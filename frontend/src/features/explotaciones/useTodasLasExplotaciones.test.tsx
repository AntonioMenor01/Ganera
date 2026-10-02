import { act, renderHook, waitFor } from "@testing-library/react"
import { http, HttpResponse } from "msw"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { cargarTodasLasExplotaciones } from "./todasLasExplotaciones"
import { useTodasLasExplotaciones, type CargaTodasLasExplotaciones } from "./useTodasLasExplotaciones"

// El loader real, envuelto solo para ver qué AbortSignal le pasa el hook: el signal de la request
// de MSW no refleja el abort de XHR en jsdom. La petición HTTP y el mapeo de la cancelación a
// ErrorApi{cancelado} (interceptor de httpClient) siguen siendo los reales.
vi.mock("./todasLasExplotaciones", async (importOriginal) => {
  const real = await importOriginal<typeof import("./todasLasExplotaciones")>()
  return { ...real, cargarTodasLasExplotaciones: vi.fn(real.cargarTodasLasExplotaciones) }
})

/** AbortSignal que el hook pasó al loader en cada carga, en orden. */
function senalesDelHook(): AbortSignal[] {
  return vi.mocked(cargarTodasLasExplotaciones).mock.calls.map(([signal]) => signal as AbortSignal)
}

beforeEach(() => {
  vi.mocked(cargarTodasLasExplotaciones).mockClear()
})

function paginaUnica(ids: number[]) {
  return HttpResponse.json({
    content: ids.map((id) => ({
      id,
      codigoRega: `ES${id}`,
      nombre: `Explotación ${id}`,
      ganaderoId: 1,
      nombreGanadero: "Ganadero",
    })),
    totalElements: ids.length,
    totalPages: ids.length === 0 ? 0 : 1,
    number: 0,
    size: 500,
  })
}

describe("useTodasLasExplotaciones", () => {
  it("empieza cargando y termina con la lista completa, indexada por id", async () => {
    server.use(http.get(apiUrl("/explotaciones"), () => paginaUnica([4, 9])))

    const { result } = renderHook(() => useTodasLasExplotaciones())

    expect(result.current.carga.estado).toBe("cargando")
    await waitFor(() => expect(result.current.carga.estado).toBe("listo"))
    const carga = result.current.carga
    if (carga.estado !== "listo") throw new Error("debería estar listo")
    expect(carga.explotaciones.map((e) => e.id)).toEqual([4, 9])
    expect(carga.porId.get(9)?.codigoRega).toBe("ES9")
  })

  it("un fallo es un error visible sin lista, y Reintentar vuelve a cargar todo", async () => {
    let llamadas = 0
    server.use(
      http.get(apiUrl("/explotaciones"), () => {
        llamadas += 1
        return llamadas === 1 ? HttpResponse.error() : paginaUnica([1])
      }),
    )

    const { result } = renderHook(() => useTodasLasExplotaciones())

    await waitFor(() => expect(result.current.carga.estado).toBe("error"))
    expect(result.current.carga).not.toHaveProperty("explotaciones")

    act(() => result.current.reintentar())
    expect(result.current.carga.estado).toBe("cargando")
    await waitFor(() => expect(result.current.carga.estado).toBe("listo"))
    expect(llamadas).toBe(2)
  })

  it("carga una sola vez durante la vida del componente (caché de página)", async () => {
    let llamadas = 0
    server.use(
      http.get(apiUrl("/explotaciones"), () => {
        llamadas += 1
        return paginaUnica([1])
      }),
    )

    const { result, rerender } = renderHook(() => useTodasLasExplotaciones())
    await waitFor(() => expect(result.current.carga.estado).toBe("listo"))
    rerender()
    rerender()

    expect(llamadas).toBe(1)
  })

  describe("cancelación (M2 de la revisión de Task 6)", () => {
    /** Promesa que el test abre cuando quiere: deja una respuesta de MSW "en vuelo". */
    function puerta() {
      let abrir!: () => void
      const promesa = new Promise<void>((resolve) => {
        abrir = resolve
      })
      return { promesa, abrir }
    }

    it("desmontar con la petición en vuelo la cancela", async () => {
      const { promesa, abrir } = puerta()
      let enVuelo = 0
      server.use(
        http.get(apiUrl("/explotaciones"), async () => {
          enVuelo += 1
          await promesa
          return paginaUnica([1])
        }),
      )

      const { unmount } = renderHook(() => useTodasLasExplotaciones())
      await waitFor(() => expect(enVuelo).toBe(1))
      const [senal] = senalesDelHook()
      expect(senal.aborted).toBe(false)

      unmount()

      expect(senal.aborted).toBe(true)
      abrir()
    })

    it("Reintentar con la petición en vuelo cancela la anterior, y esa cancelación nunca es un error", async () => {
      const primera = puerta()
      const segunda = puerta()
      let peticiones = 0
      server.use(
        http.get(apiUrl("/explotaciones"), async () => {
          peticiones += 1
          await (peticiones === 1 ? primera.promesa : segunda.promesa)
          return paginaUnica([1])
        }),
      )
      const estados: CargaTodasLasExplotaciones["estado"][] = []

      const { result } = renderHook(() => {
        const valor = useTodasLasExplotaciones()
        estados.push(valor.carga.estado)
        return valor
      })
      await waitFor(() => expect(peticiones).toBe(1))
      const [senalPrimera] = senalesDelHook()

      act(() => result.current.reintentar())

      await waitFor(() => expect(peticiones).toBe(2))
      expect(senalPrimera.aborted).toBe(true)
      expect(senalesDelHook()[1].aborted).toBe(false)
      // Se deja asentar el rechazo de la petición cancelada antes de soltar la segunda: si se
      // tratara como un fallo, aquí ya se habría pintado el estado "error".
      await new Promise((resolve) => setTimeout(resolve, 50))
      expect(result.current.carga.estado).toBe("cargando")

      segunda.abrir()
      primera.abrir()
      await waitFor(() => expect(result.current.carga.estado).toBe("listo"))
      expect(estados).not.toContain("error")
    })
  })
})
