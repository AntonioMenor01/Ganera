import { describe, expect, it } from "vitest"
import {
  aErrorApi,
  ErrorApi,
  esCancelacion,
  esErrorApi,
  mensajeDeError,
  TEXTO_ERROR_RED,
  TEXTO_ERROR_SERVIDOR,
  type ContextoError,
  type TipoErrorApi,
} from "@/shared/api/errores"

const TEXTO_SUSCRIPCION =
  "Tu suscripción no permite aprobar trámites ahora mismo (trial expirado o suspendida). Actualiza tu suscripción en Facturación."

describe("ErrorApi", () => {
  it("es un Error de verdad, con tipo, status y motivo", () => {
    const error = new ErrorApi({ tipo: "conflicto", status: 409, motivo: "Versión desfasada" })
    expect(error).toBeInstanceOf(Error)
    expect(error.name).toBe("ErrorApi")
    expect(error.tipo).toBe("conflicto")
    expect(error.status).toBe(409)
    expect(error.motivo).toBe("Versión desfasada")
    expect(esErrorApi(error)).toBe(true)
    expect(esErrorApi(new Error("x"))).toBe(false)
  })

  it("un motivo vacío o en blanco se guarda como undefined", () => {
    expect(new ErrorApi({ tipo: "validacion", status: 400, motivo: "" }).motivo).toBeUndefined()
    expect(new ErrorApi({ tipo: "validacion", status: 400, motivo: "  " }).motivo).toBeUndefined()
  })

  it("aErrorApi deja pasar un ErrorApi y convierte cualquier otra cosa en desconocido", () => {
    const original = new ErrorApi({ tipo: "red" })
    expect(aErrorApi(original)).toBe(original)
    expect(aErrorApi(new Error("boom"))).toMatchObject({ tipo: "desconocido" })
    expect(aErrorApi("cadena")).toMatchObject({ tipo: "desconocido" })
    expect(aErrorApi(undefined)).toMatchObject({ tipo: "desconocido" })
  })
})

describe("cancelaciones (M5)", () => {
  it("una cancelación se marca y esCancelacion la reconoce; un error normal no", () => {
    const cancelado = new ErrorApi({ tipo: "desconocido", cancelado: true })
    expect(esCancelacion(cancelado)).toBe(true)
    expect(esCancelacion(new ErrorApi({ tipo: "red" }))).toBe(false)
    expect(esCancelacion(new Error("x"))).toBe(false)
    expect(esCancelacion(undefined)).toBe(false)
    // Aunque alguien lo pinte por error, nunca es una cadena vacía.
    expect(mensajeDeError(cancelado).trim()).not.toBe("")
  })
})

