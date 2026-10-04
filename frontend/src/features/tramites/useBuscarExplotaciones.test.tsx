import { act, renderHook, waitFor } from "@testing-library/react"
import { http, HttpResponse } from "msw"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { listarExplotaciones } from "@/features/explotaciones/api"
import type { Explotacion } from "@/features/explotaciones/types"
import {
  ESPERA_BUSQUEDA_MS,
  TAMANIO_BUSQUEDA,
  useBuscarExplotaciones,
} from "./useBuscarExplotaciones"

// La función real, envuelta solo para ver con qué consulta y AbortSignal se llama: el signal de la
// request de MSW no refleja el abort de XHR en jsdom (mismo patrón que AnimalesDeExplotacion).
vi.mock("@/features/explotaciones/api", async (importOriginal) => {
  const real = await importOriginal<typeof import("@/features/explotaciones/api")>()
  return { ...real, listarExplotaciones: vi.fn(real.listarExplotaciones) }
})

beforeEach(() => {
  vi.mocked(listarExplotaciones).mockClear()
})

function explotacion(id: number, nombre: string): Explotacion {
  return { id, codigoRega: `ES${String(id).padStart(12, "0")}`, nombre, ganaderoId: 1, nombreGanadero: "Ana" }
}

/** Consultas (q) de cada llamada, en orden: undefined = sin q. */
function consultasPedidas() {
  return vi.mocked(listarExplotaciones).mock.calls.map(([, , opciones]) => opciones?.q)
}

function senalDe(q: string | undefined): AbortSignal {
  const llamada = vi.mocked(listarExplotaciones).mock.calls.find(([, , opciones]) => opciones?.q === q)
  if (!llamada) throw new Error(`no se pidió q=${String(q)}`)
  return llamada[2]!.signal!
}

function diferido() {
  let abrir!: () => void
  const promesa = new Promise<void>((r) => {
    abrir = r
  })
  return { promesa, abrir }
}

/** GET /explotaciones que responde con una explotación cuyo nombre es la q recibida. */
function servidorEco(esperas: Partial<Record<string, Promise<void>>> = {}) {
  server.use(
    http.get(apiUrl("/explotaciones"), async ({ request }) => {
      const q = new URL(request.url).searchParams.get("q") ?? ""
      await esperas[q]
      return HttpResponse.json({
        content: [explotacion(1, `resultado de «${q}»`)],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: TAMANIO_BUSQUEDA,
      })
    }),
  )
}

function montar(inicial: { abierta: boolean; consulta: string }) {
  return renderHook((props: { abierta: boolean; consulta: string }) => useBuscarExplotaciones(props), {
    initialProps: inicial,
  })
}

const esperar = (ms: number) => new Promise((r) => setTimeout(r, ms))

