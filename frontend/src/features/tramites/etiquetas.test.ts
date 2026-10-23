import { describe, expect, it } from "vitest"
import {
  ESTADOS_RESPUESTA_WHATSAPP,
  ESTADOS_TRAMITE,
  ORIGENES_TRAMITE,
  RESOLUCIONES_CROTAL,
  TEXTOS_EXTRACCION,
  avisosExtraccion,
  etiquetaOrigen,
  ROLES_CONTACTO,
  TIPOS_TRAMITE,
  badgeVarianteDeEstado,
  etiquetaRolContacto,
  etiquetaTipoTramite,
  presentacionEstado,
  presentacionEstadoTramite,
  esDescartado,
  iconoEstadoTramite,
  iconoTipoTramite,
  presentacionCrotal,
  presentacionResolucion,
  presentacionRespuestaWhatsapp,
  presentacionRolContacto,
} from "./etiquetas"
import { badgeVarianteDeEstado as badgeVarianteDeEstadoReexportado } from "./types"

describe("TIPOS_TRAMITE", () => {
  it("tiene exactamente los 6 tipos de OVZ del backend, en su orden, con su etiqueta", () => {
    expect(Object.entries(TIPOS_TRAMITE)).toEqual([
      ["ALTA_NACIMIENTO", "Alta por nacimiento"],
      ["BAJA_MUERTE", "Baja por muerte"],
      ["SOLICITUD_MOVIMIENTO", "Solicitud de movimiento"],
      ["CONFIRMACION_MOVIMIENTO", "Confirmación de entrada"],
      ["DECLARACION_CENSO", "Declaración de censo"],
      ["DEMORA_CROTALIZACION", "Demora de crotalización"],
    ])
  })

  it.each([
    ["ALTA_NACIMIENTO", "Alta por nacimiento"],
    ["BAJA_MUERTE", "Baja por muerte"],
    ["SOLICITUD_MOVIMIENTO", "Solicitud de movimiento"],
    ["CONFIRMACION_MOVIMIENTO", "Confirmación de entrada"],
    ["DECLARACION_CENSO", "Declaración de censo"],
    ["DEMORA_CROTALIZACION", "Demora de crotalización"],
  ])("%s se muestra como %s", (tipo, etiqueta) => {
    expect(etiquetaTipoTramite(tipo)).toBe(etiqueta)
  })

  it.each(["ALTA", "BAJA", "CENSO", "MOVIMIENTO", "DEMORA"])(
    "el tipo viejo %s (antes de la V20) ya no es conocido y se muestra tal cual",
    (tipo) => {
      expect(etiquetaTipoTramite(tipo)).toBe(tipo)
    },
  )

  it("un tipo desconocido (p. ej. tras el prompt B) muestra el valor tal cual", () => {
    expect(etiquetaTipoTramite("TRASLADO_FERIA")).toBe("TRASLADO_FERIA")
  })

  it("no confunde claves heredadas del prototipo con tipos", () => {
    expect(etiquetaTipoTramite("toString")).toBe("toString")
    expect(etiquetaTipoTramite("constructor")).toBe("constructor")
  })

  it("nunca devuelve un texto vacío", () => {
    expect(etiquetaTipoTramite("")).toBe("—")
  })
})

