import { act, renderHook, waitFor } from "@testing-library/react"
import { http, HttpResponse } from "msw"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { apiUrl } from "@/test/apiBaseUrl"
import { server } from "@/test/server"
import { setUnauthorizedHandler } from "@/shared/api/authSession"
import { TEXTO_ERROR_RED, TEXTO_ERROR_SERVIDOR } from "@/shared/api/errores"
import { obtenerDetalleTramite } from "./api"
import type { Tramite, TramiteCrotal, TramiteDetalle } from "./types"
import { useRevisionTramite } from "./useRevisionTramite"

// La API real, envuelta solo para ver el AbortSignal que el hook pasa al cargar el detalle (el
// signal de la request de MSW no refleja el abort de XHR en jsdom). Las peticiones HTTP, el
// interceptor y ErrorApi siguen siendo los reales.
vi.mock("./api", async (importOriginal) => {
  const real = await importOriginal<typeof import("./api")>()
  return { ...real, obtenerDetalleTramite: vi.fn(real.obtenerDetalleTramite) }
})

beforeEach(() => {
  vi.mocked(obtenerDetalleTramite).mockClear()
})

const TEXTO_SUSCRIPCION =
  "Tu suscripción no permite aprobar trámites ahora mismo (trial expirado o suspendida). Actualiza tu suscripción en Facturación."
const TEXTO_NO_EXISTE = "Este trámite ya no existe o no es de tu gestoría."

function crotal(crotalIndicado: string, parcial: Partial<TramiteCrotal> = {}): TramiteCrotal {
  return {
    crotalIndicado,
    crotal: crotalIndicado,
    animalId: null,
    enInventario: false,
    resolucion: "NO_ENCONTRADO",
    ...parcial,
  }
}

function detalle(parcial: Partial<TramiteDetalle> = {}): TramiteDetalle {
  return {
    id: 7,
    tipoTramite: "ALTA",
    estado: "PENDIENTE_REVISION",
    motivoError: null,
    explotacionId: 3,
    explotacionCodigoRega: "ES123",
    explotacionNombre: "La Dehesa",
    mensajeOriginal: "alta del 1234",
    crotales: [crotal("1234")],
    version: 4,
    ...parcial,
  }
}

/** Lo que devuelven aprobar/rechazar: el DTO del listado (`TramiteResponse`), con la etiqueta de
 * la explotación desde el mini-prompt tras A2 (m2 de la revisión de T3). */
function respuestaLista(d: TramiteDetalle) {
  return {
    id: d.id,
    explotacionId: d.explotacionId,
    explotacionCodigoRega: d.explotacionCodigoRega,
    explotacionNombre: d.explotacionNombre,
    tipoTramite: d.tipoTramite,
    estado: d.estado,
    motivoError: d.motivoError,
    crotales: d.crotales,
    version: d.version,
  } satisfies Tramite
}

function diferido() {
  let liberar!: () => void
  const promesa = new Promise<void>((resolve) => {
    liberar = resolve
  })
  return { promesa, liberar }
}

/** GET /tramites/7 que devuelve, en orden, las respuestas dadas (la última se repite). */
function detallesEnOrden(...respuestas: (() => Response)[]) {
  let llamadas = 0
  server.use(
    http.get(apiUrl("/tramites/7"), () => {
      const respuesta = respuestas[Math.min(llamadas, respuestas.length - 1)]
      llamadas += 1
      return respuesta()
    }),
  )
  return { llamadas: () => llamadas }
}

async function montar(tramiteId: number | null = 7) {
  const onCambiado = vi.fn()
  const hook = renderHook(({ id }) => useRevisionTramite(id, { onCambiado }), {
    initialProps: { id: tramiteId },
  })
  if (tramiteId !== null) {
    await waitFor(() => expect(hook.result.current.carga.estado).not.toBe("cargando"))
  }
  return { ...hook, onCambiado }
}

// ---------------------------------------------------------------------------------------------

describe("useRevisionTramite: carga del detalle", () => {
  it("sin trámite está inactivo y no pide nada", async () => {
    const { result } = await montar(null)
    expect(result.current.carga.estado).toBe("inactivo")
    expect(result.current.detalle).toBeNull()
    expect(result.current.editable).toBe(false)
    expect(obtenerDetalleTramite).not.toHaveBeenCalled()
  })

  it("carga, inicializa el formulario y expone la explotación asignada", async () => {
    detallesEnOrden(() => HttpResponse.json(detalle({ crotales: [crotal("1234"), crotal("ES010000005678")] })))
    const onCambiado = vi.fn()
    const { result } = renderHook(() => useRevisionTramite(7, { onCambiado }))
    expect(result.current.carga.estado).toBe("cargando")
    await waitFor(() => expect(result.current.carga.estado).toBe("listo"))
    expect(result.current.detalle?.id).toBe(7)
    expect(result.current.formulario).toEqual({
      explotacionId: 3,
      tipoTramite: "ALTA",
      crotales: ["1234", "ES010000005678"],
    })
    expect(result.current.explotacionAsignada).toEqual({ id: 3, codigoRega: "ES123", nombre: "La Dehesa" })
    expect(result.current.sucio).toBe(false)
    expect(result.current.aviso).toBeNull()
  })

  it("sin explotación asignada, explotacionAsignada es null", async () => {
    detallesEnOrden(() =>
      HttpResponse.json(detalle({ explotacionId: null, explotacionCodigoRega: null, explotacionNombre: null })),
    )
    const { result } = await montar()
    expect(result.current.explotacionAsignada).toBeNull()
  })

  it("un fallo es un error visible con su texto, y reintentarCarga vuelve a pedir", async () => {
    const get = detallesEnOrden(
      () => new HttpResponse(null, { status: 500 }),
      () => HttpResponse.json(detalle()),
    )
    const { result } = await montar()
    const carga = result.current.carga
    expect(carga.estado).toBe("error")
    if (carga.estado !== "error") throw new Error("debería ser error")
    expect(carga.mensaje).toBe(TEXTO_ERROR_SERVIDOR)
    expect(result.current.detalle).toBeNull()

    act(() => result.current.reintentarCarga())
    expect(result.current.carga.estado).toBe("cargando")
    await waitFor(() => expect(result.current.carga.estado).toBe("listo"))
    expect(get.llamadas()).toBe(2)
  })

  it("un 404 es el estado no-encontrado con el texto del trámite", async () => {
    detallesEnOrden(() => new HttpResponse(null, { status: 404 }))
    const { result } = await montar()
    expect(result.current.carga).toEqual({ estado: "no-encontrado", mensaje: TEXTO_NO_EXISTE })
    expect(result.current.editable).toBe(false)
  })

  it("al cambiar de id se aborta la carga anterior y su respuesta nunca se muestra", async () => {
    const lento = diferido()
    const lento8 = diferido()
    server.use(
      http.get(apiUrl("/tramites/7"), async () => {
        await lento.promesa
        return HttpResponse.json(detalle({ id: 7, tipoTramite: "BAJA" }))
      }),
      http.get(apiUrl("/tramites/8"), async () => {
        await lento8.promesa
        return HttpResponse.json(detalle({ id: 8, tipoTramite: "CENSO" }))
      }),
    )
    const onCambiado = vi.fn()
    const { result, rerender } = renderHook(({ id }) => useRevisionTramite(id, { onCambiado }), {
      initialProps: { id: 7 as number | null },
    })
    const senal7 = vi.mocked(obtenerDetalleTramite).mock.calls[0][1] as AbortSignal
    rerender({ id: 8 })
    expect(senal7.aborted).toBe(true)
    // La cancelación de la carga de 7 no es un error: mientras llega 8, sigue "cargando".
    await new Promise((r) => setTimeout(r, 30))
    expect(result.current.carga.estado).toBe("cargando")
    expect(result.current.detalle).toBeNull()
    lento8.liberar()
    await waitFor(() => expect(result.current.detalle?.id).toBe(8))

    lento.liberar()
    await new Promise((r) => setTimeout(r, 30))
    expect(result.current.detalle?.id).toBe(8)
    expect(result.current.formulario.tipoTramite).toBe("CENSO")
    // Una cancelación nunca es un error.
    expect(result.current.carga.estado).toBe("listo")
  })

  it("al desmontar se aborta la carga en curso", async () => {
    const lento = diferido()
    server.use(
      http.get(apiUrl("/tramites/7"), async () => {
        await lento.promesa
        return HttpResponse.json(detalle())
      }),
    )
    const { unmount } = renderHook(() => useRevisionTramite(7, { onCambiado: vi.fn() }))
    const senal = vi.mocked(obtenerDetalleTramite).mock.calls[0][1] as AbortSignal
    expect(senal.aborted).toBe(false)
    unmount()
    expect(senal.aborted).toBe(true)
    lento.liberar()
  })

  it("al pasar a null vuelve a inactivo y aborta", async () => {
    const lento = diferido()
    server.use(
      http.get(apiUrl("/tramites/7"), async () => {
        await lento.promesa
        return HttpResponse.json(detalle())
      }),
    )
    const { result, rerender } = renderHook(({ id }) => useRevisionTramite(id, { onCambiado: vi.fn() }), {
      initialProps: { id: 7 as number | null },
    })
    const senal = vi.mocked(obtenerDetalleTramite).mock.calls[0][1] as AbortSignal
    rerender({ id: null })
    expect(senal.aborted).toBe(true)
    expect(result.current.carga.estado).toBe("inactivo")
    expect(result.current.detalle).toBeNull()
    lento.liberar()
  })
})

