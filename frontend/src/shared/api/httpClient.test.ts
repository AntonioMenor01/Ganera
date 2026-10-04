import { delay, http, HttpResponse } from "msw"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { httpClient } from "@/shared/api/httpClient"
import { ErrorApi, esCancelacion, mensajeDeError, TEXTO_ERROR_SERVIDOR } from "@/shared/api/errores"
import { setAuthToken, setUnauthorizedHandler } from "@/shared/api/authSession"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"

/** Lanza la petición y devuelve lo que rechaza el cliente (falla si no rechaza). */
async function capturarError(peticion: Promise<unknown>): Promise<unknown> {
  try {
    await peticion
  } catch (error) {
    return error
  }
  throw new Error("La petición debía fallar y no ha fallado")
}

describe("httpClient: normalización de errores a ErrorApi", () => {
  it("400 con {motivo} JSON → validacion con ese motivo", async () => {
    server.use(
      http.get(apiUrl("/x"), () =>
        HttpResponse.json({ motivo: "Campo de ordenación no permitido." }, { status: 400 }),
      ),
    )
    const error = await capturarError(httpClient.get("/x"))
    expect(error).toBeInstanceOf(ErrorApi)
    expect(error).toMatchObject({
      tipo: "validacion",
      status: 400,
      motivo: "Campo de ordenación no permitido.",
    })
  })

  // Defensa: hoy ningún endpoint responde en texto plano (el importador ya manda `{motivo}`).
  it("400 con cuerpo de texto plano → el texto es el motivo", async () => {
    server.use(
      http.post(apiUrl("/ruta-de-prueba"), () =>
        HttpResponse.text("Falta la hoja obligatoria \"Animales\"", { status: 400 }),
      ),
    )
    const error = await capturarError(httpClient.post("/ruta-de-prueba", {}))
    expect(error).toMatchObject({
      tipo: "validacion",
      status: 400,
      motivo: "Falta la hoja obligatoria \"Animales\"",
    })
  })

  it("{mensaje} no se lee como motivo: solo `{motivo}` es el formato del backend", async () => {
    server.use(
      http.post(apiUrl("/ruta-de-prueba"), () =>
        HttpResponse.json({ mensaje: "El email ya existe" }, { status: 400 }),
      ),
    )
    const error = await capturarError(httpClient.post("/ruta-de-prueba", {}))
    expect(error).toMatchObject({ tipo: "validacion", status: 400 })
    expect((error as ErrorApi).motivo).toBeUndefined()
  })

  it("5xx con texto plano (traza de Java) → sin motivo, texto genérico de servidor (M1)", async () => {
    server.use(
      http.get(apiUrl("/x"), () =>
        HttpResponse.text("java.lang.NullPointerException at com.ganera...", { status: 500 }),
      ),
    )
    const error = await capturarError(httpClient.get("/x"))
    expect(error).toMatchObject({ tipo: "servidor", status: 500 })
    expect((error as ErrorApi).motivo).toBeUndefined()
    expect(mensajeDeError(error, "listar-tramites")).toBe(TEXTO_ERROR_SERVIDOR)
  })

  it("503 de un gateway con texto plano → no pisa el texto del contexto (M1)", async () => {
    server.use(
      http.post(apiUrl("/gestorias/registro"), () =>
        HttpResponse.text("no healthy upstream", { status: 503 }),
      ),
    )
    const error = await capturarError(httpClient.post("/gestorias/registro"))
    expect((error as ErrorApi).motivo).toBeUndefined()
    expect(mensajeDeError(error, "registro")).toBe(
      "La facturación todavía no está configurada. Vuelve a intentarlo más tarde.",
    )
    // "registro" ignora el motivo, así que la línea anterior pasaría aunque el interceptor leyera
    // el texto plano. Con un contexto que sí enseña el motivo, ese texto acabaría en pantalla.
    expect(mensajeDeError(error, "suscripcion")).toBe(TEXTO_ERROR_SERVIDOR)
    expect(mensajeDeError(error, "suscripcion")).not.toContain("no healthy upstream")
  })

  it("5xx con {motivo} JSON → tampoco se lee (solo 4xx traen motivo para el usuario) (M1)", async () => {
    server.use(
      http.get(apiUrl("/x"), () => HttpResponse.json({ motivo: "detalle interno" }, { status: 500 })),
    )
    const error = await capturarError(httpClient.get("/x"))
    expect((error as ErrorApi).motivo).toBeUndefined()
  })

  it("una petición cancelada se marca como cancelación, no como fallo (M5)", async () => {
    server.use(http.get(apiUrl("/lento"), async () => {
        await delay("infinite")
        return HttpResponse.json({})
      }))
    const controlador = new AbortController()
    const peticion = capturarError(httpClient.get("/lento", { signal: controlador.signal }))
    controlador.abort()
    const error = await peticion
    expect(error).toBeInstanceOf(ErrorApi)
    expect(esCancelacion(error)).toBe(true)
  })

  it("403 sin cuerpo → prohibido sin motivo", async () => {
    server.use(http.post(apiUrl("/tramites/1/aprobar"), () => new HttpResponse(null, { status: 403 })))
    const error = await capturarError(httpClient.post("/tramites/1/aprobar"))
    expect(error).toBeInstanceOf(ErrorApi)
    expect(error).toMatchObject({ tipo: "prohibido", status: 403 })
    expect((error as ErrorApi).motivo).toBeUndefined()
  })

  it("404 sin cuerpo → no-encontrado sin motivo", async () => {
    server.use(http.get(apiUrl("/tramites/9"), () => new HttpResponse(null, { status: 404 })))
    const error = await capturarError(httpClient.get("/tramites/9"))
    expect(error).toMatchObject({ tipo: "no-encontrado", status: 404 })
    expect((error as ErrorApi).motivo).toBeUndefined()
  })

  it("409 con {motivo} → conflicto con ese motivo", async () => {
    server.use(
      http.post(apiUrl("/tramites/1/aprobar"), () =>
        HttpResponse.json({ motivo: "La versión del trámite ha cambiado." }, { status: 409 }),
      ),
    )
    const error = await capturarError(httpClient.post("/tramites/1/aprobar", { version: 1 }))
    expect(error).toMatchObject({
      tipo: "conflicto",
      status: 409,
      motivo: "La versión del trámite ha cambiado.",
    })
  })

  it("500 con el JSON de error por defecto de Spring → servidor, sin motivo", async () => {
    server.use(
      http.get(apiUrl("/x"), () =>
        HttpResponse.json(
          { timestamp: "2026-09-28T10:00:00Z", status: 500, error: "Internal Server Error", path: "/x" },
          { status: 500 },
        ),
      ),
    )
    const error = await capturarError(httpClient.get("/x"))
    expect(error).toMatchObject({ tipo: "servidor", status: 500 })
    expect((error as ErrorApi).motivo).toBeUndefined()
  })

  it("503 sin cuerpo → servidor con status 503", async () => {
    server.use(http.post(apiUrl("/gestorias/registro"), () => new HttpResponse(null, { status: 503 })))
    const error = await capturarError(httpClient.post("/gestorias/registro"))
    expect(error).toMatchObject({ tipo: "servidor", status: 503 })
  })

  it("error de red (sin respuesta) → red, sin status ni motivo", async () => {
    server.use(http.get(apiUrl("/x"), () => HttpResponse.error()))
    const error = await capturarError(httpClient.get("/x"))
    expect(error).toBeInstanceOf(ErrorApi)
    expect(error).toMatchObject({ tipo: "red" })
    expect((error as ErrorApi).status).toBeUndefined()
    expect((error as ErrorApi).motivo).toBeUndefined()
  })

  it("un status no contemplado (418) → desconocido con su status", async () => {
    server.use(http.get(apiUrl("/x"), () => new HttpResponse(null, { status: 418 })))
    const error = await capturarError(httpClient.get("/x"))
    expect(error).toMatchObject({ tipo: "desconocido", status: 418 })
  })

  it.each([
    ["texto vacío", () => HttpResponse.text("", { status: 400 })],
    ["texto en blanco", () => HttpResponse.text("   \n ", { status: 400 })],
    ["{motivo: ''}", () => HttpResponse.json({ motivo: "" }, { status: 400 })],
    ["{motivo: '  '}", () => HttpResponse.json({ motivo: "  " }, { status: 400 })],
    ["{motivo: null}", () => HttpResponse.json({ motivo: null }, { status: 400 })],
    ["{motivo: 42}", () => HttpResponse.json({ motivo: 42 }, { status: 400 })],
    [
      "página HTML de un proxy",
      () =>
        new HttpResponse("<html><body>Bad Request</body></html>", {
          status: 400,
          headers: { "Content-Type": "text/html" },
        }),
    ],
  ])("cuerpo sin motivo útil (%s) → sin motivo y con un texto de reserva", async (_nombre, respuesta) => {
    server.use(http.post(apiUrl("/x"), respuesta))
    const error = await capturarError(httpClient.post("/x"))
    expect(error).toMatchObject({ tipo: "validacion", status: 400 })
    expect((error as ErrorApi).motivo).toBeUndefined()
    const texto = mensajeDeError(error)
    expect(texto.trim()).not.toBe("")
  })

  it("recorta los espacios del motivo", async () => {
    server.use(http.post(apiUrl("/x"), () => HttpResponse.text("  Falta la hoja  \n", { status: 400 })))
    const error = await capturarError(httpClient.post("/x"))
    expect((error as ErrorApi).motivo).toBe("Falta la hoja")
  })
})