describe("ESTADOS_TRAMITE", () => {
  const esperado = {
    PENDIENTE_EXTRACCION: { etiqueta: "Pendiente de extracción", variante: "warning" },
    PENDIENTE_REVISION: { etiqueta: "Pendiente de revisión", variante: "warning" },
    APROBADO: { etiqueta: "Aprobado · falta OVZ", variante: "warning" },
    HECHO_EN_OVZ: { etiqueta: "Hecho en OVZ", variante: "success" },
    EN_PROCESO: { etiqueta: "En proceso", variante: "warning" },
    EJECUTADO_OVZ: { etiqueta: "Hecho en OVZ.net", variante: "success" },
    ERROR_OVZ: { etiqueta: "Error en OVZ.net", variante: "danger" },
    RECHAZADO: { etiqueta: "Rechazado", variante: "danger" },
  }

  it("cubre los 8 estados con su etiqueta y variante", () => {
    expect(ESTADOS_TRAMITE).toEqual(esperado)
  })

  it.each(Object.entries(esperado))("%s → presentación y variante", (estado, presentacion) => {
    expect(presentacionEstado(estado)).toEqual(presentacion)
    expect(badgeVarianteDeEstado(estado)).toBe(presentacion.variante)
  })

  it("un estado desconocido se muestra tal cual con badge neutro", () => {
    expect(presentacionEstado("PAUSADO")).toEqual({ etiqueta: "PAUSADO", variante: "outline" })
    expect(badgeVarianteDeEstado("PAUSADO")).toBe("outline")
    expect(presentacionEstado("hasOwnProperty")).toEqual({
      etiqueta: "hasOwnProperty",
      variante: "outline",
    })
    expect(presentacionEstado("")).toEqual({ etiqueta: "—", variante: "outline" })
  })

  it("badgeVarianteDeEstado sigue exportándose desde types.ts (lo cita DESIGN.md)", () => {
    expect(badgeVarianteDeEstadoReexportado).toBe(badgeVarianteDeEstado)
  })
})

describe("RESOLUCIONES_CROTAL", () => {
  const esperado = {
    EN_INVENTARIO: { etiqueta: "En inventario", variante: "success" },
    AMBIGUO: { etiqueta: "Varios animales coinciden", variante: "warning" },
    NO_ENCONTRADO: { etiqueta: "No está en el inventario", variante: "outline" },
    SIN_EXPLOTACION: { etiqueta: "Falta la explotación", variante: "warning" },
  }

  it("cubre las 4 resoluciones con su etiqueta y variante", () => {
    expect(RESOLUCIONES_CROTAL).toEqual(esperado)
  })

  it.each(Object.entries(esperado))("%s → presentación", (resolucion, presentacion) => {
    expect(presentacionResolucion(resolucion)).toEqual(presentacion)
  })

  it("una resolución desconocida se muestra tal cual con badge neutro", () => {
    expect(presentacionResolucion("DUPLICADO")).toEqual({ etiqueta: "DUPLICADO", variante: "outline" })
  })
})

describe("presentacionCrotal (completo, mini-prompt tras A2; plan antes del piloto, punto 5)", () => {
  it("NO_ENCONTRADO con completo:false → ámbar y lo dice en el texto", () => {
    expect(presentacionCrotal({ resolucion: "NO_ENCONTRADO", completo: false })).toEqual({
      etiqueta: "No está en el inventario · incompleto",
      variante: "warning",
    })
  })

  it("NO_ENCONTRADO con completo:true → neutro como antes", () => {
    expect(presentacionCrotal({ resolucion: "NO_ENCONTRADO", completo: true })).toEqual({
      etiqueta: "No está en el inventario",
      variante: "outline",
    })
  })

  it("D5b: NO_ENCONTRADO sin completo → neutro (el frontend no clasifica crotales)", () => {
    expect(presentacionCrotal({ resolucion: "NO_ENCONTRADO" })).toEqual({
      etiqueta: "No está en el inventario",
      variante: "outline",
    })
  })

  it.each(["EN_INVENTARIO", "AMBIGUO", "SIN_EXPLOTACION"] as const)(
    "%s con completo:false no cambia: completo solo matiza NO_ENCONTRADO",
    (resolucion) => {
      expect(presentacionCrotal({ resolucion, completo: false })).toEqual(RESOLUCIONES_CROTAL[resolucion])
    },
  )

  it("una resolución desconocida sigue saliendo tal cual en neutro, con o sin completo", () => {
    expect(presentacionCrotal({ resolucion: "DUPLICADO", completo: false })).toEqual({
      etiqueta: "DUPLICADO",
      variante: "outline",
    })
  })
})