// ---------------------------------------------------------------------------------------------

describe("useRevisionTramite: editable por estado", () => {
  const estados = [
    "PENDIENTE_EXTRACCION",
    "PENDIENTE_REVISION",
    "APROBADO",
    "EN_PROCESO",
    "EJECUTADO_OVZ",
    "ERROR_OVZ",
    "RECHAZADO",
  ] as const

  for (const estado of estados) {
    it(`${estado}: editable = ${estado === "PENDIENTE_REVISION"}`, async () => {
      detallesEnOrden(() => HttpResponse.json(detalle({ estado })))
      const { result } = await montar()
      const esperado = estado === "PENDIENTE_REVISION"
      expect(result.current.editable).toBe(esperado)
      expect(result.current.puedeAprobar).toBe(esperado)
      expect(result.current.puedeRechazar).toBe(esperado)
      expect(result.current.puedeGuardar).toBe(false)
    })
  }

  it("fuera de PENDIENTE_REVISION el formulario es de solo lectura", async () => {
    detallesEnOrden(() => HttpResponse.json(detalle({ estado: "APROBADO" })))
    const { result } = await montar()
    let resultado: ReturnType<typeof result.current.cambiarTipoTramite> | undefined
    act(() => {
      resultado = result.current.cambiarTipoTramite("BAJA")
    })
    expect(resultado?.aceptado).toBe(false)
    act(() => {
      result.current.anadirCrotal("9999")
      result.current.cambiarCrotal(0, "1111")
      result.current.quitarCrotal(0)
      result.current.cambiarExplotacion(9)
    })
    expect(result.current.formulario).toEqual({ explotacionId: 3, tipoTramite: "ALTA", crotales: ["1234"] })
    expect(result.current.sucio).toBe(false)
  })

  it("fuera de PENDIENTE_REVISION las acciones no envían nada", async () => {
    let posts = 0
    detallesEnOrden(() => HttpResponse.json(detalle({ estado: "RECHAZADO" })))
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), () => {
        posts += 1
        return HttpResponse.json({})
      }),
      http.post(apiUrl("/tramites/7/rechazar"), () => {
        posts += 1
        return HttpResponse.json({})
      }),
    )
    const { result } = await montar()
    await act(async () => {
      await result.current.aprobar()
      await result.current.rechazar()
      await result.current.guardar()
    })
    expect(posts).toBe(0)
  })
})

// ---------------------------------------------------------------------------------------------

describe("useRevisionTramite: formulario", () => {
  it("añadir, cambiar y quitar crotales por índice, en orden", async () => {
    detallesEnOrden(() => HttpResponse.json(detalle({ crotales: [crotal("1111"), crotal("2222")] })))
    const { result } = await montar()
    act(() => {
      result.current.anadirCrotal()
    })
    expect(result.current.formulario.crotales).toEqual(["1111", "2222", ""])
    // Una entrada vacía no ensucia el formulario.
    expect(result.current.sucio).toBe(false)
    act(() => {
      result.current.cambiarCrotal(2, "3333")
    })
    expect(result.current.formulario.crotales).toEqual(["1111", "2222", "3333"])
    act(() => {
      result.current.quitarCrotal(0)
    })
    expect(result.current.formulario.crotales).toEqual(["2222", "3333"])
    act(() => {
      result.current.anadirCrotal("4444")
    })
    expect(result.current.formulario.crotales).toEqual(["2222", "3333", "4444"])
    expect(result.current.sucio).toBe(true)
  })

  it("un índice fuera de la lista se rechaza sin cambiar nada", async () => {
    detallesEnOrden(() => HttpResponse.json(detalle()))
    const { result } = await montar()
    let r1, r2
    act(() => {
      r1 = result.current.cambiarCrotal(5, "x")
      r2 = result.current.quitarCrotal(-1)
    })
    expect(r1).toMatchObject({ aceptado: false })
    expect(r2).toMatchObject({ aceptado: false })
    expect(result.current.formulario.crotales).toEqual(["1234"])
  })

  it("con valor guardado, explotación y tipo no se pueden volver a null (H5)", async () => {
    detallesEnOrden(() => HttpResponse.json(detalle()))
    const { result } = await montar()
    expect(result.current.puedeQuitarExplotacion).toBe(false)
    expect(result.current.puedeQuitarTipo).toBe(false)
    let rExp: ReturnType<typeof result.current.cambiarExplotacion> | undefined
    let rTipo: ReturnType<typeof result.current.cambiarTipoTramite> | undefined
    act(() => {
      result.current.cambiarExplotacion(9)
    })
    act(() => {
      rExp = result.current.cambiarExplotacion(null)
      rTipo = result.current.cambiarTipoTramite(null)
    })
    expect(rExp).toEqual({
      aceptado: false,
      motivo: "Una vez asignada, la explotación no se puede quitar. Elige otra si es necesario.",
    })
    expect(rTipo).toEqual({
      aceptado: false,
      motivo: "Una vez asignado, el tipo de trámite no se puede quitar. Elige otro si es necesario.",
    })
    expect(result.current.formulario.explotacionId).toBe(9)
    expect(result.current.formulario.tipoTramite).toBe("ALTA")
  })

  it("sin valor guardado, se puede elegir y volver a null (vuelve a no estar sucio)", async () => {
    detallesEnOrden(() =>
      HttpResponse.json(
        detalle({ explotacionId: null, explotacionCodigoRega: null, explotacionNombre: null, tipoTramite: null }),
      ),
    )
    const { result } = await montar()
    expect(result.current.puedeQuitarExplotacion).toBe(true)
    expect(result.current.puedeQuitarTipo).toBe(true)
    act(() => {
      result.current.cambiarExplotacion(5)
      result.current.cambiarTipoTramite("BAJA")
    })
    expect(result.current.sucio).toBe(true)
    act(() => {
      result.current.cambiarExplotacion(null)
      result.current.cambiarTipoTramite(null)
    })
    expect(result.current.formulario).toMatchObject({ explotacionId: null, tipoTramite: null })
    expect(result.current.sucio).toBe(false)
  })

  it("descartarCambios vuelve a lo cargado", async () => {
    detallesEnOrden(() => HttpResponse.json(detalle()))
    const { result } = await montar()
    act(() => {
      result.current.cambiarTipoTramite("BAJA")
      result.current.anadirCrotal("9999")
    })
    expect(result.current.sucio).toBe(true)
    act(() => result.current.descartarCambios())
    expect(result.current.formulario).toEqual({ explotacionId: 3, tipoTramite: "ALTA", crotales: ["1234"] })
    expect(result.current.sucio).toBe(false)
  })
})

