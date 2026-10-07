import { describe, expect, it } from "vitest"
import { ESTADOS_TRAMITE, TIPOS_TRAMITE, type EstadoTramite } from "./etiquetas"
import type { TramiteCrotal, TramiteDetalle } from "./types"
import {
  construirFichaOvz,
  modoFicha,
  PISTA_CROTAL_COMPLETO,
  PISTA_CROTAL_MADRE,
  PISTA_CROTAL_TERNERO,
  PISTA_EXPLOTACION,
  PISTA_GESTOR,
  PISTA_LISTA_OVZ,
  PISTA_MENSAJE,
  PISTA_PREFERENCIA,
  type CampoFicha,
  type FichaOvz,
  type ResultadoFicha,
} from "./fichaOvz"

function enInventario(crotalIndicado: string, crotal: string): TramiteCrotal {
  return { crotalIndicado, crotal, animalId: 1, enInventario: true, resolucion: "EN_INVENTARIO", completo: false }
}

function noEncontrado(crotal: string, completo: boolean | undefined): TramiteCrotal {
  return { crotalIndicado: crotal, crotal, animalId: null, enInventario: false, resolucion: "NO_ENCONTRADO", completo }
}

function ambiguo(crotal: string): TramiteCrotal {
  return { crotalIndicado: crotal, crotal, animalId: null, enInventario: false, resolucion: "AMBIGUO", completo: false }
}

const C1 = enInventario("1234", "ES010000001234")
const C2 = enInventario("5678", "ES010000005678")
const C3 = noEncontrado("ES010000009999", true)

function detalle(parcial: Partial<TramiteDetalle> = {}): TramiteDetalle {
  return {
    id: 7,
    tipoTramite: "BAJA_MUERTE",
    estado: "APROBADO",
    motivoError: null,
    explotacionId: 3,
    explotacionCodigoRega: "ES060010000001",
    explotacionNombre: "La Dehesa",
    ganaderoNombre: "Ana Martínez",
    ganaderoNif: "12345678Z",
    mensajeOriginal: "se ha muerto la 1234",
    crotales: [C1],
    version: 4,
    origen: "WHATSAPP",
    estadoExtraccion: "COMPLETADA",
    crotalesDescartados: 0,
    ...parcial,
  }
}

function ficha(parcial: Partial<TramiteDetalle> = {}): FichaOvz {
  const resultado = construirFichaOvz(detalle(parcial))
  if (resultado.tipo !== "ficha") throw new Error(`se esperaba una ficha, llegó ${resultado.tipo}`)
  return resultado.ficha
}

function etiquetas(f: FichaOvz, bloque = 0): string[] {
  return f.bloques[bloque].campos.map((c) => c.etiqueta)
}

function campo(f: FichaOvz, id: string): CampoFicha {
  const encontrado = [f.explotacion, ...f.bloques.flatMap((b) => b.campos)].find((c) => c.id === id)
  if (!encontrado) throw new Error(`no hay campo ${id}`)
  return encontrado
}

function todosLosCampos(f: FichaOvz): CampoFicha[] {
  return [f.explotacion, ...f.bloques.flatMap((b) => b.campos)]
}

const CROTALES_VARIADOS = [C1, C2, C3]
const TIPOS_CON_FICHA = [
  "BAJA_MUERTE",
  "ALTA_NACIMIENTO",
  "SOLICITUD_MOVIMIENTO",
  "CONFIRMACION_MOVIMIENTO",
  "DEMORA_CROTALIZACION",
] as const

describe("modoFicha (D4)", () => {
  it.each<[EstadoTramite, string]>([
    ["APROBADO", "copiable"],
    ["PENDIENTE_REVISION", "vista-previa"],
    ["RECHAZADO", "rechazado"],
    ["PENDIENTE_EXTRACCION", "extraccion"],
    ["EN_PROCESO", "no-disponible"],
    ["EJECUTADO_OVZ", "no-disponible"],
    ["ERROR_OVZ", "no-disponible"],
  ])("%s → %s", (estado, modo) => {
    expect(modoFicha(estado)).toBe(modo)
  })

  it("cubre los 7 estados", () => {
    expect(Object.keys(ESTADOS_TRAMITE)).toHaveLength(7)
  })

  it("un estado desconocido no permite copiar", () => {
    expect(modoFicha("OTRO" as EstadoTramite)).toBe("no-disponible")
  })
})

