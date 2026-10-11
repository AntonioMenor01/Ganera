import { http, HttpResponse } from "msw"
import { describe, expect, it } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { ErrorApi } from "@/shared/api/errores"
import {
  actualizarTramite,
  aprobarTramite,
  desmarcarHechoEnOvz,
  marcarHechoEnOvz,
  obtenerDetalleTramite,
  rechazarTramite,
  responderPorWhatsapp,
} from "./api"

const DETALLE = {
  id: 7,
  tipoTramite: "ALTA_NACIMIENTO",
  estado: "PENDIENTE_REVISION",
  motivoError: null,
  explotacionId: 3,
  explotacionCodigoRega: "ES123",
  explotacionNombre: "La Dehesa",
  mensajeOriginal: "alta del 1234",
  crotales: [],
  version: 4,
  origen: null,
  estadoExtraccion: null,
  crotalesDescartados: 0,
  hechoEnOvzEn: null,
  hechoEnOvzPorNombre: null,
  whatsapp: null,
}

const RESPUESTA_LISTA = {
  id: 7,
  explotacionId: 3,
  tipoTramite: "ALTA_NACIMIENTO",
  estado: "APROBADO",
  motivoError: null,
  crotales: [],
  version: 5,
  origen: null,
  estadoExtraccion: null,
  crotalesDescartados: 0,
  hechoEnOvzEn: null,
  hechoEnOvzPorNombre: null,
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
    await actualizarTramite(7, { version: 2, explotacionId: 9, tipoTramite: "BAJA_MUERTE" })
    expect(cuerpo).toEqual({ version: 2, explotacionId: 9, tipoTramite: "BAJA_MUERTE" })
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
  it("envía {version} en el cuerpo (obligatoria desde el mini-prompt tras A2, H4)", async () => {
    let cuerpo: unknown
    server.use(
      http.post(apiUrl("/tramites/7/rechazar"), async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json({ ...RESPUESTA_LISTA, estado: "RECHAZADO" })
      }),
    )
    const tramite = await rechazarTramite(7, 4)
    expect(cuerpo).toEqual({ version: 4 })
    expect(tramite.estado).toBe("RECHAZADO")
  })

  it("la versión 0 también se envía (no se toma por ausente)", async () => {
    let cuerpo: unknown
    server.use(
      http.post(apiUrl("/tramites/7/rechazar"), async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json({ ...RESPUESTA_LISTA, estado: "RECHAZADO" })
      }),
    )
    await rechazarTramite(7, 0)
    expect(cuerpo).toEqual({ version: 0 })
  })
})

describe("marcarHechoEnOvz (POST /tramites/{id}/marcar-hecho-ovz)", () => {
  it("envía solo {version} (quién marca sale del JWT) y devuelve el trámite con quién y cuándo", async () => {
    let cuerpo: unknown
    server.use(
      http.post(apiUrl("/tramites/7/marcar-hecho-ovz"), async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json({
          ...RESPUESTA_LISTA,
          estado: "HECHO_EN_OVZ",
          hechoEnOvzEn: "2026-10-07T10:30:00Z",
          hechoEnOvzPorNombre: "Ana Pérez",
        })
      }),
    )
    const tramite = await marcarHechoEnOvz(7, 4)
    expect(cuerpo).toEqual({ version: 4 })
    expect(tramite.estado).toBe("HECHO_EN_OVZ")
    expect(tramite.hechoEnOvzEn).toBe("2026-10-07T10:30:00Z")
    expect(tramite.hechoEnOvzPorNombre).toBe("Ana Pérez")
  })

  it("la versión 0 también se envía (no se toma por ausente)", async () => {
    let cuerpo: unknown
    server.use(
      http.post(apiUrl("/tramites/7/marcar-hecho-ovz"), async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json({ ...RESPUESTA_LISTA, estado: "HECHO_EN_OVZ" })
      }),
    )
    await marcarHechoEnOvz(7, 0)
    expect(cuerpo).toEqual({ version: 0 })
  })

  it("un 409 llega como ErrorApi de conflicto con su motivo", async () => {
    server.use(
      http.post(apiUrl("/tramites/7/marcar-hecho-ovz"), () =>
        HttpResponse.json({ motivo: "Este trámite ya está marcado como hecho en OVZ." }, { status: 409 }),
      ),
    )
    const error = await marcarHechoEnOvz(7, 4).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ErrorApi)
    expect((error as ErrorApi).tipo).toBe("conflicto")
    expect((error as ErrorApi).motivo).toBe("Este trámite ya está marcado como hecho en OVZ.")
  })
})