// ---------------------------------------------------------------------------------------------

describe("useRevisionTramite: cambios sin guardar", () => {
  it("con cambios, aprobar y rechazar se desactivan con el aviso exacto; guardar se activa", async () => {
    detallesEnOrden(() => HttpResponse.json(detalle()))
    const { result } = await montar()
    expect(result.current.puedeGuardar).toBe(false)
    expect(result.current.avisoCambiosSinGuardar).toBeNull()

    act(() => {
      result.current.cambiarTipoTramite("BAJA")
    })
    expect(result.current.sucio).toBe(true)
    expect(result.current.puedeGuardar).toBe(true)
    expect(result.current.puedeAprobar).toBe(false)
    expect(result.current.puedeRechazar).toBe(false)
    expect(result.current.avisoCambiosSinGuardar).toBe("Guarda antes de aprobar")
  })

  it("con cambios, aprobar y rechazar no envían nada aunque se llamen", async () => {
    let posts = 0
    detallesEnOrden(() => HttpResponse.json(detalle()))
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), () => {
        posts += 1
        return HttpResponse.json({})
      }),
      http.post(apiUrl("/tramites/7/rechazar"), () => {
        posts += 1
        return HttpResponse.json({})
      }),
    )
    const { result } = await montar()
    act(() => {
      result.current.anadirCrotal("5555")
    })
    await act(async () => {
      await result.current.aprobar()
      await result.current.rechazar()
    })
    expect(posts).toBe(0)
  })

  it("sin cambios, guardar no envía PATCH", async () => {
    let patches = 0
    detallesEnOrden(() => HttpResponse.json(detalle()))
    server.use(
      http.patch(apiUrl("/tramites/7"), () => {
        patches += 1
        return HttpResponse.json(detalle())
      }),
    )
    const { result } = await montar()
    act(() => {
      result.current.cambiarCrotal(0, "  1234  ")
      result.current.anadirCrotal("   ")
    })
    expect(result.current.sucio).toBe(false)
    await act(async () => {
      await result.current.guardar()
    })
    expect(patches).toBe(0)
  })
})

// ---------------------------------------------------------------------------------------------

describe("useRevisionTramite: envío único", () => {
  it("mientras guarda, una segunda acción se ignora y enviando = 'guardar'", async () => {
    const lento = diferido()
    let patches = 0
    let posts = 0
    detallesEnOrden(() => HttpResponse.json(detalle()))
    server.use(
      http.patch(apiUrl("/tramites/7"), async () => {
        patches += 1
        await lento.promesa
        return HttpResponse.json(detalle({ tipoTramite: "BAJA", version: 5 }))
      }),
      http.post(apiUrl("/tramites/7/aprobar"), () => {
        posts += 1
        return HttpResponse.json({})
      }),
    )
    const { result } = await montar()
    act(() => {
      result.current.cambiarTipoTramite("BAJA")
    })
    let primera!: Promise<void>
    act(() => {
      primera = result.current.guardar()
    })
    expect(result.current.enviando).toBe("guardar")
    expect(result.current.puedeGuardar).toBe(false)
    expect(result.current.puedeAprobar).toBe(false)
    expect(result.current.puedeRechazar).toBe(false)
    await act(async () => {
      await result.current.guardar()
      await result.current.aprobar()
    })
    // Mientras hay una petición en curso tampoco se edita.
    let edicion: ReturnType<typeof result.current.cambiarTipoTramite> | undefined
    act(() => {
      edicion = result.current.cambiarTipoTramite("CENSO")
    })
    expect(edicion?.aceptado).toBe(false)

    lento.liberar()
    await act(async () => {
      await primera
    })
    expect(patches).toBe(1)
    expect(posts).toBe(0)
    expect(result.current.enviando).toBeNull()
  })

  it("dos aprobar seguidos en el mismo render envían una sola petición", async () => {
    const lento = diferido()
    let posts = 0
    detallesEnOrden(
      () => HttpResponse.json(detalle()),
      () => HttpResponse.json(detalle({ estado: "APROBADO", version: 5 })),
    )
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), async () => {
        posts += 1
        await lento.promesa
        return HttpResponse.json(respuestaLista(detalle({ estado: "APROBADO", version: 5 })))
      }),
      http.post(apiUrl("/tramites/7/rechazar"), () => {
        posts += 1
        return HttpResponse.json({})
      }),
    )
    const { result } = await montar()
    const acciones = result.current
    let p1!: Promise<void>
    let p2!: Promise<void>
    let p3!: Promise<void>
    act(() => {
      p1 = acciones.aprobar()
      p2 = acciones.aprobar()
      p3 = acciones.rechazar()
    })
    expect(result.current.enviando).toBe("aprobar")
    lento.liberar()
    await act(async () => {
      await Promise.all([p1, p2, p3])
    })
    expect(posts).toBe(1)
  })
})

// ---------------------------------------------------------------------------------------------