describe("ROLES_CONTACTO", () => {
  it("tiene los 2 roles del backend", () => {
    expect(ROLES_CONTACTO).toEqual({ TITULAR: "Titular", EMPLEADO: "Empleado" })
  })

  it("etiqueta los conocidos y deja tal cual los desconocidos", () => {
    expect(etiquetaRolContacto("TITULAR")).toBe("Titular")
    expect(etiquetaRolContacto("EMPLEADO")).toBe("Empleado")
    expect(etiquetaRolContacto("VETERINARIO")).toBe("VETERINARIO")
  })
})

describe("presentacionRolContacto", () => {
  it("Titular es éxito y Empleado el neutro, con la etiqueta de ROLES_CONTACTO", () => {
    expect(presentacionRolContacto("TITULAR")).toEqual({ etiqueta: ROLES_CONTACTO.TITULAR, variante: "success" })
    expect(presentacionRolContacto("EMPLEADO")).toEqual({ etiqueta: ROLES_CONTACTO.EMPLEADO, variante: "outline" })
  })

  it("un rol desconocido sale tal cual con badge neutro", () => {
    expect(presentacionRolContacto("VETERINARIO")).toEqual({ etiqueta: "VETERINARIO", variante: "outline" })
  })
})

describe("ORIGENES_TRAMITE / etiquetaOrigen (B1, D7)", () => {
  it("WhatsApp se anuncia como «Recibido por WhatsApp»", () => {
    expect(ORIGENES_TRAMITE).toEqual({ WHATSAPP: "Recibido por WhatsApp" })
    expect(etiquetaOrigen("WHATSAPP")).toBe("Recibido por WhatsApp")
  })

  it("sin origen (trámite anterior a B1) o con uno desconocido no hay indicador", () => {
    expect(etiquetaOrigen(null)).toBeNull()
    expect(etiquetaOrigen(undefined)).toBeNull()
    expect(etiquetaOrigen("EMAIL")).toBeNull()
    expect(etiquetaOrigen("toString")).toBeNull()
  })
})

describe("TEXTOS_EXTRACCION (B1, D7 y brief de T4)", () => {
  it("tiene los textos exactos de la cola y del modal, con su tono", () => {
    expect(TEXTOS_EXTRACCION).toEqual({
      PENDIENTE: { cola: "Extracción en curso", modal: "Extracción en curso", tono: "neutro" },
      FALLIDA: {
        cola: "No se ha podido extraer",
        modal: "No se ha podido extraer automáticamente. Revisa el mensaje original.",
        tono: "aviso",
      },
      SIN_TEXTO: {
        cola: "Mensaje sin texto",
        modal: "El mensaje no tiene texto; puede traer una foto o un audio, que todavía no se procesan.",
        tono: "aviso",
      },
      SIN_TRAMITE: {
        cola: "Parece un mensaje sin trámite",
        modal:
          "Parece un mensaje sin trámite: un saludo, un agradecimiento o una pregunta. Si hay que contestar, responde antes de descartar.",
        tono: "neutro",
      },
    })
  })
})

describe("aviso SIN_TRAMITE: honestidad del texto", () => {
  // La misma lista que el test de honestidad del modal (CLAUDE.md: nada insinúa que Ganera hizo algo fuera).
  const PROHIBIDAS = /enviad|ejecutad|registrad|en curso en OVZ|tramitad|completad|procesad|presentad/i

  it("ni la línea de la cola ni el aviso del modal dicen nada prohibido ni mencionan OVZ", () => {
    const { cola, modal } = TEXTOS_EXTRACCION.SIN_TRAMITE
    for (const texto of [cola, modal]) {
      expect(texto).not.toMatch(PROHIBIDAS)
      expect(texto).not.toMatch(/OVZ/)
    }
  })
})