describe("cabecera común: cuenta y explotación", () => {
  it("lleva el nombre y el NIF del titular y el REGA con su etiqueta de OVZ", () => {
    const f = ficha()
    expect(f.cuenta).toEqual({ nombre: "Ana Martínez", nif: "12345678Z" })
    expect(f.explotacion).toEqual({
      id: "explotacion",
      etiqueta: "Explotación",
      obligatorio: true,
      valor: "ES060010000001",
      pista: null,
      escrito: null,
    })
  })

  it("sin explotación ni cuenta: valores null y la pista de asignarla en Ganera", () => {
    const f = ficha({
      explotacionId: null,
      explotacionCodigoRega: null,
      explotacionNombre: null,
      ganaderoNombre: null,
      ganaderoNif: null,
    })
    expect(f.cuenta).toEqual({ nombre: null, nif: null })
    expect(f.explotacion.valor).toBeNull()
    expect(f.explotacion.pista).toBe(PISTA_EXPLOTACION)
    expect(PISTA_EXPLOTACION).toBe("Asigna la explotación en Ganera")
  })

  it("recorta espacios y trata un texto vacío como null", () => {
    const f = ficha({ explotacionCodigoRega: "  ES1  ", ganaderoNombre: "  ", ganaderoNif: " 1Z " })
    expect(f.explotacion.valor).toBe("ES1")
    expect(f.cuenta).toEqual({ nombre: null, nif: "1Z" })
  })
})

describe("pistas", () => {
  it("tienen los textos del brief", () => {
    expect(PISTA_MENSAJE).toBe("Está en el mensaje")
    expect(PISTA_LISTA_OVZ).toBe("Elígelo en la lista de OVZ")
    expect(PISTA_PREFERENCIA).toBe("Preferencia de la gestoría")
    expect(PISTA_GESTOR).toBe("Lo rellenas tú en OVZ")
    expect(PISTA_CROTAL_COMPLETO).toBe("Falta el crotal completo")
    expect(PISTA_CROTAL_TERNERO).toBe("Uno de los crotales de arriba, o elígelo en la lista de OVZ")
    expect(PISTA_CROTAL_MADRE).toBe("Uno de los crotales de arriba")
  })
})

describe("crotales (D7)", () => {
  it("EN_INVENTARIO copia el crotal completo, no lo escrito", () => {
    const f = ficha({ crotales: [C1] })
    expect(campo(f, "baja-1-crotal")).toMatchObject({ valor: "ES010000001234", pista: null })
  })

  it("NO_ENCONTRADO completo copia el crotal", () => {
    const f = ficha({ crotales: [noEncontrado("ES010000009999", true)] })
    expect(campo(f, "baja-1-crotal")).toMatchObject({ valor: "ES010000009999", pista: null })
  })

  it("NO_ENCONTRADO incompleto falta", () => {
    const f = ficha({ crotales: [noEncontrado("9999", false)] })
    expect(campo(f, "baja-1-crotal")).toMatchObject({ valor: null, pista: PISTA_CROTAL_COMPLETO })
  })

  it("NO_ENCONTRADO sin `completo` falta (no se adivina)", () => {
    const f = ficha({ crotales: [noEncontrado("ES010000009999", undefined)] })
    expect(campo(f, "baja-1-crotal")).toMatchObject({ valor: null, pista: PISTA_CROTAL_COMPLETO })
  })

  it("AMBIGUO falta", () => {
    const f = ficha({ crotales: [ambiguo("1234")] })
    expect(campo(f, "baja-1-crotal")).toMatchObject({ valor: null, pista: PISTA_CROTAL_COMPLETO })
  })

  it("SIN_EXPLOTACION falta", () => {
    const f = ficha({
      crotales: [{ ...ambiguo("1234"), resolucion: "SIN_EXPLOTACION" }],
    })
    expect(campo(f, "baja-1-crotal")).toMatchObject({ valor: null, pista: PISTA_CROTAL_COMPLETO })
  })

  it("`escrito` lleva lo escrito aunque no se haya resuelto; la pista sigue siendo la constante", () => {
    const f = ficha({ crotales: [ambiguo("1234")] })
    expect(campo(f, "baja-1-crotal")).toMatchObject({ valor: null, pista: PISTA_CROTAL_COMPLETO, escrito: "1234" })
  })

  it("`escrito` acompaña también a un crotal resuelto, y no es lo que se copia", () => {
    expect(campo(ficha({ crotales: [C1] }), "baja-1-crotal")).toMatchObject({
      valor: "ES010000001234",
      escrito: "1234",
    })
  })

  it("`escrito` se recorta y un texto en blanco da null", () => {
    const f = ficha({
      tipoTramite: "DEMORA_CROTALIZACION",
      crotales: [enInventario(" 1234 ", "ES010000001234"), { ...ambiguo("5678"), crotalIndicado: "   " }],
    })
    expect(campo(f, "demora-animal-1").escrito).toBe("1234")
    expect(campo(f, "demora-animal-2")).toMatchObject({ escrito: null, valor: null, pista: PISTA_CROTAL_COMPLETO })
  })

  it("solo recorta: no reformatea", () => {
    const f = ficha({ crotales: [enInventario("1234", " ES010000001234 ")] })
    expect(campo(f, "baja-1-crotal").valor).toBe("ES010000001234")
  })

  it("los crotales de un movimiento siguen la misma regla", () => {
    const f = ficha({ tipoTramite: "SOLICITUD_MOVIMIENTO", crotales: [C1, ambiguo("4321")] })
    expect(campo(f, "solicitud-animal-1").valor).toBe("ES010000001234")
    expect(campo(f, "solicitud-animal-2")).toMatchObject({ valor: null, pista: PISTA_CROTAL_COMPLETO })
  })
})

