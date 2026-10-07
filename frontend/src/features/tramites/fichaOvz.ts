/** Ficha para copiar en OVZ (ficha OVZ, T3). ÚNICA fuente del orden y de las etiquetas de los
 * formularios de OVZ: si OVZ cambia un formulario, se cambia aquí y en sus tests.
 *
 * Fuente: docs/referencias/ovz-tramites-bovino.md (§2 campos y orden, §3 origen de cada dato, §5
 * plazos), sacado del manual de OVZNET de Extremadura. Lógica pura: sin React ni red.
 *
 * Sin datos inventados: lo que no está en `TramiteDetalle` es `valor: null` con una `pista` de dónde
 * sacarlo. Hoy Ganera solo tiene la explotación, el titular y los crotales; las fechas, el destino y
 * el resto llegarán con el B2 y se rellenarán aquí sin tocar la página. */

import { buscar, type EstadoTramite, type TipoTramite } from "./etiquetas";
import type { TramiteCrotal, TramiteDetalle } from "./types";

// ---------------------------------------------------------------------------------------------
// Modo de la ficha según el estado (D4).

/** D4: qué se puede hacer con la ficha según el estado del trámite. Solo `copiable` lleva botones
 * de copiar: nada que no se haya aprobado se facilita para teclearlo en OVZ. */
export type ModoFicha = "copiable" | "vista-previa" | "rechazado" | "extraccion" | "no-disponible";

const MODOS: Readonly<Record<EstadoTramite, ModoFicha>> = {
  APROBADO: "copiable",
  PENDIENTE_REVISION: "vista-previa",
  RECHAZADO: "rechazado",
  PENDIENTE_EXTRACCION: "extraccion",
  // Hoy no se dan (3c no existe). Lo conservador: sin copiar, porque ya está (o estará) en OVZ.
  EN_PROCESO: "no-disponible",
  EJECUTADO_OVZ: "no-disponible",
  ERROR_OVZ: "no-disponible",
};

export function modoFicha(estado: EstadoTramite): ModoFicha {
  return buscar<ModoFicha>(MODOS, estado) ?? "no-disponible";
}

// ---------------------------------------------------------------------------------------------
// Pistas de lo que falta. Constantes para que la página retoque el texto en un solo sitio.

/** Datos que el B2 sacará del mensaje con la IA (§3, origen IA). */
export const PISTA_MENSAJE = "Está en el mensaje";
/** Listas de OVZ cuyos valores aún no se conocen (§3, nota de las listas). */
export const PISTA_LISTA_OVZ = "Elígelo en la lista de OVZ";
/** Preferencias fijas de la gestoría o del ganadero (§3, origen P). */
export const PISTA_PREFERENCIA = "Preferencia de la gestoría";
/** Lo rellena el gestor (§3, origen M), o lo deduce OVZ y hay que comprobarlo (D). */
export const PISTA_GESTOR = "Lo rellenas tú en OVZ";
export const PISTA_EXPLOTACION = "Asigna la explotación en Ganera";
/** Crotal ambiguo, incompleto o sin resolver (D7). */
export const PISTA_CROTAL_COMPLETO = "Falta el crotal completo";
/** Alta: el ternero y la madre están entre los crotales mencionados, pero aún no se sabe cuál es
 * cuál (D6; el rol llega con el B2). */
export const PISTA_CROTAL_TERNERO = "Uno de los crotales de arriba, o elígelo en la lista de OVZ";
export const PISTA_CROTAL_MADRE = "Uno de los crotales de arriba";

// ---------------------------------------------------------------------------------------------
// Modelo.

export interface CampoFicha {
  /** Estable y único dentro de la ficha (key de React y para tests). */
  id: string;
  /** Texto EXACTO del campo en OVZ (manual, §2). */
  etiqueta: string;
  /** `*` u obligatorio según el texto del manual. */
  obligatorio: boolean;
  /** Lo que se copia, recortado; null = falta. */
  valor: string | null;
  /** Solo si falta: de dónde sacarlo. Siempre una de las constantes `PISTA_*`. */
  pista: string | null;
  /** Solo en los campos que salen de un crotal del trámite, resuelto o no: lo que se escribió
   * (`crotalIndicado`, recortado; en blanco = null). Informativo: NUNCA se copia. null en el resto. */
  escrito: string | null;
}

export interface BloqueFicha {
  id: string;
  titulo: string;
  campos: CampoFicha[];
}