describe("httpClient: 401 y 403 frente a la sesión", () => {
  const alCaducar = vi.fn()

  beforeEach(() => {
    alCaducar.mockReset()
    setUnauthorizedHandler(alCaducar)
  })

  afterEach(() => {
    setUnauthorizedHandler(null)
  })

  it("401 en un endpoint normal → no-autorizado y avisa a la sesión", async () => {
    server.use(http.get(apiUrl("/tramites"), () => new HttpResponse(null, { status: 401 })))
    const error = await capturarError(httpClient.get("/tramites"))
    expect(error).toMatchObject({ tipo: "no-autorizado", status: 401 })
    expect(alCaducar).toHaveBeenCalledTimes(1)
  })

  it("401 en POST /auth/login → no-autorizado pero NO avisa a la sesión (H11)", async () => {
    server.use(http.post(apiUrl("/auth/login"), () => new HttpResponse(null, { status: 401 })))
    const error = await capturarError(httpClient.post("/auth/login", { email: "a@b.c", password: "x" }))
    expect(error).toMatchObject({ tipo: "no-autorizado", status: 401 })
    expect(alCaducar).not.toHaveBeenCalled()
  })

  it("401 en /auth/me sí avisa a la sesión (solo se exceptúa el login)", async () => {
    server.use(http.get(apiUrl("/auth/me"), () => new HttpResponse(null, { status: 401 })))
    await capturarError(httpClient.get("/auth/me"))
    expect(alCaducar).toHaveBeenCalledTimes(1)
  })

  it("401 de una petición hecha con un token que ya no es el actual NO avisa (M1)", async () => {
    let liberar: () => void = () => {}
    const puedeResponder = new Promise<void>((resolver) => {
      liberar = resolver
    })
    let marcarRecibida: (autorizacion: string | null) => void = () => {}
    const recibida = new Promise<string | null>((resolver) => {
      marcarRecibida = resolver
    })
    server.use(
      http.get(apiUrl("/auth/me"), async ({ request }) => {
        marcarRecibida(request.headers.get("Authorization"))
        await puedeResponder
        return new HttpResponse(null, { status: 401 })
      }),
    )
    setAuthToken("tok-viejo")
    const peticion = capturarError(httpClient.get("/auth/me"))
    expect(await recibida).toBe("Bearer tok-viejo")
    // Ya en vuelo, un login deja otro token: el 401 tardío es del token viejo.
    setAuthToken("tok-nuevo")
    liberar()
    expect(await peticion).toMatchObject({ tipo: "no-autorizado", status: 401 })
    expect(alCaducar).not.toHaveBeenCalled()
  })

  it("401 de una petición hecha con el token actual sí avisa", async () => {
    server.use(http.get(apiUrl("/tramites"), () => new HttpResponse(null, { status: 401 })))
    setAuthToken("tok-actual")
    await capturarError(httpClient.get("/tramites"))
    expect(alCaducar).toHaveBeenCalledTimes(1)
  })

  it("403 nunca cierra la sesión", async () => {
    server.use(http.post(apiUrl("/tramites/1/aprobar"), () => new HttpResponse(null, { status: 403 })))
    await capturarError(httpClient.post("/tramites/1/aprobar"))
    expect(alCaducar).not.toHaveBeenCalled()
  })

  it("mantiene la cabecera Bearer del interceptor de petición", async () => {
    let autorizacion: string | null = null
    server.use(
      http.get(apiUrl("/auth/me"), ({ request }) => {
        autorizacion = request.headers.get("Authorization")
        return HttpResponse.json({})
      }),
    )
    setAuthToken("token-de-prueba")
    await httpClient.get("/auth/me")
    expect(autorizacion).toBe("Bearer token-de-prueba")
  })
})
