import axios from "axios";

/**
 * Modelo único de error de la API. httpClient convierte TODO fallo de petición en un ErrorApi, así
 * que ninguna pantalla necesita saber nada de axios: mira `tipo` y pide el texto a mensajeDeError.
 */
export type TipoErrorApi =
  | "no-autorizado" // 401
  | "prohibido" // 403
  | "validacion" // 400
  | "no-encontrado" // 404
  | "conflicto" // 409
  | "servidor" // 5xx
  | "red" // sin respuesta del servidor
  | "desconocido"; // cualquier otro status, o un fallo que no viene de una petición

interface DatosErrorApi {
  tipo: TipoErrorApi;
  status?: number;
  motivo?: string;
  /** La petición la canceló el propio frontend (AbortController): no es un fallo que enseñar. */
  cancelado?: boolean;
}

/**
 * Clase (no un objeto etiquetado) para que sea un Error de verdad: pila, `instanceof` fiable en
 * los `catch`, y cualquier código que espere un Error (consola, boundaries) lo trata bien.
 */
export class ErrorApi extends Error {
  readonly tipo: TipoErrorApi;
  readonly status?: number;
  /** Texto del backend para el usuario. Nunca vacío: si viene en blanco, es undefined. */
  readonly motivo?: string;
  /** true si la petición se canceló a propósito. Ver esCancelacion. */
  readonly cancelado: boolean;

  constructor({ tipo, status, motivo, cancelado = false }: DatosErrorApi) {
    const motivoLimpio = limpiarTexto(motivo);
    super(motivoLimpio ?? `Error de la API (${tipo}${status ? ` ${status}` : ""})`);
    this.name = "ErrorApi";
    this.tipo = tipo;
    this.status = status;
    this.motivo = motivoLimpio;
    this.cancelado = cancelado;
  }
}

export function esErrorApi(error: unknown): error is ErrorApi {
  return error instanceof ErrorApi;
}

/**
 * Una petición cancelada con AbortController (p. ej. al cerrar un panel o cambiar de filtro) no es
 * un error para el usuario: quien cancele debe comprobar esto en su `catch` y no mostrar nada.
 */
export function esCancelacion(error: unknown): boolean {
  return esErrorApi(error) && error.cancelado;
}

export function tipoDeStatus(status: number): TipoErrorApi {
  if (status === 400) return "validacion";
  if (status === 401) return "no-autorizado";
  if (status === 403) return "prohibido";
  if (status === 404) return "no-encontrado";
  if (status === 409) return "conflicto";
  if (status >= 500) return "servidor";
  return "desconocido";
}

/**
 * Convierte cualquier cosa lanzada en un ErrorApi. Lo usa el interceptor de httpClient, y también
 * sirve en un `catch` para guardar el error en estado con un tipo concreto.
 */
export function aErrorApi(error: unknown): ErrorApi {
  if (esErrorApi(error)) return error;
  if (axios.isAxiosError(error)) {
    const respuesta = error.response;
    if (!respuesta) {
      // Sin respuesta: el servidor no contestó (caído, sin red, CORS, timeout).
      if (error.code === "ERR_CANCELED" || axios.isCancel(error)) {
        return new ErrorApi({ tipo: "desconocido", cancelado: true });
      }
      return new ErrorApi({ tipo: "red" });
    }
    return new ErrorApi({
      tipo: tipoDeStatus(respuesta.status),
      status: respuesta.status,
      // Solo un 4xx trae un motivo pensado para el usuario. Un 5xx puede traer una traza o el
      // texto de un gateway ("no healthy upstream"): nunca se enseña (M1 de la revisión).
      motivo:
        respuesta.status >= 400 && respuesta.status < 500
          ? extraerMotivo(respuesta.data, cabecera(respuesta.headers, "content-type"))
          : undefined,
    });
  }
  return new ErrorApi({ tipo: "desconocido" });
}

/**
 * `{motivo}` es el formato del backend en todos sus errores con texto (400/403/409), también el 400
 * del importador y el de POST /gestorias/registro. Un cuerpo de texto plano se sigue tomando como
 * motivo solo como defensa: hoy ningún endpoint lo usa. Una página HTML (un proxy) no es un texto
 * para el usuario. El `{motivo}` del registro sí se extrae, pero esa pantalla no lo enseña
 * (`ignorarMotivo`, ver el contexto "registro"). Nunca lanza.
 */
function extraerMotivo(data: unknown, contentType: string | undefined): string | undefined {
  try {
    if (typeof data === "string") {
      if (contentType?.toLowerCase().includes("html") || data.trimStart().startsWith("<")) {
        return undefined;
      }
      return limpiarTexto(data);
    }
    if (data !== null && typeof data === "object") {
      const cuerpo = data as Record<string, unknown>;
      return limpiarTexto(cuerpo.motivo);
    }
  } catch {
    // Un cuerpo raro nunca debe romper el manejo del error.
  }
  return undefined;
}