export interface FichaOvz {
  /** Nombre del formulario en OVZ, p. ej. "Baja de Bovino". */
  formulario: string;
  /** Cómo llegar en OVZ, p. ej. "Trámites › Bóvidos › Baja". */
  rutaOvz: string;
  /** Titular con cuya cuenta se entra en OVZ. */
  cuenta: { nombre: string | null; nif: string | null };
  /** "Explotación", con valor = código REGA. */
  explotacion: CampoFicha;
  bloques: BloqueFicha[];
  /** Plazos del manual (§5) y avisos de cómo rellenar el formulario. */
  avisos: string[];
}

export type ResultadoFicha =
  | { tipo: "ficha"; ficha: FichaOvz }
  | { tipo: "sin-tipo" }
  | { tipo: "tipo-desconocido"; valor: string }
  | { tipo: "sin-preparar"; formulario: string };

// ---------------------------------------------------------------------------------------------
// Ayudas.

function texto(valor: string | null | undefined): string | null {
  const recortado = (valor ?? "").trim();
  return recortado === "" ? null : recortado;
}

/** Campo que Ganera aún no tiene. */
function falta(id: string, etiqueta: string, obligatorio: boolean, pista: string): CampoFicha {
  return { id, etiqueta, obligatorio, valor: null, pista, escrito: null };
}

/** Campo con un valor posible; si no lo hay, falta con la pista dada. */
function conValor(
  id: string,
  etiqueta: string,
  obligatorio: boolean,
  valor: string | null,
  pista: string,
): CampoFicha {
  const limpio = texto(valor);
  return { id, etiqueta, obligatorio, valor: limpio, pista: limpio === null ? pista : null, escrito: null };
}

/** D7: solo se copia un crotal que se sabe completo. `EN_INVENTARIO` trae el del inventario; un
 * `NO_ENCONTRADO` solo si lo escrito era completo. El resto (ambiguo, incompleto, sin explotación,
 * `completo` ausente) falta. No se reformatea: el backend ya lo da normalizado. */
function valorCrotal(crotal: TramiteCrotal): string | null {
  if (crotal.resolucion === "EN_INVENTARIO") return texto(crotal.crotal);
  if (crotal.resolucion === "NO_ENCONTRADO" && crotal.completo === true) return texto(crotal.crotal);
  return null;
}

/** Campo que sale de un crotal del trámite: lleva siempre lo `escrito`, se haya resuelto o no. */
function campoCrotal(id: string, etiqueta: string, crotal: TramiteCrotal, obligatorio = true): CampoFicha {
  return {
    ...conValor(id, etiqueta, obligatorio, valorCrotal(crotal), PISTA_CROTAL_COMPLETO),
    escrito: texto(crotal.crotalIndicado),
  };
}

/** Un campo por crotal ("Animal 1"…). Sin crotales, uno solo que falta: el crotal estará en el
 * mensaje (o no llegó). */
function camposAnimales(prefijo: string, crotales: TramiteCrotal[]): CampoFicha[] {
  if (crotales.length === 0) return [falta(`${prefijo}-animal-1`, "Animal 1", true, PISTA_MENSAJE)];
  return crotales.map((c, i) => campoCrotal(`${prefijo}-animal-${i + 1}`, `Animal ${i + 1}`, c));
}

// ---------------------------------------------------------------------------------------------
// Formularios, en el orden de OVZ (§2).

type Cuerpo = Pick<FichaOvz, "formulario" | "rutaOvz" | "bloques" | "avisos">;
type ConstructorCuerpo = (crotales: TramiteCrotal[]) => Cuerpo;

/** §2.2. Una baja por animal (D6): un bloque por crotal. */
function bajaMuerte(crotales: TramiteCrotal[]): Cuerpo {
  const total = Math.max(crotales.length, 1);
  const bloques: BloqueFicha[] = [];
  for (let i = 0; i < total; i++) {
    const n = i + 1;
    const p = `baja-${n}`;
    const crotal = crotales[i];
    bloques.push({
      id: p,
      titulo: total === 1 ? "Baja" : `Baja ${n} de ${total}`,
      campos: [
        crotal
          ? campoCrotal(`${p}-crotal`, "Crotal", crotal)
          : falta(`${p}-crotal`, "Crotal", true, PISTA_MENSAJE),
        falta(`${p}-fecha-muerte`, "Fecha de muerte", true, PISTA_MENSAJE),
        falta(`${p}-mer`, "Nº Documento MER", true, PISTA_GESTOR),
        falta(`${p}-oficina-mer`, "Oficina para entregar la copia del ejemplar MER", true, PISTA_PREFERENCIA),
      ],
    });
  }
  return {
    formulario: "Baja de Bovino",
    rutaOvz: "Trámites › Bóvidos › Baja",
    bloques,
    avisos: [
      "OVZ rellena solo los datos del animal (raza, sexo, fecha de nacimiento…)",
      "Plazo: 7 días desde la muerte",
      "Entrega la copia del ejemplar MER en la OVZ en 15 días",
    ],
  };
}