describe("useRevisionTramite: 200", () => {
  it("guardar envía versión + solo lo cambiado, y el formulario pasa a la respuesta", async () => {
    let cuerpo: unknown
    detallesEnOrden(() => HttpResponse.json(detalle({ crotales: [crotal("1234")] })))
    const respuesta = detalle({
      tipoTramite: "BAJA",
      version: 5,
      crotales: [crotal("1234", { crotal: "ES010000001234", resolucion: "EN_INVENTARIO", animalId: 1 }), crotal("5678")],
    })
    server.use(
      http.patch(apiUrl("/tramites/7"), async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json(respuesta)
      }),
    )
    const { result, onCambiado } = await montar()
    act(() => {
      result.current.cambiarTipoTramite("BAJA")
      result.current.anadirCrotal(" 5678 ")
      result.current.anadirCrotal("")
    })
    await act(async () => {
      await result.current.guardar()
    })
    expect(cuerpo).toEqual({ version: 4, tipoTramite: "BAJA", crotales: ["1234", "5678"] })
    expect(result.current.detalle).toEqual(respuesta)
    expect(result.current.formulario).toEqual({ explotacionId: 3, tipoTramite: "BAJA", crotales: ["1234", "5678"] })
    expect(result.current.sucio).toBe(false)
    expect(result.current.puedeAprobar).toBe(true)
    expect(onCambiado).toHaveBeenCalledTimes(1)
  })

  it("guardar con éxito borra el aviso anterior", async () => {
    let patches = 0
    detallesEnOrden(() => HttpResponse.json(detalle()))
    server.use(
      http.patch(apiUrl("/tramites/7"), () => {
        patches += 1
        return patches === 1
          ? HttpResponse.json({ motivo: "Crotal no válido." }, { status: 400 })
          : HttpResponse.json(detalle({ tipoTramite: "BAJA", version: 5 }))
      }),
    )
    const { result } = await montar()
    act(() => {
      result.current.cambiarTipoTramite("BAJA")
    })
    await act(async () => {
      await result.current.guardar()
    })
    expect(result.current.aviso?.mensaje).toBe("Crotal no válido.")
    await act(async () => {
      await result.current.guardar()
    })
    expect(result.current.aviso).toBeNull()
  })

  it("un segundo guardar usa la versión devuelta por el primero", async () => {
    const versiones: unknown[] = []
    detallesEnOrden(() => HttpResponse.json(detalle({ version: 4 })))
    server.use(
      http.patch(apiUrl("/tramites/7"), async ({ request }) => {
        const cuerpo = (await request.json()) as { version: number; tipoTramite: string }
        versiones.push(cuerpo.version)
        return HttpResponse.json(detalle({ tipoTramite: cuerpo.tipoTramite, version: cuerpo.version + 1 }))
      }),
    )
    const { result } = await montar()
    act(() => {
      result.current.cambiarTipoTramite("BAJA")
    })
    await act(async () => {
      await result.current.guardar()
    })
    act(() => {
      result.current.cambiarTipoTramite("CENSO")
    })
    await act(async () => {
      await result.current.guardar()
    })
    expect(versiones).toEqual([4, 5])
  })

  it("aprobar envía la versión cargada; tras el 200 se recarga en solo lectura y avisa a la cola", async () => {
    let cuerpo: unknown
    const get = detallesEnOrden(
      () => HttpResponse.json(detalle({ version: 4 })),
      () => HttpResponse.json(detalle({ estado: "APROBADO", version: 5 })),
    )
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json(respuestaLista(detalle({ estado: "APROBADO", version: 5 })))
      }),
    )
    const { result, onCambiado } = await montar()
    await act(async () => {
      await result.current.aprobar()
    })
    expect(cuerpo).toEqual({ version: 4 })
    expect(get.llamadas()).toBe(2)
    expect(result.current.detalle?.estado).toBe("APROBADO")
    expect(result.current.detalle?.version).toBe(5)
    expect(result.current.editable).toBe(false)
    expect(result.current.puedeAprobar).toBe(false)
    expect(result.current.puedeRechazar).toBe(false)
    expect(result.current.enviando).toBeNull()
    expect(result.current.aviso).toEqual({ tipo: "exito", accion: "aprobar", mensaje: "Trámite aprobado." })
    expect(onCambiado).toHaveBeenCalledTimes(1)
  })

  it("rechazar lleva {version} del detalle mostrado; tras el 200 se recarga en solo lectura y avisa a la cola", async () => {
    let crudo: string | null = null
    detallesEnOrden(
      () => HttpResponse.json(detalle()),
      () => HttpResponse.json(detalle({ estado: "RECHAZADO", version: 5 })),
    )
    server.use(
      http.post(apiUrl("/tramites/7/rechazar"), async ({ request }) => {
        crudo = await request.text()
        return HttpResponse.json(respuestaLista(detalle({ estado: "RECHAZADO", version: 5 })))
      }),
    )
    const { result, onCambiado } = await montar()
    await act(async () => {
      await result.current.rechazar()
    })
    expect(crudo).toBe('{"version":4}')
    expect(result.current.detalle?.estado).toBe("RECHAZADO")
    expect(result.current.editable).toBe(false)
    expect(result.current.aviso).toEqual({ tipo: "exito", accion: "rechazar", mensaje: "Trámite rechazado." })
    expect(onCambiado).toHaveBeenCalledTimes(1)
  })

  it("si la recarga tras aprobar falla, queda el detalle de la respuesta, el éxito y un aviso de recarga no bloqueante", async () => {
    const get = detallesEnOrden(
      () => HttpResponse.json(detalle({ version: 4 })),
      () => new HttpResponse(null, { status: 500 }),
      () => HttpResponse.json(detalle({ estado: "APROBADO", version: 5, mensajeOriginal: "recargado" })),
    )
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), () =>
        HttpResponse.json(
          respuestaLista(
            detalle({
              estado: "APROBADO",
              version: 5,
              tipoTramite: "BAJA",
              crotales: [crotal("1234", { crotal: "ES010000001234", resolucion: "EN_INVENTARIO", animalId: 1 })],
            }),
          ),
        ),
      ),
    )
    const { result, onCambiado } = await montar()
    await act(async () => {
      await result.current.aprobar()
    })
    // El detalle es el de la respuesta del 200 (fusionado), no null ni el anterior.
    expect(result.current.carga.estado).toBe("listo")
    expect(result.current.detalle).toMatchObject({
      estado: "APROBADO",
      version: 5,
      tipoTramite: "BAJA",
      explotacionCodigoRega: "ES123",
      mensajeOriginal: "alta del 1234",
    })
    expect(result.current.detalle?.crotales[0]).toMatchObject({ crotal: "ES010000001234", resolucion: "EN_INVENTARIO" })
    expect(result.current.formulario).toEqual({ explotacionId: 3, tipoTramite: "BAJA", crotales: ["1234"] })
    expect(result.current.editable).toBe(false)
    expect(result.current.puedeAprobar).toBe(false)
    expect(result.current.enviando).toBeNull()
    // Los dos avisos conviven: el éxito (aviso) y la recarga fallida (recargaFallida, aparte).
    expect(result.current.aviso).toEqual({ tipo: "exito", accion: "aprobar", mensaje: "Trámite aprobado." })
    expect(result.current.recargaFallida).toBe(TEXTO_ERROR_SERVIDOR)
    expect(onCambiado).toHaveBeenCalledTimes(1)

    // Reintentar recarga sin quitar el detalle; si va bien, el aviso de recarga desaparece.
    act(() => result.current.reintentarRecarga())
    expect(result.current.detalle?.estado).toBe("APROBADO")
    expect(result.current.recargaFallida).toBeNull()
    await waitFor(() => expect(result.current.detalle?.mensajeOriginal).toBe("recargado"))
    expect(result.current.aviso?.tipo).toBe("exito")
    expect(result.current.recargaFallida).toBeNull()
    expect(get.llamadas()).toBe(3)
  })

  it("m3 (T4): la etiqueta de la explotación sale de la respuesta del 200, no del detalle anterior", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle({ version: 4 })),
      () => HttpResponse.json({ status: 500 }, { status: 500 }),
    )
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), () =>
        HttpResponse.json(
          respuestaLista(
            detalle({
              estado: "APROBADO",
              version: 5,
              explotacionId: 9,
              explotacionCodigoRega: "ES999",
              explotacionNombre: "Los Olivos",
            }),
          ),
        ),
      ),
    )
    const { result } = await montar()
    await act(async () => {
      await result.current.aprobar()
    })
    expect(result.current.recargaFallida).toBe(TEXTO_ERROR_SERVIDOR)
    expect(result.current.explotacionAsignada).toEqual({ id: 9, codigoRega: "ES999", nombre: "Los Olivos" })
  })

  it("si reintentar la recarga vuelve a fallar, el aviso de recarga vuelve y el detalle sigue", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle()),
      () => HttpResponse.error(),
    )
    server.use(
      http.post(apiUrl("/tramites/7/rechazar"), () =>
        HttpResponse.json(respuestaLista(detalle({ estado: "RECHAZADO", version: 5 }))),
      ),
    )
    const { result } = await montar()
    await act(async () => {
      await result.current.rechazar()
    })
    expect(result.current.detalle?.estado).toBe("RECHAZADO")
    expect(result.current.recargaFallida).toBe(TEXTO_ERROR_RED)
    act(() => result.current.reintentarRecarga())
    expect(result.current.recargaFallida).toBeNull()
    await waitFor(() => expect(result.current.recargaFallida).toBe(TEXTO_ERROR_RED))
    expect(result.current.detalle?.estado).toBe("RECHAZADO")
    expect(result.current.aviso?.tipo).toBe("exito")
  })

  it("mientras la recarga tras el 200 está en curso, ya se ve el estado de la respuesta (fusionado)", async () => {
    const pausa = diferido()
    let gets = 0
    server.use(
      http.get(apiUrl("/tramites/7"), async () => {
        gets += 1
        if (gets === 1) return HttpResponse.json(detalle())
        await pausa.promesa
        return HttpResponse.json(detalle({ estado: "RECHAZADO", version: 5 }))
      }),
      http.post(apiUrl("/tramites/7/rechazar"), () =>
        HttpResponse.json(respuestaLista(detalle({ estado: "RECHAZADO", version: 5 }))),
      ),
    )
    const { result } = await montar()
    let accion!: Promise<void>
    act(() => {
      accion = result.current.rechazar()
    })
    await waitFor(() => expect(gets).toBe(2))
    await waitFor(() => expect(result.current.detalle).toMatchObject({ estado: "RECHAZADO", version: 5 }))
    expect(result.current.editable).toBe(false)
    pausa.liberar()
    await act(async () => {
      await accion
    })
  })

})