describe("useBuscarExplotaciones (D3a, D3c)", () => {
  it("con el desplegable cerrado no pide nada", async () => {
    servidorEco()
    const { result } = montar({ abierta: false, consulta: "" })
    await act(() => esperar(ESPERA_BUSQUEDA_MS + 50))
    expect(listarExplotaciones).not.toHaveBeenCalled()
    expect(result.current.busqueda.estado).toBe("inactiva")
  })

  it("al abrir pide enseguida la primera página de 20, sin q", async () => {
    servidorEco()
    const { result } = montar({ abierta: true, consulta: "" })
    expect(result.current.busqueda.estado).toBe("buscando")
    await waitFor(() => expect(result.current.busqueda.estado).toBe("lista"))
    expect(listarExplotaciones).toHaveBeenCalledTimes(1)
    const [page, size, opciones] = vi.mocked(listarExplotaciones).mock.calls[0]
    expect([page, size, opciones?.q]).toEqual([0, TAMANIO_BUSQUEDA, ""])
    expect(TAMANIO_BUSQUEDA).toBe(20)
  })

  it("teclear rápido lanza UNA petición, 300 ms después de la última pulsación, con la q recortada", async () => {
    servidorEco()
    const { result, rerender } = montar({ abierta: true, consulta: "" })
    await waitFor(() => expect(result.current.busqueda.estado).toBe("lista"))

    rerender({ abierta: true, consulta: "d" })
    rerender({ abierta: true, consulta: "de" })
    rerender({ abierta: true, consulta: "deh " })
    expect(result.current.busqueda.estado).toBe("buscando")
    await act(() => esperar(ESPERA_BUSQUEDA_MS - 100))
    expect(consultasPedidas()).toEqual([""])

    await waitFor(() => expect(result.current.busqueda.estado).toBe("lista"))
    expect(consultasPedidas()).toEqual(["", "deh"])
    if (result.current.busqueda.estado !== "lista") throw new Error()
    expect(result.current.busqueda.explotaciones[0].nombre).toBe("resultado de «deh»")
  })

  it("una respuesta vieja nunca pisa a una nueva, y la petición vieja se cancela", async () => {
    const lenta = diferido()
    servidorEco({ uno: lenta.promesa })
    const { result, rerender } = montar({ abierta: true, consulta: "" })
    await waitFor(() => expect(result.current.busqueda.estado).toBe("lista"))

    rerender({ abierta: true, consulta: "uno" })
    await waitFor(() => expect(consultasPedidas()).toEqual(["", "uno"]))
    rerender({ abierta: true, consulta: "dos" })
    expect(senalDe("uno").aborted).toBe(true)

    await waitFor(() => expect(result.current.busqueda.estado).toBe("lista"))
    if (result.current.busqueda.estado !== "lista") throw new Error()
    expect(result.current.busqueda.explotaciones[0].nombre).toBe("resultado de «dos»")

    // La lenta llega después (MSW+jsdom no la corta): no cambia nada.
    lenta.abrir()
    await act(() => esperar(50))
    if (result.current.busqueda.estado !== "lista") throw new Error()
    expect(result.current.busqueda.explotaciones[0].nombre).toBe("resultado de «dos»")
  })

  it("total y mostradas: el total es totalElements, no el número recibido (D3c)", async () => {
    server.use(
      http.get(apiUrl("/explotaciones"), () =>
        HttpResponse.json({
          content: Array.from({ length: 20 }, (_, i) => explotacion(i + 1, `F${i}`)),
          totalElements: 137,
          totalPages: 7,
          number: 0,
          size: 20,
        }),
      ),
    )
    const { result } = montar({ abierta: true, consulta: "" })
    await waitFor(() => expect(result.current.busqueda.estado).toBe("lista"))
    if (result.current.busqueda.estado !== "lista") throw new Error()
    expect(result.current.busqueda.explotaciones).toHaveLength(20)
    expect(result.current.busqueda.total).toBe(137)
  })

  it("un error queda en el estado, y reintentar vuelve a pedir la misma consulta enseguida", async () => {
    let fallar = true
    server.use(
      http.get(apiUrl("/explotaciones"), () =>
        fallar
          ? HttpResponse.json({ motivo: "La búsqueda es demasiado larga." }, { status: 400 })
          : HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
      ),
    )
    const { result } = montar({ abierta: true, consulta: "" })
    await waitFor(() => expect(result.current.busqueda.estado).toBe("error"))
    if (result.current.busqueda.estado !== "error") throw new Error()
    expect(result.current.busqueda.error.motivo).toBe("La búsqueda es demasiado larga.")

    fallar = false
    act(() => result.current.reintentar())
    expect(result.current.busqueda.estado).toBe("buscando")
    await waitFor(() => expect(result.current.busqueda.estado).toBe("lista"))
    expect(listarExplotaciones).toHaveBeenCalledTimes(2)
  })

  it("cerrar cancela lo que esté en vuelo sin enseñar nada; reabrir vuelve a pedir", async () => {
    const lenta = diferido()
    servidorEco({ "": lenta.promesa })
    const { result, rerender } = montar({ abierta: true, consulta: "" })
    await waitFor(() => expect(listarExplotaciones).toHaveBeenCalledTimes(1))
    rerender({ abierta: false, consulta: "" })
    expect(senalDe("").aborted).toBe(true)
    expect(result.current.busqueda.estado).toBe("inactiva")
    lenta.abrir()
    await act(() => esperar(50))
    expect(result.current.busqueda.estado).toBe("inactiva")

    rerender({ abierta: true, consulta: "" })
    await waitFor(() => expect(result.current.busqueda.estado).toBe("lista"))
    expect(listarExplotaciones).toHaveBeenCalledTimes(2)
  })
})