describe("avisosExtraccion (B1: regla de visibilidad, plurales)", () => {
  const PENDIENTES = ["PENDIENTE_EXTRACCION", "PENDIENTE_REVISION"] as const
  const NO_PENDIENTES = ["APROBADO", "HECHO_EN_OVZ", "EN_PROCESO", "EJECUTADO_OVZ", "ERROR_OVZ", "RECHAZADO"] as const
  // SIN_TRAMITE tiene su propia regla (solo con `descartable`): ver el describe de SIN_TRAMITE.
  const CONOCIDOS = ["PENDIENTE", "FALLIDA", "SIN_TEXTO"] as const

  for (const estado of PENDIENTES) {
    it.each(CONOCIDOS)(`con estado ${estado} y extracción %s, devuelve su aviso`, (estadoExtraccion) => {
      expect(avisosExtraccion({ estado, estadoExtraccion, crotalesDescartados: 0 })).toEqual({
        extraccion: TEXTOS_EXTRACCION[estadoExtraccion],
        descartados: null,
      })
    })

    it(`con estado ${estado}: COMPLETADA, null, desconocido o vacío no dan aviso de extracción`, () => {
      for (const estadoExtraccion of ["COMPLETADA", null, undefined, "OTRA_COSA", "", "toString"]) {
        expect(avisosExtraccion({ estado, estadoExtraccion, crotalesDescartados: 0 })).toEqual({
          extraccion: null,
          descartados: null,
        })
      }
    })
  }

  for (const estado of NO_PENDIENTES) {
    it(`con estado ${estado} no hay ningún aviso, aunque la extracción fallara o hubiera descartados`, () => {
      for (const estadoExtraccion of [...CONOCIDOS, "COMPLETADA", null]) {
        expect(avisosExtraccion({ estado, estadoExtraccion, crotalesDescartados: 3 })).toEqual({
          extraccion: null,
          descartados: null,
        })
      }
    })
  }

  it("un estado de trámite desconocido tampoco muestra avisos", () => {
    expect(
      avisosExtraccion({ estado: "ARCHIVADO", estadoExtraccion: "FALLIDA", crotalesDescartados: 2 }),
    ).toEqual({ extraccion: null, descartados: null })
  })

  it("descartados: singular con 1", () => {
    expect(
      avisosExtraccion({ estado: "PENDIENTE_REVISION", estadoExtraccion: "COMPLETADA", crotalesDescartados: 1 }),
    ).toEqual({
      extraccion: null,
      descartados: {
        cola: "1 descartado",
        modal: "Hay 1 identificador que no parece un crotal; revisa el mensaje.",
      },
    })
  })

  it.each([2, 7, 51])("descartados: plural con %i", (n) => {
    expect(
      avisosExtraccion({ estado: "PENDIENTE_REVISION", estadoExtraccion: null, crotalesDescartados: n }),
    ).toEqual({
      extraccion: null,
      descartados: {
        cola: `${n} descartados`,
        modal: `Hay ${n} identificadores que no parecen crotales; revisa el mensaje.`,
      },
    })
  })

  it("0, ausente, negativo o no entero no muestran aviso de descartados", () => {
    for (const crotalesDescartados of [0, undefined, null, -1, 1.5, Number.NaN]) {
      expect(
        avisosExtraccion({
          estado: "PENDIENTE_REVISION",
          estadoExtraccion: "COMPLETADA",
          crotalesDescartados: crotalesDescartados as number,
        }).descartados,
      ).toBeNull()
    }
  })

  it("el aviso de extracción y el de descartados conviven", () => {
    const avisos = avisosExtraccion({
      estado: "PENDIENTE_REVISION",
      estadoExtraccion: "FALLIDA",
      crotalesDescartados: 2,
    })
    expect(avisos.extraccion).toEqual(TEXTOS_EXTRACCION.FALLIDA)
    expect(avisos.descartados?.cola).toBe("2 descartados")
  })
})