/** §2.1. Un solo formulario: aún no se sabe qué crotal es la madre y cuál el ternero (D6). */
function altaNacimiento(crotales: TramiteCrotal[]): Cuerpo {
  const hayCrotales = crotales.length > 0;
  const pistaTernero = hayCrotales ? PISTA_CROTAL_TERNERO : PISTA_LISTA_OVZ;
  const pistaMadre = hayCrotales ? PISTA_CROTAL_MADRE : PISTA_MENSAJE;
  // "Crotales del trámite" NO es un bloque del formulario de OVZ (sus etiquetas no son de OVZ y no
  // son obligatorios): es contexto para elegir el ternero y la madre hasta que el B2 los distinga.
  // Sin crotales no se pinta.
  const crotalesDelTramite: BloqueFicha[] = hayCrotales
    ? [
        {
          id: "alta-crotales",
          titulo: "Crotales del trámite",
          campos: crotales.map((c, i) => campoCrotal(`alta-crotal-${i + 1}`, `Crotal ${i + 1}`, c, false)),
        },
      ]
    : [];
  return {
    formulario: "Alta de Bóvidos",
    rutaOvz: "Trámites › Bóvidos › Alta",
    bloques: [
      ...crotalesDelTramite,
      {
        id: "alta",
        titulo: "Alta",
        campos: [
          falta("alta-crotal-ternero", "Crotal del ternero", true, pistaTernero),
          falta("alta-crotal-madre", "Crotal de la Madre", true, pistaMadre),
          falta("alta-fecha-nacimiento", "Fecha Nacimiento", true, PISTA_MENSAJE),
          falta("alta-identificador-lidia", "Identificador de Lidia", false, PISTA_GESTOR),
          falta("alta-sexo", "Sexo", true, PISTA_MENSAJE),
          falta("alta-fecha-identificacion", "Fecha Identificación", true, PISTA_MENSAJE),
          falta("alta-tipo-identificador", "Tipo Identificador", true, PISTA_PREFERENCIA),
          falta("alta-raza", "Raza", true, PISTA_LISTA_OVZ),
          falta("alta-emision-di", "Emisión del DI", true, PISTA_PREFERENCIA),
          falta("alta-oficina", "Oficina de recogida o envío por correo", true, PISTA_PREFERENCIA),
        ],
      },
    ],
    avisos: [],
  };
}

/** §2.3. Una guía lleva varios animales: un bloque por bloque de OVZ. */
function solicitudMovimiento(crotales: TramiteCrotal[]): Cuerpo {
  const p = "solicitud";
  return {
    formulario: "Solicitud de Movimiento",
    rutaOvz: "Movimientos › Solicitud de Movimientos › Bóvidos › Solicitud de Movimiento",
    bloques: [
      {
        id: `${p}-origen`,
        titulo: "Origen",
        campos: [
          falta(`${p}-telefono`, "Teléfono", true, PISTA_PREFERENCIA),
          falta(`${p}-sms`, "Autorizo envío de SMS", false, PISTA_GESTOR),
        ],
      },
      {
        id: `${p}-destino`,
        titulo: "Destino",
        campos: [
          falta(`${p}-pais`, "País", false, PISTA_GESTOR),
          falta(`${p}-comunidad`, "Comunidad Autónoma", false, PISTA_GESTOR),
          falta(`${p}-rega-destino`, "Código REGA", true, PISTA_MENSAJE),
          falta(`${p}-consignatario`, "Consignatario", true, PISTA_MENSAJE),
        ],
      },
      {
        id: `${p}-guia`,
        titulo: "Datos de la guía",
        campos: [
          falta(`${p}-fecha-salida`, "Fecha de Salida", true, PISTA_MENSAJE),
          falta(`${p}-fecha-llegada`, "Fecha de Llegada", true, PISTA_GESTOR),
          falta(`${p}-medio-transporte`, "Medio de Transporte", false, PISTA_PREFERENCIA),
          falta(`${p}-matricula`, "Identificador del medio de transporte (Matrícula)", false, PISTA_PREFERENCIA),
          falta(`${p}-transportista`, "Transportista (Código SIRENTRA)", false, PISTA_PREFERENCIA),
          falta(`${p}-tipo-responsable`, "Tipo Responsable del Movimiento", false, PISTA_PREFERENCIA),
          falta(`${p}-aptitud`, "Aptitud del Movimiento", false, PISTA_MENSAJE),
          falta(`${p}-guia-temporal`, "Guía Temporal", false, PISTA_GESTOR),
        ],
      },
      { id: `${p}-animales`, titulo: "Animales", campos: camposAnimales(p, crotales) },
    ],
    avisos: [
      "Pídela con al menos 48 h de antelación: con menos, puede que OVZ no la tramite",
      "Una guía firmada y no emitida caduca a los 5 días",
      "OVZ no deja pedir guías si hay entradas sin confirmar desde hace más de 13 días",
    ],
  };
}