function limpiarTexto(valor: unknown): string | undefined {
  if (typeof valor !== "string") return undefined;
  const texto = valor.trim();
  return texto === "" ? undefined : texto;
}

function cabecera(headers: unknown, nombre: string): string | undefined {
  if (!headers || typeof headers !== "object") return undefined;
  const valor = (headers as Record<string, unknown>)[nombre];
  return typeof valor === "string" ? valor : undefined;
}

// ---------------------------------------------------------------------------------------------
// Textos para el usuario
// ---------------------------------------------------------------------------------------------

export const TEXTO_ERROR_RED =
  "No se ha podido conectar con Ganera. Inténtalo de nuevo en unos segundos.";
export const TEXTO_ERROR_SERVIDOR =
  "Ha fallado algo en el servidor. Inténtalo de nuevo; si se repite, avísanos.";

const TEXTO_FACTURACION_NO_CONFIGURADA =
  "La facturación todavía no está configurada. Vuelve a intentarlo más tarde.";
const TEXTO_TRAMITE_NO_ENCONTRADO = "Este trámite ya no existe o no es de tu gestoría.";
/** Exportado: la ficha del Ganadero lo enseña en su estado "no encontrado" (404 o id no válido). */
export const TEXTO_GANADERO_NO_ENCONTRADO = "Este ganadero no existe o no es de tu gestoría.";
const TEXTO_EXPLOTACION_NO_ENCONTRADA = "Esta explotación no existe o no es de tu gestoría.";

const TEXTO_SESION_CADUCADA = "Tu sesión ha caducado. Vuelve a iniciar sesión.";
const TEXTO_PROHIBIDO = "No tienes permiso para hacer esto.";
const TEXTO_NO_ENCONTRADO = "No se ha encontrado lo que buscabas. Puede que ya no exista.";
const TEXTO_GENERICO = "Ha ocurrido un error inesperado. Inténtalo de nuevo.";

/**
 * Dónde ocurrió el error: decide los textos cuando el backend no manda `motivo`. Para un sitio
 * nuevo (Ganaderos, animales, guardar trámite…) se añade aquí el nombre y su entrada en TEXTOS; al
 * ser un Record exhaustivo, TypeScript obliga a darle al menos su texto genérico.
 */
export type ContextoError =
  | "login"
  | "comprobar-sesion"
  | "registro"
  | "checkout"
  | "suscripcion"
  | "importar-excel"
  | "listar-explotaciones"
  | "listar-tramites"
  | "detalle-tramite"
  | "aprobar-tramite"
  | "rechazar-tramite"
  | "guardar-tramite"
  | "listar-ganaderos"
  | "detalle-ganadero"
  | "animales-explotacion";

interface TextosContexto {
  /** Texto de reserva del contexto: validación/conflicto/desconocido sin motivo. Obligatorio. */
  generico: string;
  /**
   * true en pantallas cuyo error debe ser uniforme (login, registro): el `motivo` del backend no
   * se enseña nunca, para que ningún cambio futuro del backend pueda revelar por qué se rechazó
   * (cuenta inactiva, email ya registrado…). Solo se distinguen red y servidor.
   */
  ignorarMotivo?: boolean;
  noAutorizado?: string;
  prohibido?: string;
  noEncontrado?: string;
  /** Textos por status concreto, antes que los del tipo (p. ej. 503 = facturación sin configurar). */
  porStatus?: Partial<Record<number, string>>;
}