// ---------------------------------------------------------------------------------------------

describe("useRevisionTramite: 409", () => {
  it("guardar 409: recarga, descarta el formulario, motivo tal cual, sin reintento, avisa a la cola", async () => {
    let patches = 0
    const get = detallesEnOrden(
      () => HttpResponse.json(detalle({ version: 4 })),
      () => HttpResponse.json(detalle({ tipoTramite: "CENSO", version: 6, crotales: [crotal("7777")] })),
    )
    server.use(
      http.patch(apiUrl("/tramites/7"), () => {
        patches += 1
        return HttpResponse.json(
          { motivo: "Otra persona ha modificado este trámite. Recarga y vuelve a intentarlo." },
          { status: 409 },
        )
      }),
    )
    const { result, onCambiado } = await montar()
    act(() => {
      result.current.cambiarTipoTramite("BAJA")
      result.current.anadirCrotal("5555")
    })
    await act(async () => {
      await result.current.guardar()
    })
    expect(patches).toBe(1)
    expect(get.llamadas()).toBe(2)
    expect(result.current.aviso).toEqual({
      tipo: "conflicto",
      accion: "guardar",
      mensaje: "Otra persona ha modificado este trámite. Recarga y vuelve a intentarlo.",
    })
    expect(result.current.formulario).toEqual({ explotacionId: 3, tipoTramite: "CENSO", crotales: ["7777"] })
    expect(result.current.detalle?.version).toBe(6)
    expect(result.current.sucio).toBe(false)
    expect(onCambiado).toHaveBeenCalledTimes(1)

    // Sin reintento automático: pasado un rato sigue habiendo un solo PATCH.
    await new Promise((r) => setTimeout(r, 30))
    expect(patches).toBe(1)
  })

  it("aprobar 409 por resolución cambiada: la recarga trae la versión nueva y el siguiente aprobar la usa", async () => {
    const versiones: unknown[] = []
    detallesEnOrden(
      () => HttpResponse.json(detalle({ version: 4, crotales: [crotal("1234", { resolucion: "EN_INVENTARIO" })] })),
      () => HttpResponse.json(detalle({ version: 5, crotales: [crotal("1234", { resolucion: "AMBIGUO" })] })),
      () => HttpResponse.json(detalle({ estado: "APROBADO", version: 6 })),
    )
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), async ({ request }) => {
        const { version } = (await request.json()) as { version: number }
        versiones.push(version)
        return version === 4
          ? HttpResponse.json(
              { motivo: "La resolución de los crotales ha cambiado. Revisa el trámite antes de aprobarlo." },
              { status: 409 },
            )
          : HttpResponse.json(respuestaLista(detalle({ estado: "APROBADO", version: 6 })))
      }),
    )
    const { result, onCambiado } = await montar()
    await act(async () => {
      await result.current.aprobar()
    })
    expect(result.current.aviso).toEqual({
      tipo: "conflicto",
      accion: "aprobar",
      mensaje: "La resolución de los crotales ha cambiado. Revisa el trámite antes de aprobarlo.",
    })
    expect(result.current.detalle?.version).toBe(5)
    expect(result.current.detalle?.crotales[0].resolucion).toBe("AMBIGUO")
    expect(onCambiado).toHaveBeenCalledTimes(1)
    expect(versiones).toEqual([4])

    await act(async () => {
      await result.current.aprobar()
    })
    expect(versiones).toEqual([4, 5])
    expect(result.current.detalle?.estado).toBe("APROBADO")
  })

  it("rechazar 409: recarga y el trámite queda en solo lectura con el motivo", async () => {
    const cuerpos: unknown[] = []
    detallesEnOrden(
      () => HttpResponse.json(detalle()),
      () => HttpResponse.json(detalle({ estado: "APROBADO", version: 5 })),
    )
    server.use(
      http.post(apiUrl("/tramites/7/rechazar"), async ({ request }) => {
        cuerpos.push(await request.json())
        return HttpResponse.json({ motivo: "El trámite ya no está pendiente de revisión." }, { status: 409 })
      }),
    )
    const { result, onCambiado } = await montar()
    await act(async () => {
      await result.current.rechazar()
    })
    expect(result.current.aviso).toEqual({
      tipo: "conflicto",
      accion: "rechazar",
      mensaje: "El trámite ya no está pendiente de revisión.",
    })
    // Se envió la versión que se mostraba; tras el 409 no se reintenta.
    expect(cuerpos).toEqual([{ version: 4 }])
    expect(result.current.detalle?.estado).toBe("APROBADO")
    expect(result.current.editable).toBe(false)
    expect(onCambiado).toHaveBeenCalledTimes(1)
  })

  it("si la recarga tras el 409 también falla, se ven los dos errores y no hay acciones", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle()),
      () => HttpResponse.error(),
      () => HttpResponse.json(detalle({ version: 9 })),
    )
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), () =>
        HttpResponse.json({ motivo: "Falta el tipo de trámite." }, { status: 409 }),
      ),
    )
    const { result, onCambiado } = await montar()
    await act(async () => {
      await result.current.aprobar()
    })
    expect(result.current.aviso).toEqual({ tipo: "conflicto", accion: "aprobar", mensaje: "Falta el tipo de trámite." })
    expect(result.current.carga).toMatchObject({ estado: "error", mensaje: TEXTO_ERROR_RED })
    expect(result.current.detalle).toBeNull()
    expect(result.current.editable).toBe(false)
    expect(result.current.puedeAprobar).toBe(false)
    expect(onCambiado).toHaveBeenCalledTimes(1)

    // Reintentar la carga recupera el detalle y conserva el motivo del conflicto.
    act(() => result.current.reintentarCarga())
    await waitFor(() => expect(result.current.carga.estado).toBe("listo"))
    expect(result.current.detalle?.version).toBe(9)
    expect(result.current.aviso?.mensaje).toBe("Falta el tipo de trámite.")
  })
})