/** §2.4. OVZ enseña la guía; el gestor confirma o rechaza cada animal y elige la oficina. */
function confirmacionMovimiento(crotales: TramiteCrotal[]): Cuerpo {
  const p = "confirmacion";
  return {
    formulario: "Confirmación de Movimientos",
    rutaOvz: "Movimientos › Confirmación",
    bloques: [
      {
        id: `${p}-guia`,
        titulo: "Guía",
        campos: [falta(`${p}-guia-remo`, "Guía o Código REMO", true, PISTA_GESTOR)],
      },
      { id: `${p}-animales`, titulo: "Animales que llegaron", campos: camposAnimales(p, crotales) },
      {
        id: `${p}-di`,
        titulo: "Documentos de identificación",
        campos: [falta(`${p}-oficina-di`, "Oficina para los DI", true, PISTA_PREFERENCIA)],
      },
    ],
    avisos: [
      "En OVZ, pestaña Internos si viene de Extremadura y Externos si viene de otra comunidad",
      "Marca Confirmar en estos animales y Rechazar en los demás",
      "Plazo: 7 días desde la entrada",
    ],
  };
}

/** §2.6. Se marcan los animales y se pone la fecha de identificación. */
function demoraCrotalizacion(crotales: TramiteCrotal[]): Cuerpo {
  const p = "demora";
  return {
    formulario: "Animales con Demora",
    rutaOvz: "Trámites › Demora",
    bloques: [
      {
        id: p,
        titulo: "Animales con demora",
        campos: [
          ...camposAnimales(p, crotales),
          falta(`${p}-fecha-identificacion`, "Fecha de Identificación", true, PISTA_MENSAJE),
        ],
      },
    ],
    avisos: [],
  };
}

/** Tipos con formulario en OVZ cuya ficha Ganera aún no prepara (las líneas del censo). */
const SIN_PREPARAR = {
  DECLARACION_CENSO: "Declaración de Censo",
} as const satisfies Partial<Record<TipoTramite, string>>;

/** Una ficha por cada tipo de `TIPOS_TRAMITE` que no esté en `SIN_PREPARAR`: añadir un tipo nuevo sin
 * ficha (o sin pasarlo a `SIN_PREPARAR`) no compila. */
const FORMULARIOS = {
  BAJA_MUERTE: bajaMuerte,
  ALTA_NACIMIENTO: altaNacimiento,
  SOLICITUD_MOVIMIENTO: solicitudMovimiento,
  CONFIRMACION_MOVIMIENTO: confirmacionMovimiento,
  DEMORA_CROTALIZACION: demoraCrotalizacion,
} as const satisfies Record<Exclude<TipoTramite, keyof typeof SIN_PREPARAR>, ConstructorCuerpo>;

// ---------------------------------------------------------------------------------------------

/** Construye la ficha de un trámite. Nunca lanza: un tipo que el frontend no conoce da
 * `tipo-desconocido`. No mira el estado: eso es `modoFicha`. */
export function construirFichaOvz(detalle: TramiteDetalle): ResultadoFicha {
  const tipo = texto(detalle.tipoTramite);
  if (tipo === null) return { tipo: "sin-tipo" };
  const sinPreparar = buscar<string>(SIN_PREPARAR, tipo);
  if (sinPreparar !== undefined) return { tipo: "sin-preparar", formulario: sinPreparar };
  const construir = buscar<ConstructorCuerpo>(FORMULARIOS, tipo);
  if (construir === undefined) return { tipo: "tipo-desconocido", valor: tipo };

  const cuerpo = construir(detalle.crotales ?? []);
  return {
    tipo: "ficha",
    ficha: {
      formulario: cuerpo.formulario,
      rutaOvz: cuerpo.rutaOvz,
      cuenta: { nombre: texto(detalle.ganaderoNombre), nif: texto(detalle.ganaderoNif) },
      explotacion: conValor("explotacion", "Explotación", true, detalle.explotacionCodigoRega, PISTA_EXPLOTACION),
      bloques: cuerpo.bloques,
      avisos: cuerpo.avisos,
    },
  };
}