describe("desmarcarHechoEnOvz (POST /tramites/{id}/desmarcar-hecho-ovz)", () => {
  it("envía {version} y devuelve el trámite otra vez aprobado, sin quién ni cuándo", async () => {
    let cuerpo: unknown
    server.use(
      http.post(apiUrl("/tramites/7/desmarcar-hecho-ovz"), async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json(RESPUESTA_LISTA)
      }),
    )
    const tramite = await desmarcarHechoEnOvz(7, 4)
    expect(cuerpo).toEqual({ version: 4 })
    expect(tramite.estado).toBe("APROBADO")
    expect(tramite.hechoEnOvzEn).toBeNull()
    expect(tramite.hechoEnOvzPorNombre).toBeNull()
  })

  it("la versión 0 también se envía (no se toma por ausente)", async () => {
    let cuerpo: unknown
    server.use(
      http.post(apiUrl("/tramites/7/desmarcar-hecho-ovz"), async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json(RESPUESTA_LISTA)
      }),
    )
    await desmarcarHechoEnOvz(7, 0)
    expect(cuerpo).toEqual({ version: 0 })
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

describe("responderPorWhatsapp (POST /tramites/{id}/respuestas-whatsapp, B2 reducido)", () => {
  const RESPUESTA = {
    id: 41,
    texto: "Hola, Juan.\n— Gestoría X",
    estado: "ENVIADO",
    enviadoPorNombre: "Ana Pérez",
    enviadoEn: "2031-03-10T09:00:00Z",
    codigoError: null,
    motivoError: null,
  }
  const CLAVE = "3f1c2a9e-1b2c-4d5e-8f90-123456789abc"

  it.each([201, 200])("envía {texto, claveIdempotencia} y un %s es éxito", async (status) => {
    let cuerpo: unknown
    server.use(
      http.post(apiUrl("/tramites/7/respuestas-whatsapp"), async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json(RESPUESTA, { status })
      }),
    )
    const respuesta = await responderPorWhatsapp(7, "Hola, Juan.\n— Gestoría X", CLAVE)
    expect(cuerpo).toEqual({ texto: "Hola, Juan.\n— Gestoría X", claveIdempotencia: CLAVE })
    expect(Object.keys(cuerpo as object)).toEqual(["texto", "claveIdempotencia"])
    expect(respuesta).toEqual(RESPUESTA)
  })

  it("el 503 de «no configurado» trae su motivo (solo en este endpoint: el servidor no guardó nada)", async () => {
    const motivo = "El envío por WhatsApp no está configurado. Ponte en contacto con Ganera."
    server.use(
      http.post(apiUrl("/tramites/7/respuestas-whatsapp"), () => HttpResponse.json({ motivo }, { status: 503 })),
    )
    const error = await responderPorWhatsapp(7, "x", CLAVE).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ErrorApi)
    expect(error).toMatchObject({ tipo: "servidor", status: 503, motivo })
  })

  it("un 503 de un gateway (texto plano) o sin cuerpo no trae motivo: es un fallo incierto", async () => {
    for (const respuesta of [
      () => HttpResponse.text("no healthy upstream", { status: 503 }),
      () => new HttpResponse(null, { status: 503 }),
    ]) {
      server.use(http.post(apiUrl("/tramites/7/respuestas-whatsapp"), respuesta))
      const error = (await responderPorWhatsapp(7, "x", CLAVE).catch((e: unknown) => e)) as ErrorApi
      expect(error).toBeInstanceOf(ErrorApi)
      expect(error.tipo).toBe("servidor")
      expect(error.motivo).toBeUndefined()
    }
  })

  it("un 500 con {motivo} sigue sin enseñarlo (M1)", async () => {
    server.use(
      http.post(apiUrl("/tramites/7/respuestas-whatsapp"), () =>
        HttpResponse.json({ motivo: "detalle interno" }, { status: 500 }),
      ),
    )
    const error = (await responderPorWhatsapp(7, "x", CLAVE).catch((e: unknown) => e)) as ErrorApi
    expect(error.tipo).toBe("servidor")
    expect(error.motivo).toBeUndefined()
  })

  it("un 409 llega como conflicto con el motivo", async () => {
    server.use(
      http.post(apiUrl("/tramites/7/respuestas-whatsapp"), () =>
        HttpResponse.json({ motivo: "En este estado no se puede responder por WhatsApp." }, { status: 409 }),
      ),
    )
    const error = (await responderPorWhatsapp(7, "x", CLAVE).catch((e: unknown) => e)) as ErrorApi
    expect(error).toMatchObject({ tipo: "conflicto", motivo: "En este estado no se puede responder por WhatsApp." })
  })
})