// ---------------------------------------------------------------------------------------------

describe("useRevisionTramite: 400, 403, 404, red y servidor", () => {
  it("guardar 400: conserva las ediciones, muestra el motivo, no recarga ni avisa a la cola", async () => {
    const get = detallesEnOrden(() => HttpResponse.json(detalle()))
    server.use(
      http.patch(apiUrl("/tramites/7"), () =>
        HttpResponse.json({ motivo: "Indica al menos los últimos 4 dígitos del crotal." }, { status: 400 }),
      ),
    )
    const { result, onCambiado } = await montar()
    act(() => {
      result.current.anadirCrotal("12")
    })
    await act(async () => {
      await result.current.guardar()
    })
    expect(result.current.aviso).toEqual({
      tipo: "validacion",
      accion: "guardar",
      mensaje: "Indica al menos los últimos 4 dígitos del crotal.",
    })
    expect(result.current.formulario.crotales).toEqual(["1234", "12"])
    expect(result.current.sucio).toBe(true)
    expect(result.current.puedeGuardar).toBe(true)
    expect(get.llamadas()).toBe(1)
    expect(onCambiado).not.toHaveBeenCalled()
  })

  it("aprobar 403: texto fijo de la suscripción y nunca cierra sesión", async () => {
    const alNoAutorizado = vi.fn()
    setUnauthorizedHandler(alNoAutorizado)
    detallesEnOrden(() => HttpResponse.json(detalle()))
    server.use(http.post(apiUrl("/tramites/7/aprobar"), () => new HttpResponse(null, { status: 403 })))
    const { result, onCambiado } = await montar()
    await act(async () => {
      await result.current.aprobar()
    })
    expect(result.current.aviso).toEqual({ tipo: "prohibido", accion: "aprobar", mensaje: TEXTO_SUSCRIPCION })
    expect(alNoAutorizado).not.toHaveBeenCalled()
    expect(result.current.puedeAprobar).toBe(true)
    expect(onCambiado).not.toHaveBeenCalled()
  })

  it("aprobar 403 con {motivo}: se enseña el motivo del backend tal cual, no el texto fijo", async () => {
    const motivo = "Tu suscripción está suspendida: actualízala en Facturación para aprobar."
    detallesEnOrden(() => HttpResponse.json(detalle()))
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), () => HttpResponse.json({ motivo }, { status: 403 })),
    )
    const { result, onCambiado } = await montar()
    await act(async () => {
      await result.current.aprobar()
    })
    expect(result.current.aviso).toEqual({ tipo: "prohibido", accion: "aprobar", mensaje: motivo })
    expect(onCambiado).not.toHaveBeenCalled()
  })

  for (const accion of ["aprobar", "rechazar"] as const) {
    it(`${accion} 404 y la recarga también 404: estado no-encontrado, sin acciones, avisa a la cola`, async () => {
      const get = detallesEnOrden(
        () => HttpResponse.json(detalle()),
        () => new HttpResponse(null, { status: 404 }),
      )
      server.use(http.post(apiUrl(`/tramites/7/${accion}`), () => new HttpResponse(null, { status: 404 })))
      const { result, onCambiado } = await montar()
      await act(async () => {
        await result.current[accion]()
      })
      expect(get.llamadas()).toBe(2)
      expect(result.current.carga).toEqual({ estado: "no-encontrado", mensaje: TEXTO_NO_EXISTE })
      expect(result.current.detalle).toBeNull()
      expect(result.current.editable).toBe(false)
      expect(result.current.puedeAprobar).toBe(false)
      expect(result.current.puedeRechazar).toBe(false)
      expect(result.current.enviando).toBeNull()
      expect(onCambiado).toHaveBeenCalledTimes(1)
    })
  }

  it("guardar 404 y la recarga también 404: estado no-encontrado y avisa a la cola", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle()),
      () => new HttpResponse(null, { status: 404 }),
    )
    server.use(http.patch(apiUrl("/tramites/7"), () => new HttpResponse(null, { status: 404 })))
    const { result, onCambiado } = await montar()
    act(() => {
      result.current.cambiarExplotacion(9)
    })
    await act(async () => {
      await result.current.guardar()
    })
    expect(result.current.carga).toEqual({ estado: "no-encontrado", mensaje: TEXTO_NO_EXISTE })
    expect(result.current.puedeGuardar).toBe(false)
    expect(onCambiado).toHaveBeenCalledTimes(1)
  })

  it("guardar 404 pero la recarga va bien: la explotación era el problema, se conservan las ediciones", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle({ version: 4 })),
      () => HttpResponse.json(detalle({ version: 4, mensajeOriginal: "recargado" })),
    )
    server.use(http.patch(apiUrl("/tramites/7"), () => new HttpResponse(null, { status: 404 })))
    const { result, onCambiado } = await montar()
    act(() => {
      result.current.cambiarExplotacion(9)
      result.current.cambiarTipoTramite("BAJA")
    })
    await act(async () => {
      await result.current.guardar()
    })
    expect(result.current.aviso).toEqual({
      tipo: "no-encontrado",
      accion: "guardar",
      mensaje: "La explotación elegida ya no está disponible. Elige otra.",
    })
    expect(result.current.detalle?.mensajeOriginal).toBe("recargado")
    expect(result.current.formulario).toMatchObject({ explotacionId: 9, tipoTramite: "BAJA" })
    expect(result.current.sucio).toBe(true)
    expect(result.current.puedeGuardar).toBe(true)
    expect(result.current.carga.estado).toBe("listo")
    // En el servidor no cambió nada: la cola no se refresca.
    expect(onCambiado).not.toHaveBeenCalled()
  })

  for (const caso of [
    { nombre: "otra versión", fresco: { version: 5, tipoTramite: "CENSO" } },
    { nombre: "ya no está pendiente", fresco: { version: 4, estado: "APROBADO" } },
  ] as const) {
    it(`N1: guardar 404 y la recarga trae ${caso.nombre}: se descartan las ediciones como en un 409`, async () => {
      const patches: unknown[] = []
      detallesEnOrden(
        () => HttpResponse.json(detalle({ version: 4 })),
        () => HttpResponse.json(detalle(caso.fresco)),
      )
      server.use(
        http.patch(apiUrl("/tramites/7"), async ({ request }) => {
          patches.push(await request.json())
          return new HttpResponse(null, { status: 404 })
        }),
      )
      const { result, onCambiado } = await montar()
      act(() => {
        result.current.cambiarExplotacion(9)
        result.current.cambiarTipoTramite("BAJA")
      })
      await act(async () => {
        await result.current.guardar()
      })
      expect(patches).toEqual([{ version: 4, explotacionId: 9, tipoTramite: "BAJA" }])
      expect(result.current.aviso).toEqual({
        tipo: "conflicto",
        accion: "guardar",
        mensaje: "El trámite ha cambiado mientras lo editabas. Se han descartado tus cambios; revisa los datos actuales.",
      })
      expect(result.current.detalle).toMatchObject(caso.fresco)
      expect(result.current.formulario).toMatchObject({
        explotacionId: 3,
        tipoTramite: "tipoTramite" in caso.fresco ? caso.fresco.tipoTramite : "ALTA",
      })
      expect(result.current.sucio).toBe(false)
      expect(result.current.puedeGuardar).toBe(false)
      // Algo cambió en el servidor: la cola se refresca.
      expect(onCambiado).toHaveBeenCalledTimes(1)
    })
  }

  it("red y 5xx: textos genéricos, se conservan las ediciones", async () => {
    let patches = 0
    detallesEnOrden(() => HttpResponse.json(detalle()))
    server.use(
      http.patch(apiUrl("/tramites/7"), () => {
        patches += 1
        return patches === 1 ? HttpResponse.error() : new HttpResponse("traza", { status: 500 })
      }),
    )
    const { result, onCambiado } = await montar()
    act(() => {
      result.current.cambiarTipoTramite("BAJA")
    })
    await act(async () => {
      await result.current.guardar()
    })
    expect(result.current.aviso).toEqual({ tipo: "error", accion: "guardar", mensaje: TEXTO_ERROR_RED })
    await act(async () => {
      await result.current.guardar()
    })
    expect(result.current.aviso).toEqual({ tipo: "error", accion: "guardar", mensaje: TEXTO_ERROR_SERVIDOR })
    expect(result.current.formulario.tipoTramite).toBe("BAJA")
    expect(onCambiado).not.toHaveBeenCalled()
  })

  it("el aviso se puede descartar", async () => {
    detallesEnOrden(() => HttpResponse.json(detalle()))
    server.use(http.post(apiUrl("/tramites/7/aprobar"), () => new HttpResponse(null, { status: 403 })))
    const { result } = await montar()
    await act(async () => {
      await result.current.aprobar()
    })
    expect(result.current.aviso).not.toBeNull()
    act(() => result.current.descartarAviso())
    expect(result.current.aviso).toBeNull()
  })

  it("una acción nueva quita el aviso anterior en cuanto empieza", async () => {
    const lento = diferido()
    let posts = 0
    detallesEnOrden(() => HttpResponse.json(detalle()))
    server.use(
      http.post(apiUrl("/tramites/7/aprobar"), async () => {
        posts += 1
        if (posts === 2) await lento.promesa
        return new HttpResponse(null, { status: 403 })
      }),
    )
    const { result } = await montar()
    await act(async () => {
      await result.current.aprobar()
    })
    expect(result.current.aviso?.tipo).toBe("prohibido")
    let segunda!: Promise<void>
    act(() => {
      segunda = result.current.aprobar()
    })
    expect(result.current.enviando).toBe("aprobar")
    expect(result.current.aviso).toBeNull()
    lento.liberar()
    await act(async () => {
      await segunda
    })
    expect(result.current.aviso?.tipo).toBe("prohibido")
  })

  it("una acción que termina después de cambiar de trámite no toca el nuevo, pero sí avisa a la cola", async () => {
    const lento = diferido()
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ id: 7 }))),
      http.get(apiUrl("/tramites/8"), () => HttpResponse.json(detalle({ id: 8, version: 1 }))),
      http.post(apiUrl("/tramites/7/aprobar"), async () => {
        await lento.promesa
        return HttpResponse.json({ motivo: "Falta el tipo de trámite." }, { status: 409 })
      }),
    )
    const onCambiado = vi.fn()
    const { result, rerender } = renderHook(({ id }) => useRevisionTramite(id, { onCambiado }), {
      initialProps: { id: 7 as number | null },
    })
    await waitFor(() => expect(result.current.carga.estado).toBe("listo"))
    let accion!: Promise<void>
    act(() => {
      accion = result.current.aprobar()
    })
    rerender({ id: 8 })
    await waitFor(() => expect(result.current.detalle?.id).toBe(8))
    expect(result.current.enviando).toBeNull()
    lento.liberar()
    await act(async () => {
      await accion
    })
    expect(result.current.detalle?.id).toBe(8)
    expect(result.current.aviso).toBeNull()
    expect(onCambiado).toHaveBeenCalledTimes(1)
  })
})