describe("BAJA_MUERTE: Baja de Bovino", () => {
  const ORDEN_BAJA = [
    "Crotal",
    "Fecha de muerte",
    "Nº Documento MER",
    "Oficina para entregar la copia del ejemplar MER",
  ]

  it("formulario, ruta y avisos", () => {
    const f = ficha()
    expect(f.formulario).toBe("Baja de Bovino")
    expect(f.rutaOvz).toBe("Trámites › Bóvidos › Baja")
    expect(f.avisos).toEqual([
      "OVZ rellena solo los datos del animal (raza, sexo, fecha de nacimiento…)",
      "Plazo: 7 días desde la muerte",
      "Entrega la copia del ejemplar MER en la OVZ en 15 días",
    ])
  })

  it("un crotal: un bloque titulado «Baja» con los campos en el orden de OVZ", () => {
    const f = ficha({ crotales: [C1] })
    expect(f.bloques).toHaveLength(1)
    expect(f.bloques[0].titulo).toBe("Baja")
    expect(etiquetas(f)).toEqual(ORDEN_BAJA)
  })

  it("obligatorios y pistas de cada campo", () => {
    const f = ficha({ crotales: [C1] })
    const ninguno = { valor: null, escrito: null }
    expect(f.bloques[0].campos).toEqual([
      { id: "baja-1-crotal", etiqueta: "Crotal", obligatorio: true, valor: C1.crotal, pista: null, escrito: "1234" },
      { id: "baja-1-fecha-muerte", etiqueta: "Fecha de muerte", obligatorio: true, pista: PISTA_MENSAJE, ...ninguno },
      { id: "baja-1-mer", etiqueta: "Nº Documento MER", obligatorio: true, pista: PISTA_GESTOR, ...ninguno },
      {
        id: "baja-1-oficina-mer",
        etiqueta: "Oficina para entregar la copia del ejemplar MER",
        obligatorio: true,
        pista: PISTA_PREFERENCIA,
        ...ninguno,
      },
    ])
  })

  it("tres crotales: tres bloques «Baja N de 3», cada uno con su crotal", () => {
    const f = ficha({ crotales: CROTALES_VARIADOS })
    expect(f.bloques.map((b) => b.titulo)).toEqual(["Baja 1 de 3", "Baja 2 de 3", "Baja 3 de 3"])
    expect(f.bloques.map((b) => b.campos[0].valor)).toEqual([
      "ES010000001234",
      "ES010000005678",
      "ES010000009999",
    ])
    for (let i = 0; i < 3; i++) expect(etiquetas(f, i)).toEqual(ORDEN_BAJA)
  })

  it("sin crotales: un único bloque con el crotal como falta", () => {
    const f = ficha({ crotales: [] })
    expect(f.bloques).toHaveLength(1)
    expect(f.bloques[0].titulo).toBe("Baja")
    expect(etiquetas(f)).toEqual(ORDEN_BAJA)
    expect(f.bloques[0].campos[0]).toMatchObject({ valor: null, pista: PISTA_MENSAJE })
  })
})