describe("presentacionRespuestaWhatsapp (B2 reducido)", () => {
  const PROHIBIDAS = /enviad|ejecutad|registrad|en curso en OVZ|tramitad|completad|procesad|presentad/i

  it("etiquetas y variantes fijadas", () => {
    expect(presentacionRespuestaWhatsapp("ENVIANDO")).toEqual({ etiqueta: "Enviando…", variante: "outline" })
    expect(presentacionRespuestaWhatsapp("ENVIADO")).toEqual({ etiqueta: "Aceptado por WhatsApp", variante: "outline" })
    expect(presentacionRespuestaWhatsapp("ENTREGADO")).toEqual({ etiqueta: "Entregado", variante: "success" })
    expect(presentacionRespuestaWhatsapp("LEIDO")).toEqual({ etiqueta: "Leído", variante: "success" })
    expect(presentacionRespuestaWhatsapp("FALLIDO")).toEqual({ etiqueta: "No ha llegado", variante: "danger" })
    expect(presentacionRespuestaWhatsapp("SIN_CONFIRMAR")).toEqual({ etiqueta: "Sin confirmar", variante: "warning" })
  })

  it("ninguna etiqueta dice «enviado» ni nada de la lista prohibida", () => {
    for (const { etiqueta } of Object.values(ESTADOS_RESPUESTA_WHATSAPP)) expect(etiqueta).not.toMatch(PROHIBIDAS)
  })

  it("desconocido: tal cual y neutro", () => {
    expect(presentacionRespuestaWhatsapp("NUEVO")).toEqual({ etiqueta: "NUEVO", variante: "outline" })
  })
})

describe("SIN_TRAMITE: el aviso depende de `descartable`, que decide el backend (D3)", () => {
  it("PENDIENTE_REVISION + SIN_TRAMITE + descartable: el aviso neutro", () => {
    expect(
      avisosExtraccion({
        estado: "PENDIENTE_REVISION",
        estadoExtraccion: "SIN_TRAMITE",
        crotalesDescartados: 0,
        descartable: true,
      }),
    ).toEqual({ extraccion: TEXTOS_EXTRACCION.SIN_TRAMITE, descartados: null })
  })

  it("SIN_TRAMITE sin `descartable` (el gestor ya le puso tipo o crotales, o falta el campo): sin aviso", () => {
    for (const descartable of [false, undefined, null]) {
      expect(
        avisosExtraccion({
          estado: "PENDIENTE_REVISION",
          estadoExtraccion: "SIN_TRAMITE",
          crotalesDescartados: 0,
          descartable: descartable as boolean | undefined,
        }).extraccion,
      ).toBeNull()
    }
  })

  it("`descartable` no hace aparecer el aviso con otra extracción ni cambia los demás avisos", () => {
    expect(
      avisosExtraccion({ estado: "PENDIENTE_REVISION", estadoExtraccion: "FALLIDA", crotalesDescartados: 0, descartable: true })
        .extraccion,
    ).toEqual(TEXTOS_EXTRACCION.FALLIDA)
    expect(
      avisosExtraccion({ estado: "PENDIENTE_REVISION", estadoExtraccion: "COMPLETADA", crotalesDescartados: 0, descartable: true })
        .extraccion,
    ).toBeNull()
  })

  it("descartado (RECHAZADO): sin aviso aunque siga SIN_TRAMITE", () => {
    expect(
      avisosExtraccion({ estado: "RECHAZADO", estadoExtraccion: "SIN_TRAMITE", crotalesDescartados: 0, descartable: true }),
    ).toEqual({ extraccion: null, descartados: null })
  })
})

