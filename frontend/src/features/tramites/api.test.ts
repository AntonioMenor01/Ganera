import { http, HttpResponse } from "msw"
import { describe, expect, it } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { ErrorApi } from "@/shared/api/errores"
import { actualizarTramite, aprobarTramite, obtenerDetalleTramite, rechazarTramite } from "./api"

const DETALLE = {
  id: 7,
  tipoTramite: "ALTA",
  estado: "PENDIENTE_REVISION",
  motivoError: null,
  explotacionId: 3,
  explotacionCodigoRega: "ES123",
  explotacionNombre: "La Dehesa",
  mensajeOriginal: "alta del 1234",
  crotales: [],
  version: 4,
}

const RESPUESTA_LISTA = {
  id: 7,
  explotacionId: 3,
  tipoTramite: "ALTA",
  estado: "APROBADO",
  motivoError: null,
  crotales: [],
  version: 5,
}

/** Cuerpo crudo (texto) de la petición, para distinguir "sin cuerpo" de "{}". */
async function cuerpoCrudo(request: Request): Promise<string> {
  return await request.text()
}

describe("actualizarTramite (PATCH /tramites/{id})", () => {
  it("envía la versión y solo los campos presentes, y devuelve el detalle", async () => {
    let cuerpo: unknown
    let metodo = ""
    server.use(
      http.patch(apiUrl("/tramites/7"), async ({ request }) => {
        metodo = request.method
        cuerpo = await request.json()
        return HttpResponse.json({ ...DETALLE, version: 5 })
      }),
    )

    const detalle = await actualizarTramite(7, { version: 4, crotales: ["1234"] })

    expect(metodo).toBe("PATCH")
    expect(cuerpo).toEqual({ version: 4, crotales: ["1234"] })
    expect(Object.keys(cuerpo as object)).toEqual(["version", "crotales"])
    expect(detalle.version).toBe(5)
    expect(detalle.explotacionCodigoRega).toBe("ES123")
  })

  it("una lista vacía de crotales se envía (los quita todos), no se omite", async () => {
    let cuerpo: unknown
    server.use(
      http.patch(apiUrl("/tramites/7"), async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json(DETALLE)
      }),
    )
    await actualizarTramite(7, { version: 0, crotales: [] })
    expect(cuerpo).toEqual({ version: 0, crotales: [] })
  })

  it("explotación y tipo se envían cuando vienen", async () => {
    let cuerpo: unknown
    server.use(
      http.patch(apiUrl("/tramites/7"), async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json(DETALLE)
      }),
    )
    await actualizarTramite(7, { version: 2, explotacionId: 9, tipoTramite: "BAJA" })
    expect(cuerpo).toEqual({ version: 2, explotacionId: 9, tipoTramite: "BAJA" })
  })

  it("un 400 llega como ErrorApi de validación con el motivo", async () => {
    server.use(
      http.patch(apiUrl("/tramites/7"), () =>
        HttpResponse.json({ motivo: "Indica al menos los últimos 4 dígitos." }, { status: 400 }),
      ),
    )
    const error = await actualizarTramite(7, { version: 0, crotales: ["12"] }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ErrorApi)
    expect((error as ErrorApi).tipo).toBe("validacion")
    expect((error as ErrorApi).motivo).toBe("Indica al menos los últimos 4 dígitos.")
  })
})

describe("aprobarTramite (POST /tramites/{id}/aprobar)", () => {
  it("envía {version} en el cuerpo", async () => {
    let cuerpo: unknown
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json(RESPUESTA_LISTA)
      }),
    )
    const tramite = await aprobarTramite(7, 4)
    expect(cuerpo).toEqual({ version: 4 })
    expect(tramite.estado).toBe("APROBADO")
  })

  it("la versión 0 también se envía (no se toma por ausente)", async () => {
    let cuerpo: unknown
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json(RESPUESTA_LISTA)
      }),
    )
    await aprobarTramite(7, 0)
    expect(cuerpo).toEqual({ version: 0 })
  })
})

describe("rechazarTramite (POST /tramites/{id}/rechazar)", () => {
  it("no envía cuerpo (H4: el backend no admite version al rechazar)", async () => {
    let crudo: string | null = null
    server.use(
      http.post(apiUrl("/tramites/7/rechazar"), async ({ request }) => {
        crudo = await cuerpoCrudo(request)
        return HttpResponse.json({ ...RESPUESTA_LISTA, estado: "RECHAZADO" })
      }),
    )
    const tramite = await rechazarTramite(7)
    expect(crudo).toBe("")
    expect(tramite.estado).toBe("RECHAZADO")
  })
})

describe("obtenerDetalleTramite", () => {
  it("acepta un AbortSignal y una cancelación llega como ErrorApi cancelado", async () => {
    server.use(http.get(apiUrl("/tramites/7"), () => HttpResponse.json(DETALLE)))
    const controlador = new AbortController()
    controlador.abort()
    const error = await obtenerDetalleTramite(7, controlador.signal).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ErrorApi)
    expect((error as ErrorApi).cancelado).toBe(true)
  })
})