describe("ALTA_NACIMIENTO: Alta de Bóvidos", () => {
  const ORDEN_ALTA_OVZ = [
    "Crotal del ternero",
    "Crotal de la Madre",
    "Fecha Nacimiento",
    "Identificador de Lidia",
    "Sexo",
    "Fecha Identificación",
    "Tipo Identificador",
    "Raza",
    "Emisión del DI",
    "Oficina de recogida o envío por correo",
  ]

  it("primero el bloque «Crotales del trámite» (no es de OVZ) y luego «Alta» en el orden de OVZ", () => {
    const f = ficha({ tipoTramite: "ALTA_NACIMIENTO", crotales: [C1, C3] })
    expect(f.formulario).toBe("Alta de Bóvidos")
    expect(f.rutaOvz).toBe("Trámites › Bóvidos › Alta")
    expect(f.bloques.map((b) => [b.id, b.titulo])).toEqual([
      ["alta-crotales", "Crotales del trámite"],
      ["alta", "Alta"],
    ])
    expect(f.bloques[0].campos).toEqual([
      { id: "alta-crotal-1", etiqueta: "Crotal 1", obligatorio: false, valor: C1.crotal, pista: null, escrito: "1234" },
      {
        id: "alta-crotal-2",
        etiqueta: "Crotal 2",
        obligatorio: false,
        valor: "ES010000009999",
        pista: null,
        escrito: "ES010000009999",
      },
    ])
    expect(etiquetas(f, 1)).toEqual(ORDEN_ALTA_OVZ)
  })

  it("obligatorios y pistas", () => {
    const f = ficha({ tipoTramite: "ALTA_NACIMIENTO", crotales: [C1] })
    const resumen = f.bloques[1].campos.map((c) => [c.etiqueta, c.obligatorio, c.pista])
    expect(resumen).toEqual([
      ["Crotal del ternero", true, PISTA_CROTAL_TERNERO],
      ["Crotal de la Madre", true, PISTA_CROTAL_MADRE],
      ["Fecha Nacimiento", true, PISTA_MENSAJE],
      ["Identificador de Lidia", false, PISTA_GESTOR],
      ["Sexo", true, PISTA_MENSAJE],
      ["Fecha Identificación", true, PISTA_MENSAJE],
      ["Tipo Identificador", true, PISTA_PREFERENCIA],
      ["Raza", true, PISTA_LISTA_OVZ],
      ["Emisión del DI", true, PISTA_PREFERENCIA],
      ["Oficina de recogida o envío por correo", true, PISTA_PREFERENCIA],
    ])
  })

  it("sin crotales: no remite a «los crotales de arriba»", () => {
    const f = ficha({ tipoTramite: "ALTA_NACIMIENTO", crotales: [] })
    expect(f.bloques.map((b) => b.id)).toEqual(["alta"])
    expect(etiquetas(f)).toEqual(ORDEN_ALTA_OVZ)
    expect(campo(f, "alta-crotal-ternero").pista).toBe(PISTA_LISTA_OVZ)
    expect(campo(f, "alta-crotal-madre").pista).toBe(PISTA_MENSAJE)
  })
})