describe("presentacionEstadoTramite: «Descartado» (SIN_TRAMITE)", () => {
  it("RECHAZADO + motivoRechazo SIN_TRAMITE → «Descartado», neutro (no danger)", () => {
    expect(presentacionEstadoTramite({ estado: "RECHAZADO", motivoRechazo: "SIN_TRAMITE" })).toEqual({
      etiqueta: "Descartado",
      variante: "outline",
    })
    expect(esDescartado({ estado: "RECHAZADO", motivoRechazo: "SIN_TRAMITE" })).toBe(true)
  })

  it("RECHAZADO sin motivo, o con un código desconocido → «Rechazado» como siempre", () => {
    for (const motivoRechazo of [null, undefined, "OTRO_MOTIVO", "", "toString"]) {
      expect(presentacionEstadoTramite({ estado: "RECHAZADO", motivoRechazo })).toEqual(ESTADOS_TRAMITE.RECHAZADO)
      expect(esDescartado({ estado: "RECHAZADO", motivoRechazo })).toBe(false)
    }
  })

  it("el motivo solo cuenta en RECHAZADO: otro estado se presenta como siempre", () => {
    expect(presentacionEstadoTramite({ estado: "PENDIENTE_REVISION", motivoRechazo: "SIN_TRAMITE" })).toEqual(
      ESTADOS_TRAMITE.PENDIENTE_REVISION,
    )
    expect(esDescartado({ estado: "PENDIENTE_REVISION", motivoRechazo: "SIN_TRAMITE" })).toBe(false)
    expect(presentacionEstadoTramite({ estado: "PAUSADO", motivoRechazo: null })).toEqual({
      etiqueta: "PAUSADO",
      variante: "outline",
    })
  })
})

describe("iconoTipoTramite (diseño de Álvaro, fase 1, T4)", () => {
  it.each([
    ["ALTA_NACIMIENTO", "log-in"],
    ["BAJA_MUERTE", "log-out"],
    ["SOLICITUD_MOVIMIENTO", "truck"],
    ["CONFIRMACION_MOVIMIENTO", "arrow-down-to-line"],
    ["DECLARACION_CENSO", "list-ordered"],
    ["DEMORA_CROTALIZACION", "tag"],
  ])("%s → %s", (tipo, icono) => {
    expect(iconoTipoTramite(tipo)).toBe(icono)
  })

  it("cada tipo de TIPOS_TRAMITE tiene icono (si se añade un tipo, hay que darle uno)", () => {
    for (const tipo of Object.keys(TIPOS_TRAMITE)) expect(iconoTipoTramite(tipo)).not.toBeNull()
  })

  it.each([null, undefined, "", "   ", "ALTA", "TRASLADO_FERIA", "toString", "constructor"])(
    "%j no tiene icono",
    (tipo) => {
      expect(iconoTipoTramite(tipo)).toBeNull()
    },
  )
})

describe("iconoEstadoTramite (diseño de Álvaro, fase 1, T4)", () => {
  it.each([
    ["PENDIENTE_EXTRACCION", "sparkles"],
    ["PENDIENTE_REVISION", "eye"],
    ["APROBADO", "check"],
    ["HECHO_EN_OVZ", "circle-check"],
    ["RECHAZADO", "x"],
  ])("%s → %s", (estado, icono) => {
    expect(iconoEstadoTramite({ estado })).toBe(icono)
  })

  it("un descartado (RECHAZADO + SIN_TRAMITE) lleva «archive», no la «x» del rechazado", () => {
    expect(iconoEstadoTramite({ estado: "RECHAZADO", motivoRechazo: "SIN_TRAMITE" })).toBe("archive")
  })

  it("RECHAZADO con otro motivo o sin motivo sigue con la «x»", () => {
    expect(iconoEstadoTramite({ estado: "RECHAZADO", motivoRechazo: "OTRO" })).toBe("x")
    expect(iconoEstadoTramite({ estado: "RECHAZADO", motivoRechazo: null })).toBe("x")
  })

  it("SIN_TRAMITE en un estado que no es RECHAZADO no es descartado", () => {
    expect(iconoEstadoTramite({ estado: "PENDIENTE_REVISION", motivoRechazo: "SIN_TRAMITE" })).toBe("eye")
  })

  it.each(["EN_PROCESO", "EJECUTADO_OVZ", "ERROR_OVZ"])("el reservado %s no lleva icono", (estado) => {
    expect(iconoEstadoTramite({ estado })).toBeNull()
  })

  it.each(["PAUSADO", "", "toString", "constructor"])("el desconocido %j no lleva icono", (estado) => {
    expect(iconoEstadoTramite({ estado })).toBeNull()
  })
})