describe("mensajeDeError", () => {
  it("el motivo gana siempre, tal cual, en cualquier tipo y contexto", () => {
    const tipos: TipoErrorApi[] = [
      "no-autorizado",
      "prohibido",
      "validacion",
      "no-encontrado",
      "conflicto",
      "servidor",
      "red",
      "desconocido",
    ]
    for (const tipo of tipos) {
      const error = new ErrorApi({ tipo, status: 400, motivo: "Motivo del backend." })
      expect(mensajeDeError(error)).toBe("Motivo del backend.")
      expect(mensajeDeError(error, "aprobar-tramite")).toBe("Motivo del backend.")
    }
  })

  it("prohibido al aprobar sin motivo → el texto fijo de la suscripción (H2)", () => {
    const error = new ErrorApi({ tipo: "prohibido", status: 403 })
    expect(mensajeDeError(error, "aprobar-tramite")).toBe(TEXTO_SUSCRIPCION)
  })

  it("prohibido al aprobar con motivo → el motivo del backend tal cual, no el texto fijo", () => {
    const motivo = "Tu suscripción está suspendida: actualízala en Facturación para aprobar."
    const error = new ErrorApi({ tipo: "prohibido", status: 403, motivo })
    expect(mensajeDeError(error, "aprobar-tramite")).toBe(motivo)
    expect(mensajeDeError(error, "aprobar-tramite")).not.toBe(TEXTO_SUSCRIPCION)
  })

  it("prohibido fuera de aprobar → un texto de permiso, no el de suscripción", () => {
    const error = new ErrorApi({ tipo: "prohibido", status: 403 })
    expect(mensajeDeError(error)).not.toBe(TEXTO_SUSCRIPCION)
    expect(mensajeDeError(error)).toMatch(/permiso/i)
  })

  it("no-encontrado → texto contextual del trámite en detalle, aprobar y rechazar", () => {
    const error = new ErrorApi({ tipo: "no-encontrado", status: 404 })
    const esperado = "Este trámite ya no existe o no es de tu gestoría."
    expect(mensajeDeError(error, "detalle-tramite")).toBe(esperado)
    expect(mensajeDeError(error, "aprobar-tramite")).toBe(esperado)
    expect(mensajeDeError(error, "rechazar-tramite")).toBe(esperado)
    expect(mensajeDeError(error, "guardar-tramite")).toBe(esperado)
  })

  it("guardar-tramite: el motivo del backend tal cual y, sin motivo, su genérico", () => {
    const conMotivo = new ErrorApi({ tipo: "validacion", status: 400, motivo: "Crotal no válido." })
    expect(mensajeDeError(conMotivo, "guardar-tramite")).toBe("Crotal no válido.")
    expect(mensajeDeError(new ErrorApi({ tipo: "conflicto", status: 409 }), "guardar-tramite")).toBe(
      "No se han podido guardar los cambios del trámite. Inténtalo de nuevo.",
    )
  })

  it("no-encontrado sin contexto → un 'no encontrado' genérico", () => {
    expect(mensajeDeError(new ErrorApi({ tipo: "no-encontrado", status: 404 }))).toMatch(
      /no se ha encontrado/i,
    )
  })

  it("red y servidor → los dos textos genéricos del plan, en cualquier contexto", () => {
    expect(TEXTO_ERROR_RED).toBe(
      "No se ha podido conectar con Ganera. Inténtalo de nuevo en unos segundos.",
    )
    expect(TEXTO_ERROR_SERVIDOR).toBe(
      "Ha fallado algo en el servidor. Inténtalo de nuevo; si se repite, avísanos.",
    )
    expect(mensajeDeError(new ErrorApi({ tipo: "red" }))).toBe(TEXTO_ERROR_RED)
    expect(mensajeDeError(new ErrorApi({ tipo: "red" }), "login")).toBe(TEXTO_ERROR_RED)
    expect(mensajeDeError(new ErrorApi({ tipo: "servidor", status: 500 }))).toBe(TEXTO_ERROR_SERVIDOR)
    expect(mensajeDeError(new ErrorApi({ tipo: "servidor", status: 500 }), "listar-tramites")).toBe(
      TEXTO_ERROR_SERVIDOR,
    )
  })

  it("503 en checkout y registro → facturación no configurada (comportamiento previo)", () => {
    const error = new ErrorApi({ tipo: "servidor", status: 503 })
    const esperado = "La facturación todavía no está configurada. Vuelve a intentarlo más tarde."
    expect(mensajeDeError(error, "checkout")).toBe(esperado)
    expect(mensajeDeError(error, "registro")).toBe(esperado)
    // Fuera de esos contextos, un 503 es un fallo de servidor más.
    expect(mensajeDeError(error, "listar-tramites")).toBe(TEXTO_ERROR_SERVIDOR)
  })

  it("401 en login → el error uniforme de credenciales", () => {
    expect(mensajeDeError(new ErrorApi({ tipo: "no-autorizado", status: 401 }), "login")).toBe(
      "Email o contraseña incorrectos.",
    )
  })

  it("login ignora cualquier motivo del backend: el texto es siempre el uniforme (I1)", () => {
    const uniforme = "Email o contraseña incorrectos."
    expect(
      mensajeDeError(new ErrorApi({ tipo: "no-autorizado", status: 401, motivo: "Usuario inactivo" }), "login"),
    ).toBe(uniforme)
    // Un 400 del login tampoco puede dar pistas: genérico del contexto, no el motivo.
    expect(
      mensajeDeError(new ErrorApi({ tipo: "validacion", status: 400, motivo: "Email no registrado" }), "login"),
    ).toBe("No se ha podido iniciar sesión. Inténtalo de nuevo.")
  })

  it("registro ignora cualquier motivo del backend: siempre el mismo 400 uniforme (I1)", () => {
    const uniforme =
      "No se ha podido completar el registro con esos datos. Revisa el email y la contraseña e inténtalo de nuevo."
    expect(
      mensajeDeError(new ErrorApi({ tipo: "validacion", status: 400, motivo: "El email ya existe" }), "registro"),
    ).toBe(uniforme)
    expect(mensajeDeError(new ErrorApi({ tipo: "validacion", status: 400 }), "registro")).toBe(uniforme)
  })

  it("401 fuera del login → sesión caducada", () => {
    expect(mensajeDeError(new ErrorApi({ tipo: "no-autorizado", status: 401 }))).toMatch(/sesión/i)
  })

  it("validacion/conflicto/desconocido sin motivo → el genérico del contexto", () => {
    expect(mensajeDeError(new ErrorApi({ tipo: "validacion", status: 400 }), "aprobar-tramite")).toBe(
      "No se ha podido aprobar el trámite. Inténtalo de nuevo.",
    )
    expect(mensajeDeError(new ErrorApi({ tipo: "conflicto", status: 409 }), "rechazar-tramite")).toBe(
      "No se ha podido rechazar el trámite. Inténtalo de nuevo.",
    )
    expect(mensajeDeError(new ErrorApi({ tipo: "validacion", status: 400 }), "importar-excel")).toBe(
      "No se ha podido importar el fichero. Inténtalo de nuevo.",
    )
  })

  it("Ganaderos: genéricos propios y 404 del detalle sin revelar si existe en otra gestoría", () => {
    expect(mensajeDeError(new ErrorApi({ tipo: "desconocido" }), "listar-ganaderos")).toBe(
      "No se han podido cargar los ganaderos. Inténtalo de nuevo.",
    )
    expect(mensajeDeError(new ErrorApi({ tipo: "desconocido" }), "detalle-ganadero")).toBe(
      "No se ha podido cargar el ganadero. Inténtalo de nuevo.",
    )
    expect(mensajeDeError(new ErrorApi({ tipo: "no-encontrado", status: 404 }), "detalle-ganadero")).toBe(
      "Este ganadero no existe o no es de tu gestoría.",
    )
  })

  it("animales de una explotación: genérico propio y 404 de explotación, sin distinguir gestoría", () => {
    expect(mensajeDeError(new ErrorApi({ tipo: "desconocido" }), "animales-explotacion")).toBe(
      "No se han podido cargar los animales. Inténtalo de nuevo.",
    )
    expect(
      mensajeDeError(new ErrorApi({ tipo: "no-encontrado", status: 404 }), "animales-explotacion"),
    ).toBe("Esta explotación no existe o no es de tu gestoría.")
  })

  it("cada contexto da un texto no vacío para cada tipo sin motivo", () => {
    const contextos: (ContextoError | undefined)[] = [
      undefined,
      "login",
      "registro",
      "checkout",
      "suscripcion",
      "importar-excel",
      "listar-explotaciones",
      "listar-tramites",
      "detalle-tramite",
      "aprobar-tramite",
      "rechazar-tramite",
      "guardar-tramite",
      "listar-ganaderos",
      "detalle-ganadero",
      "animales-explotacion",
    ]
    const tipos: TipoErrorApi[] = [
      "no-autorizado",
      "prohibido",
      "validacion",
      "no-encontrado",
      "conflicto",
      "servidor",
      "red",
      "desconocido",
    ]
    for (const contexto of contextos) {
      for (const tipo of tipos) {
        const texto = mensajeDeError(new ErrorApi({ tipo }), contexto)
        expect(texto.trim(), `${tipo} en ${contexto}`).not.toBe("")
      }
    }
  })

  it("cualquier cosa que no sea ErrorApi → un texto de reserva no vacío", () => {
    expect(mensajeDeError(new Error("boom")).trim()).not.toBe("")
    expect(mensajeDeError(undefined).trim()).not.toBe("")
    expect(mensajeDeError(null, "detalle-tramite")).toBe(
      "No se ha podido cargar el detalle del trámite. Inténtalo de nuevo.",
    )
  })
})