const TEXTOS: Record<ContextoError, TextosContexto> = {
  login: {
    ignorarMotivo: true,
    generico: "No se ha podido iniciar sesión. Inténtalo de nuevo.",
    // Siempre el mismo, sea cual sea la causa (CLAUDE.md: el login no revela por qué falla).
    noAutorizado: "Email o contraseña incorrectos.",
  },
  // GET /auth/me al arrancar con un token guardado. Su 401 no llega aquí (cierra la sesión); aquí
  // solo llegan red, servidor y lo inesperado, que no deben desloguear.
  "comprobar-sesion": {
    generico: "No se ha podido comprobar tu sesión. Inténtalo de nuevo.",
  },
  registro: {
    ignorarMotivo: true,
    // Copia literal del texto uniforme del backend (RegistroGestoriaController.
    // MOTIVO_REGISTRO_INVALIDO), el mismo para cualquier causa: email duplicado, contraseña débil,
    // email mal formado o campo vacío. El backend lo manda como `{motivo}`, pero se ignora a
    // propósito: el texto lo fija el frontend, sin oráculo de enumeración.
    generico:
      "No se ha podido completar el registro con esos datos. Revisa el email y la contraseña e inténtalo de nuevo.",
    porStatus: { 503: TEXTO_FACTURACION_NO_CONFIGURADA },
  },
  checkout: {
    generico: "No se ha podido iniciar el pago. Inténtalo de nuevo.",
    porStatus: { 503: TEXTO_FACTURACION_NO_CONFIGURADA },
  },
  suscripcion: {
    generico: "No se ha podido cargar el estado de la suscripción. Inténtalo de nuevo.",
  },
  "importar-excel": {
    generico: "No se ha podido importar el fichero. Inténtalo de nuevo.",
  },
  "listar-explotaciones": {
    generico: "No se han podido cargar las explotaciones. Inténtalo de nuevo.",
  },
  "listar-tramites": {
    generico: "No se han podido cargar los trámites. Inténtalo de nuevo.",
  },
  "detalle-tramite": {
    generico: "No se ha podido cargar el detalle del trámite. Inténtalo de nuevo.",
    noEncontrado: TEXTO_TRAMITE_NO_ENCONTRADO,
  },
  "aprobar-tramite": {
    generico: "No se ha podido aprobar el trámite. Inténtalo de nuevo.",
    noEncontrado: TEXTO_TRAMITE_NO_ENCONTRADO,
    // El 403 de aprobar solo puede ser por la suscripción y llega con `{motivo}`
    // (TramiteController.MOTIVO_SUSCRIPCION_NO_PERMITE_APROBAR, este mismo texto), que es lo que se
    // enseña. Este texto fijo es solo la reserva si no llega motivo.
    prohibido:
      "Tu suscripción no permite aprobar trámites ahora mismo (trial expirado o suspendida). Actualiza tu suscripción en Facturación.",
  },
  "rechazar-tramite": {
    generico: "No se ha podido rechazar el trámite. Inténtalo de nuevo.",
    noEncontrado: TEXTO_TRAMITE_NO_ENCONTRADO,
  },
  // PATCH /tramites/{id}. Su 404 no distingue el trámite de una explotación ajena (el backend
  // responde igual); se da el del trámite, que es el caso normal.
  "guardar-tramite": {
    generico: "No se han podido guardar los cambios del trámite. Inténtalo de nuevo.",
    noEncontrado: TEXTO_TRAMITE_NO_ENCONTRADO,
  },
  "listar-ganaderos": {
    generico: "No se han podido cargar los ganaderos. Inténtalo de nuevo.",
  },
  "detalle-ganadero": {
    generico: "No se ha podido cargar el ganadero. Inténtalo de nuevo.",
    // El 404 no distingue "no existe" de "es de otra gestoría" (a propósito, decisión 8 de A1).
    noEncontrado: TEXTO_GANADERO_NO_ENCONTRADO,
  },
  // Panel "Ver animales" (Explotaciones y ficha del Ganadero). El 404 tampoco distingue otra
  // gestoría de inexistente.
  "animales-explotacion": {
    generico: "No se han podido cargar los animales. Inténtalo de nuevo.",
    noEncontrado: TEXTO_EXPLOTACION_NO_ENCONTRADA,
  },
};

/**
 * Texto que se enseña al usuario para un error. Orden (decisión 2 del plan A2):
 * 1. el `motivo` del backend, tal cual (salvo en contextos con `ignorarMotivo`: login y registro);
 * 2. un texto del contexto para ese status concreto;
 * 3. un texto del contexto para el tipo (401/403/404);
 * 4. red y servidor → los dos textos genéricos;
 * 5. 401/403/404 sin texto de contexto → uno genérico de ese tipo;
 * 6. el genérico del contexto o, sin contexto, uno global. Nunca devuelve una cadena vacía.
 */
export function mensajeDeError(error: unknown, contexto?: ContextoError): string {
  const errorApi = aErrorApi(error);
  const textos = contexto ? TEXTOS[contexto] : undefined;
  if (errorApi.motivo && !textos?.ignorarMotivo) return errorApi.motivo;

  const porStatus = errorApi.status !== undefined ? textos?.porStatus?.[errorApi.status] : undefined;
  if (porStatus) return porStatus;

  switch (errorApi.tipo) {
    case "red":
      return TEXTO_ERROR_RED;
    case "servidor":
      return TEXTO_ERROR_SERVIDOR;
    case "no-autorizado":
      return textos?.noAutorizado ?? TEXTO_SESION_CADUCADA;
    case "prohibido":
      return textos?.prohibido ?? TEXTO_PROHIBIDO;
    case "no-encontrado":
      return textos?.noEncontrado ?? TEXTO_NO_ENCONTRADO;
    default:
      return textos?.generico ?? TEXTO_GENERICO;
  }
}