// ---------------------------------------------------------------------------------------------

describe("useRevisionTramite: la recarga tras una acción forma parte del envío (I1)", () => {
  it("tras un 409, mientras la recarga está abierta nada se puede enviar; luego aprobar usa la versión nueva", async () => {
    const pausa = diferido()
    let gets = 0
    const versiones: number[] = []
    server.use(
      http.get(apiUrl("/tramites/7"), async () => {
        gets += 1
        if (gets === 1) return HttpResponse.json(detalle({ version: 4 }))
        await pausa.promesa
        return HttpResponse.json(detalle({ version: 5, crotales: [crotal("1234", { resolucion: "AMBIGUO" })] }))
      }),
      http.post(apiUrl("/tramites/7/aprobar"), async ({ request }) => {
        const { version } = (await request.json()) as { version: number }
        versiones.push(version)
        return version === 4
          ? HttpResponse.json({ motivo: "La resolución de los crotales ha cambiado." }, { status: 409 })
          : HttpResponse.json(respuestaLista(detalle({ estado: "APROBADO", version: 6 })))
      }),
    )
    const { result } = await montar()
    let accion!: Promise<void>
    act(() => {
      accion = result.current.aprobar()
    })
    await waitFor(() => expect(gets).toBe(2))
    await waitFor(() => expect(result.current.aviso?.tipo).toBe("conflicto"))

    // Recarga abierta: sigue "enviando", nada está permitido y aprobar no envía.
    expect(result.current.enviando).toBe("aprobar")
    expect(result.current.puedeAprobar).toBe(false)
    expect(result.current.puedeGuardar).toBe(false)
    expect(result.current.puedeRechazar).toBe(false)
    await act(async () => {
      await result.current.aprobar()
    })
    expect(versiones).toEqual([4])

    pausa.liberar()
    await act(async () => {
      await accion
    })
    expect(result.current.enviando).toBeNull()
    expect(result.current.detalle?.version).toBe(5)
    await act(async () => {
      await result.current.aprobar()
    })
    expect(versiones).toEqual([4, 5])
  })

  it("tras un 200 de aprobar, mientras la recarga está abierta sigue enviando y nada se envía", async () => {
    const pausa = diferido()
    let gets = 0
    let posts = 0
    server.use(
      http.get(apiUrl("/tramites/7"), async () => {
        gets += 1
        if (gets === 1) return HttpResponse.json(detalle({ version: 4 }))
        await pausa.promesa
        return HttpResponse.json(detalle({ estado: "APROBADO", version: 5 }))
      }),
      http.post(apiUrl("/tramites/7/aprobar"), () => {
        posts += 1
        return HttpResponse.json(respuestaLista(detalle({ estado: "APROBADO", version: 5 })))
      }),
      http.post(apiUrl("/tramites/7/rechazar"), () => {
        posts += 1
        return HttpResponse.json({})
      }),
    )
    const { result } = await montar()
    let accion!: Promise<void>
    act(() => {
      accion = result.current.aprobar()
    })
    await waitFor(() => expect(gets).toBe(2))
    await waitFor(() => expect(result.current.detalle?.estado).toBe("APROBADO"))

    expect(result.current.enviando).toBe("aprobar")
    expect(result.current.puedeAprobar).toBe(false)
    expect(result.current.puedeGuardar).toBe(false)
    expect(result.current.puedeRechazar).toBe(false)
    await act(async () => {
      await result.current.aprobar()
      await result.current.rechazar()
    })
    expect(posts).toBe(1)

    pausa.liberar()
    await act(async () => {
      await accion
    })
    expect(result.current.enviando).toBeNull()
    expect(result.current.detalle).toMatchObject({ estado: "APROBADO", version: 5 })
    expect(result.current.editable).toBe(false)
  })
})