describe("SOLICITUD_MOVIMIENTO: Solicitud de Movimiento", () => {
  it("bloques de OVZ y campos en el orden del §2.3", () => {
    const f = ficha({ tipoTramite: "SOLICITUD_MOVIMIENTO", crotales: [C1, C2] })
    expect(f.formulario).toBe("Solicitud de Movimiento")
    expect(f.rutaOvz).toBe("Movimientos › Solicitud de Movimientos › Bóvidos › Solicitud de Movimiento")
    expect(f.bloques.map((b) => b.titulo)).toEqual(["Origen", "Destino", "Datos de la guía", "Animales"])
    expect(etiquetas(f, 0)).toEqual(["Teléfono", "Autorizo envío de SMS"])
    expect(etiquetas(f, 1)).toEqual(["País", "Comunidad Autónoma", "Código REGA", "Consignatario"])
    expect(etiquetas(f, 2)).toEqual([
      "Fecha de Salida",
      "Fecha de Llegada",
      "Medio de Transporte",
      "Identificador del medio de transporte (Matrícula)",
      "Transportista (Código SIRENTRA)",
      "Tipo Responsable del Movimiento",
      "Aptitud del Movimiento",
      "Guía Temporal",
    ])
    expect(etiquetas(f, 3)).toEqual(["Animal 1", "Animal 2"])
  })

  it("obligatorios y pistas", () => {
    const f = ficha({ tipoTramite: "SOLICITUD_MOVIMIENTO", crotales: [C1] })
    const resumen = f.bloques.slice(0, 3).flatMap((b) => b.campos.map((c) => [c.etiqueta, c.obligatorio, c.pista]))
    expect(resumen).toEqual([
      ["Teléfono", true, PISTA_PREFERENCIA],
      ["Autorizo envío de SMS", false, PISTA_GESTOR],
      ["País", false, PISTA_GESTOR],
      ["Comunidad Autónoma", false, PISTA_GESTOR],
      ["Código REGA", true, PISTA_MENSAJE],
      ["Consignatario", true, PISTA_MENSAJE],
      ["Fecha de Salida", true, PISTA_MENSAJE],
      ["Fecha de Llegada", true, PISTA_GESTOR],
      ["Medio de Transporte", false, PISTA_PREFERENCIA],
      ["Identificador del medio de transporte (Matrícula)", false, PISTA_PREFERENCIA],
      ["Transportista (Código SIRENTRA)", false, PISTA_PREFERENCIA],
      ["Tipo Responsable del Movimiento", false, PISTA_PREFERENCIA],
      ["Aptitud del Movimiento", false, PISTA_MENSAJE],
      ["Guía Temporal", false, PISTA_GESTOR],
    ])
    expect(campo(f, "solicitud-animal-1")).toMatchObject({ obligatorio: true, valor: "ES010000001234" })
  })

  it("avisos de plazo del manual", () => {
    expect(ficha({ tipoTramite: "SOLICITUD_MOVIMIENTO" }).avisos).toEqual([
      "Pídela con al menos 48 h de antelación: con menos, puede que OVZ no la tramite",
      "Una guía firmada y no emitida caduca a los 5 días",
      "OVZ no deja pedir guías si hay entradas sin confirmar desde hace más de 13 días",
    ])
  })

  it("sin crotales: un animal como falta", () => {
    const f = ficha({ tipoTramite: "SOLICITUD_MOVIMIENTO", crotales: [] })
    expect(f.bloques[3].campos).toEqual([
      {
        id: "solicitud-animal-1",
        etiqueta: "Animal 1",
        obligatorio: true,
        valor: null,
        pista: PISTA_MENSAJE,
        escrito: null,
      },
    ])
  })
})

describe("CONFIRMACION_MOVIMIENTO: Confirmación de Movimientos", () => {
  it("guía, animales que llegaron y oficina, en ese orden", () => {
    const f = ficha({ tipoTramite: "CONFIRMACION_MOVIMIENTO", crotales: [C1, C2] })
    expect(f.formulario).toBe("Confirmación de Movimientos")
    expect(f.rutaOvz).toBe("Movimientos › Confirmación")
    expect(f.bloques.map((b) => b.titulo)).toEqual(["Guía", "Animales que llegaron", "Documentos de identificación"])
    expect(f.bloques.flatMap((b) => b.campos.map((c) => [c.etiqueta, c.valor, c.pista]))).toEqual([
      ["Guía o Código REMO", null, PISTA_GESTOR],
      ["Animal 1", "ES010000001234", null],
      ["Animal 2", "ES010000005678", null],
      ["Oficina para los DI", null, PISTA_PREFERENCIA],
    ])
    expect(f.avisos).toEqual([
      "En OVZ, pestaña Internos si viene de Extremadura y Externos si viene de otra comunidad",
      "Marca Confirmar en estos animales y Rechazar en los demás",
      "Plazo: 7 días desde la entrada",
    ])
  })
})

describe("DEMORA_CROTALIZACION: Animales con Demora", () => {
  it("un campo por crotal y la fecha de identificación", () => {
    const f = ficha({ tipoTramite: "DEMORA_CROTALIZACION", crotales: [C1, C2] })
    expect(f.formulario).toBe("Animales con Demora")
    expect(f.rutaOvz).toBe("Trámites › Demora")
    expect(f.bloques).toHaveLength(1)
    expect(f.bloques[0].campos.map((c) => [c.etiqueta, c.obligatorio, c.pista])).toEqual([
      ["Animal 1", true, null],
      ["Animal 2", true, null],
      ["Fecha de Identificación", true, PISTA_MENSAJE],
    ])
  })
})