describe("useRevisionTramite: red o 5xx al aprobar o rechazar (M5)", () => {
  for (const accion of ["aprobar", "rechazar"] as const) {
    it(`${accion}: el resultado es incierto, así que avisa a la cola y recarga el detalle`, async () => {
      const get = detallesEnOrden(
        () => HttpResponse.json(detalle()),
        () => HttpResponse.json(detalle({ estado: accion === "aprobar" ? "APROBADO" : "RECHAZADO", version: 5 })),
      )
      server.use(http.post(apiUrl(`/tramites/7/${accion}`), () => HttpResponse.error()))
      const { result, onCambiado } = await montar()
      await act(async () => {
        await result.current[accion]()
      })
      // N2 (revisión 9a): la recarga dice que sí se aplicó, así que no se dice "Inténtalo de nuevo".
      expect(result.current.aviso).toEqual({
        tipo: "exito",
        accion,
        mensaje: `No hubo respuesta a tiempo, pero el trámite consta como ${accion === "aprobar" ? "aprobado" : "rechazado"}.`,
      })
      expect(onCambiado).toHaveBeenCalledTimes(1)
      expect(get.llamadas()).toBe(2)
      expect(result.current.detalle?.version).toBe(5)
      expect(result.current.editable).toBe(false)
    })
  }

  it("N2: si la recarga lo muestra aún pendiente, sí se dice el error con «Inténtalo de nuevo»", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle()),
      () => HttpResponse.json(detalle({ version: 4 })),
    )
    server.use(http.post(apiUrl("/tramites/7/aprobar"), () => HttpResponse.error()))
    const { result } = await montar()
    await act(async () => {
      await result.current.aprobar()
    })
    expect(result.current.aviso).toEqual({ tipo: "error", accion: "aprobar", mensaje: TEXTO_ERROR_RED })
    expect(result.current.editable).toBe(true)
  })

  // m4 (revisión 9b): si consta otro estado (hoy RECHAZADO; tras 3c, EN_PROCESO o EJECUTADO_OVZ tras
  // un aprobar que sí se aplicó), el aviso no dice "no se ha aprobado": dice que cambió de estado.
  for (const caso of [
    { estado: "RECHAZADO", etiqueta: "rechazado" },
    { estado: "EJECUTADO_OVZ", etiqueta: "ejecutado en OVZ.net" },
  ] as const) {
    it(`N2/m4: si la recarga lo muestra en ${caso.estado}, aviso neutro de cambio de estado`, async () => {
      detallesEnOrden(
        () => HttpResponse.json(detalle()),
        () => HttpResponse.json(detalle({ estado: caso.estado, version: 5 })),
      )
      server.use(http.post(apiUrl("/tramites/7/aprobar"), () => new HttpResponse(null, { status: 502 })))
      const { result } = await montar()
      await act(async () => {
        await result.current.aprobar()
      })
      expect(result.current.aviso).toEqual({
        tipo: "estado-cambiado",
        accion: "aprobar",
        mensaje: `No hubo respuesta a tiempo. El trámite consta ahora como ${caso.etiqueta}.`,
      })
    })
  }

  it("aprobar 500 y la recarga también falla: los dos errores a la vista, sin detalle desfasado", async () => {
    detallesEnOrden(
      () => HttpResponse.json(detalle()),
      () => HttpResponse.error(),
    )
    server.use(http.post(apiUrl("/tramites/7/aprobar"), () => new HttpResponse(null, { status: 500 })))
    const { result, onCambiado } = await montar()
    await act(async () => {
      await result.current.aprobar()
    })
    expect(result.current.aviso).toEqual({ tipo: "error", accion: "aprobar", mensaje: TEXTO_ERROR_SERVIDOR })
    expect(result.current.carga).toMatchObject({ estado: "error", mensaje: TEXTO_ERROR_RED })
    expect(result.current.detalle).toBeNull()
    expect(onCambiado).toHaveBeenCalledTimes(1)
  })
})

describe("useRevisionTramite: sesión ligada al trámite (M3, R2)", () => {
  it("un manejador guardado de otro trámite no envía nada ni escribe en el actual", async () => {
    let posts = 0
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ id: 7, version: 4 }))),
      http.get(apiUrl("/tramites/8"), () => HttpResponse.json(detalle({ id: 8, version: 1 }))),
      http.post(apiUrl("/tramites/7/aprobar"), () => {
        posts += 1
        return HttpResponse.json(respuestaLista(detalle({ id: 7, estado: "APROBADO", version: 5 })))
      }),
      http.post(apiUrl("/tramites/8/aprobar"), () => {
        posts += 1
        return HttpResponse.json({})
      }),
    )
    const onCambiado = vi.fn()
    const { result, rerender } = renderHook(({ id }) => useRevisionTramite(id, { onCambiado }), {
      initialProps: { id: 7 as number | null },
    })
    await waitFor(() => expect(result.current.carga.estado).toBe("listo"))
    const aprobarDel7 = result.current.aprobar
    rerender({ id: 8 })
    await waitFor(() => expect(result.current.detalle?.id).toBe(8))

    await act(async () => {
      await aprobarDel7()
    })
    expect(posts).toBe(0)
    expect(result.current.detalle).toMatchObject({ id: 8, version: 1, estado: "PENDIENTE_REVISION" })
    expect(result.current.enviando).toBeNull()
    expect(onCambiado).not.toHaveBeenCalled()
  })

  it("una recarga de la sesión cerrada que llega igualmente (sin respetar el abort) no toca el trámite nuevo", async () => {
    server.use(
      http.get(apiUrl("/tramites/7"), () => HttpResponse.json(detalle({ id: 7 }))),
      http.get(apiUrl("/tramites/8"), () => HttpResponse.json(detalle({ id: 8, version: 1 }))),
      http.post(apiUrl("/tramites/7/aprobar"), () =>
        HttpResponse.json(respuestaLista(detalle({ id: 7, estado: "APROBADO", version: 5 }))),
      ),
    )
    const onCambiado = vi.fn()
    const { result, rerender } = renderHook(({ id }) => useRevisionTramite(id, { onCambiado }), {
      initialProps: { id: 7 as number | null },
    })
    await waitFor(() => expect(result.current.carga.estado).toBe("listo"))

    // La recarga tras el 200 es una promesa que controla el test y que ignora el AbortSignal: así
    // se reproduce, de forma determinista, una respuesta que llega después de cerrar la sesión.
    const tardia = diferido()
    vi.mocked(obtenerDetalleTramite).mockImplementationOnce(async () => {
      await tardia.promesa
      return detalle({ id: 7, estado: "APROBADO", version: 5, tipoTramite: "DEMORA" })
    })
    let accion!: Promise<void>
    act(() => {
      accion = result.current.aprobar()
    })
    await waitFor(() => expect(vi.mocked(obtenerDetalleTramite).mock.calls.length).toBe(2))

    rerender({ id: 8 })
    await waitFor(() => expect(result.current.detalle?.id).toBe(8))
    tardia.liberar()
    await act(async () => {
      await accion
    })
    expect(result.current.detalle).toMatchObject({ id: 8, version: 1, estado: "PENDIENTE_REVISION" })
    expect(result.current.formulario.tipoTramite).toBe("ALTA")
    expect(result.current.recargaFallida).toBeNull()
  })
})