describe("resultados sin ficha", () => {
  it("DECLARACION_CENSO: sin preparar", () => {
    expect(construirFichaOvz(detalle({ tipoTramite: "DECLARACION_CENSO" }))).toEqual<ResultadoFicha>({
      tipo: "sin-preparar",
      formulario: "Declaración de Censo",
    })
  })

  it("sin tipo", () => {
    expect(construirFichaOvz(detalle({ tipoTramite: null }))).toEqual({ tipo: "sin-tipo" })
    expect(construirFichaOvz(detalle({ tipoTramite: "  " }))).toEqual({ tipo: "sin-tipo" })
  })

  it("tipo desconocido, sin lanzar (tampoco con nombres de Object.prototype)", () => {
    for (const valor of ["TRASLADO", "toString", "BAJA"]) {
      expect(construirFichaOvz(detalle({ tipoTramite: valor }))).toEqual({ tipo: "tipo-desconocido", valor })
    }
  })

  it("crotales ausentes en un JSON inesperado no rompen", () => {
    const f = ficha({ crotales: null as unknown as TramiteCrotal[] })
    expect(f.bloques[0].campos[0].valor).toBeNull()
  })
})

describe("invariantes en todas las fichas", () => {
  const casos = TIPOS_CON_FICHA.flatMap((tipo) =>
    [[], [C1], CROTALES_VARIADOS, [C1, ambiguo("4321"), noEncontrado("77", false)]].map(
      (crotales) => [tipo, crotales.length, ficha({ tipoTramite: tipo, crotales }), crotales] as const,
    ),
  )

  it("cubre todos los tipos de TIPOS_TRAMITE salvo el censo", () => {
    expect(Object.keys(TIPOS_TRAMITE).filter((t) => t !== "DECLARACION_CENSO").sort()).toEqual(
      [...TIPOS_CON_FICHA].sort(),
    )
  })

  it.each(casos)("%s con %i crotales: ids de campos y bloques únicos en conjunto", (_tipo, _n, f) => {
    const todos = [...todosLosCampos(f).map((c) => c.id), ...f.bloques.map((b) => b.id)]
    expect(new Set(todos).size).toBe(todos.length)
    expect(todos.every((id) => id.length > 0)).toBe(true)
  })

  it.each(casos)("%s con %i crotales: `escrito` solo en los campos de crotal", (_tipo, _n, f, crotales) => {
    expect(f.explotacion.escrito).toBeNull()
    const escritos = f.bloques
      .flatMap((b) => b.campos)
      .filter((c) => c.escrito !== null)
      .map((c) => c.escrito)
    expect(escritos).toEqual(crotales.map((c) => c.crotalIndicado.trim()))
  })

  it.each(casos)("%s con %i crotales: valor y pista se excluyen y nunca faltan los dos", (_tipo, _n, f) => {
    for (const c of todosLosCampos(f)) {
      if (c.valor !== null) {
        expect(c.valor.trim()).not.toBe("")
        expect(c.pista, c.id).toBeNull()
      } else {
        expect(c.pista, c.id).toBeTruthy()
      }
    }
  })

  it("ids únicos también entre fichas de tipos distintos (prefijo por formulario)", () => {
    const porTipo = TIPOS_CON_FICHA.map(
      (tipo) => new Set(todosLosCampos(ficha({ tipoTramite: tipo, crotales: [C1] })).map((c) => c.id)),
    )
    for (let i = 0; i < porTipo.length; i++)
      for (let j = i + 1; j < porTipo.length; j++) {
        const comunes = [...porTipo[i]].filter((id) => porTipo[j].has(id))
        expect(comunes).toEqual(["explotacion"])
      }
  })

  it("no inventa datos: sin el mensaje ni datos de OVZ, solo hay valor en explotación y crotales", () => {
    for (const [, , f] of casos) {
      for (const c of f.bloques.flatMap((b) => b.campos)) {
        if (c.valor !== null) expect(c.valor).toMatch(/^ES0100000\d{5}$/)
      }
    }
  })
})
