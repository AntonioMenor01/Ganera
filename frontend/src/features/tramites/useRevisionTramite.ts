import { useEffect, useLayoutEffect, useReducer, useRef, useState } from "react";
import {
  aErrorApi,
  esCancelacion,
  mensajeDeError,
  type ContextoError,
  type ErrorApi,
} from "@/shared/api/errores";
import { actualizarTramite, aprobarTramite, obtenerDetalleTramite, rechazarTramite } from "./api";
import { presentacionEstado, type TipoTramite } from "./etiquetas";
import {
  AVISO_CAMBIOS_SIN_GUARDAR,
  construirPatch,
  esEditable,
  estaSucio,
  formularioDesdeDetalle,
  type FormularioRevision,
} from "./revisionTramite";
import type { Tramite, TramiteDetalle } from "./types";

/**
 * Estado y acciones del modal de revisión de un trámite (Task 9a, decisiones 7–13 y 24).
 *
 * Forma: un reducer (transiciones puras: cargar, editar, enviar, aplicar respuesta, fallar) más
 * este hook, que pone lo asíncrono alrededor (peticiones, cancelación, envío único). Se eligió
 * reducer porque cada respuesta toca varias piezas a la vez (detalle, formulario, aviso, envío) y
 * así cada transición es atómica: nunca se ve un detalle nuevo con el formulario viejo.
 *
 * Las reglas de negocio las decide el backend: aquí no se valida ni clasifica ningún crotal ni se
 * predice si un trámite es aprobable. Aprobar y rechazar solo cambian el estado en Ganera (BD);
 * nada se ejecuta en OVZ.net (Prompt 3c).
 *
 * Las explotaciones para el selector NO se cargan aquí: TramitesPage ya tiene la lista completa
 * (useTodasLasExplotaciones, decisiones 20 y 22) y la UI (9b) se la pasa al modal, sin pedirla dos
 * veces.
 */

export type AccionRevision = "guardar" | "aprobar" | "rechazar";

export type CargaRevision =
  /** Sin trámite (`tramiteId` null: modal cerrado). */
  | { estado: "inactivo" }
  | { estado: "cargando" }
  /** Fallo al cargar (o al recargar tras una acción). `mensaje` listo para enseñar. */
  | { estado: "error"; error: ErrorApi; mensaje: string }
  /** 404: no existe o es de otra gestoría (no se distingue, a propósito). */
  | { estado: "no-encontrado"; mensaje: string }
  | { estado: "listo" };

export type TipoAvisoRevision =
  | "conflicto" // 409: se recargó y se descartó el formulario
  | "validacion" // 400: se conservan las ediciones
  | "prohibido" // 403 de aprobar: suscripción
  | "no-encontrado" // 404
  | "error" // red, 5xx, inesperado
  | "estado-cambiado" // sin respuesta, y la recarga lo muestra en otro estado cerrado (m4 de 9b)
  | "exito"; // aprobado o rechazado

/** Resultado de la última acción. Persiste hasta la siguiente acción o `descartarAviso`. */
export interface AvisoRevision {
  tipo: TipoAvisoRevision;
  accion: AccionRevision;
  mensaje: string;
}

export type ResultadoCambio = { aceptado: true } | { aceptado: false; motivo: string };

export interface ExplotacionAsignada {
  id: number;
  codigoRega: string | null;
  nombre: string | null;
}

export interface OpcionesRevisionTramite {
  /** Algo cambió en el servidor (guardado, aprobado, rechazado, 409 o 404): refrescar la cola. */
  onCambiado: () => void;
}

export interface RevisionTramite {
  carga: CargaRevision;
  /** Lo guardado (última respuesta del servidor). null salvo con `carga.estado === "listo"`. */
  detalle: TramiteDetalle | null;
  reintentarCarga: () => void;
  /**
   * Tras aprobar/rechazar con éxito, la recarga del detalle falló: se sigue enseñando el detalle
   * fusionado con la respuesta del 200 (ya en solo lectura) y este texto, no bloqueante, con
   * `reintentarRecarga`. Convive con el aviso `exito` (que sigue en `aviso`). null en otro caso.
   */
  recargaFallida: string | null;
  /** Vuelve a pedir el detalle sin quitar el que se ve. Solo hace algo con `recargaFallida`. */
  reintentarRecarga: () => void;
  /** Explotación GUARDADA (no la elegida en el formulario), con su etiqueta. */
  explotacionAsignada: ExplotacionAsignada | null;

  formulario: FormularioRevision;
  /** Solo en PENDIENTE_REVISION; en otro estado el formulario es de solo lectura y no hay acciones. */
  editable: boolean;
  /** El formulario difiere de lo guardado (crotales recortados y sin vacíos, en orden). */
  sucio: boolean;
  /** false si la explotación guardada tiene valor: PATCH no puede vaciarla (H5). */
  puedeQuitarExplotacion: boolean;
  /** false si el tipo guardado tiene valor: PATCH no puede vaciarlo (H5). */
  puedeQuitarTipo: boolean;
  cambiarExplotacion: (explotacionId: number | null) => ResultadoCambio;
  cambiarTipoTramite: (tipo: TipoTramite | null) => ResultadoCambio;
  anadirCrotal: (valor?: string) => ResultadoCambio;
  cambiarCrotal: (indice: number, valor: string) => ResultadoCambio;
  quitarCrotal: (indice: number) => ResultadoCambio;
  descartarCambios: () => void;

  /** Acción en curso (incluye la recarga que la sigue). Mientras no es null, nada más se envía. */
  enviando: AccionRevision | null;
  puedeGuardar: boolean;
  puedeAprobar: boolean;
  puedeRechazar: boolean;
  /** "Guarda antes de aprobar" con cambios sin guardar (decisión 24); si no, null. */
  avisoCambiosSinGuardar: string | null;
  /** Nunca lanzan: todo resultado acaba en `aviso` o en `carga`. Si no se puede, no hacen nada. */
  guardar: () => Promise<void>;
  aprobar: () => Promise<void>;
  rechazar: () => Promise<void>;

  aviso: AvisoRevision | null;
  descartarAviso: () => void;
}

// ---------------------------------------------------------------------------------------------
// Reducer

interface Estado {
  /** Trámite al que pertenece este estado; si cambia el prop, el estado se reinicia. */
  tramiteId: number | null;
  carga: CargaRevision;
  detalle: TramiteDetalle | null;
  formulario: FormularioRevision;
  enviando: AccionRevision | null;
  aviso: AvisoRevision | null;
  recargaFallida: string | null;
}

type Cambio =
  | { campo: "explotacion"; valor: number | null }
  | { campo: "tipo"; valor: string | null }
  | { campo: "anadir-crotal"; valor: string }
  | { campo: "cambiar-crotal"; indice: number; valor: string }
  | { campo: "quitar-crotal"; indice: number };

type Evento =
  | { tipo: "reiniciar"; tramiteId: number | null }
  | { tipo: "reintentar" }
  | { tipo: "cargado"; detalle: TramiteDetalle }
  | { tipo: "carga-fallida"; carga: CargaRevision }
  | { tipo: "detalle-refrescado"; detalle: TramiteDetalle }
  | { tipo: "recarga-fallida"; mensaje: string }
  | { tipo: "reintentando-recarga" }
  | { tipo: "editar"; cambio: Cambio }
  | { tipo: "descartar-cambios" }
  | { tipo: "enviar"; accion: AccionRevision }
  | { tipo: "guardado"; detalle: TramiteDetalle }
  | { tipo: "respuesta-accion"; tramite: Tramite; aviso: AvisoRevision }
  | { tipo: "aviso"; aviso: AvisoRevision }
  | { tipo: "fin-envio" }
  | { tipo: "descartar-aviso" };

const FORMULARIO_VACIO: FormularioRevision = { explotacionId: null, tipoTramite: null, crotales: [] };

function estadoInicial(tramiteId: number | null): Estado {
  return {
    tramiteId,
    carga: tramiteId === null ? { estado: "inactivo" } : { estado: "cargando" },
    detalle: null,
    formulario: FORMULARIO_VACIO,
    enviando: null,
    aviso: null,
    recargaFallida: null,
  };
}

const MOTIVO_NO_EDITABLE = "Este trámite no se puede editar ahora.";
const MOTIVO_ENVIANDO = "Espera a que termine la acción en curso.";
const MOTIVO_INDICE = "Ese crotal ya no está en la lista.";
const MOTIVO_QUITAR_EXPLOTACION =
  "Una vez asignada, la explotación no se puede quitar. Elige otra si es necesario.";
const MOTIVO_QUITAR_TIPO =
  "Una vez asignado, el tipo de trámite no se puede quitar. Elige otro si es necesario.";

/** Si el cambio se puede aplicar sobre `estado`; `null` = sí, si no el motivo. Única fuente: la
 * usan el reducer (que ignora lo rechazado) y las funciones del hook (que devuelven el motivo). */
function rechazoDeCambio(estado: Estado, cambio: Cambio): string | null {
  const { detalle, formulario } = estado;
  if (!detalle || estado.carga.estado !== "listo" || !esEditable(detalle.estado)) {
    return MOTIVO_NO_EDITABLE;
  }
  if (estado.enviando !== null) return MOTIVO_ENVIANDO;
  switch (cambio.campo) {
    case "explotacion":
      return cambio.valor === null && detalle.explotacionId !== null ? MOTIVO_QUITAR_EXPLOTACION : null;
    case "tipo":
      return cambio.valor === null && detalle.tipoTramite !== null ? MOTIVO_QUITAR_TIPO : null;
    case "anadir-crotal":
      return null;
    case "cambiar-crotal":
    case "quitar-crotal":
      return Number.isInteger(cambio.indice) &&
        cambio.indice >= 0 &&
        cambio.indice < formulario.crotales.length
        ? null
        : MOTIVO_INDICE;
  }
}

function aplicarCambio(formulario: FormularioRevision, cambio: Cambio): FormularioRevision {
  switch (cambio.campo) {
    case "explotacion":
      return { ...formulario, explotacionId: cambio.valor };
    case "tipo":
      return { ...formulario, tipoTramite: cambio.valor };
    case "anadir-crotal":
      return { ...formulario, crotales: [...formulario.crotales, cambio.valor] };
    case "cambiar-crotal":
      return {
        ...formulario,
        crotales: formulario.crotales.map((c, i) => (i === cambio.indice ? cambio.valor : c)),
      };
    case "quitar-crotal":
      return { ...formulario, crotales: formulario.crotales.filter((_, i) => i !== cambio.indice) };
  }
}

function reducer(estado: Estado, evento: Evento): Estado {
  switch (evento.tipo) {
    case "reiniciar":
      return estadoInicial(evento.tramiteId);
    case "reintentar":
      // El aviso se conserva: tras un 409 cuya recarga falló, explica por qué se descartó todo.
      return {
        ...estado,
        carga: { estado: "cargando" },
        detalle: null,
        formulario: FORMULARIO_VACIO,
        recargaFallida: null,
      };
    case "cargado":
      // También tras una acción: el formulario vuelve a lo guardado (409 → se descarta lo editado).
      return {
        ...estado,
        carga: { estado: "listo" },
        detalle: evento.detalle,
        formulario: formularioDesdeDetalle(evento.detalle),
        recargaFallida: null,
      };
    case "carga-fallida":
      // Nunca se queda a la vista un detalle desactualizado con el que seguir actuando.
      return {
        ...estado,
        carga: evento.carga,
        detalle: null,
        formulario: FORMULARIO_VACIO,
        recargaFallida: null,
      };
    case "detalle-refrescado":
      // Detalle fresco SIN tocar el formulario: el usuario conserva lo que había editado.
      return { ...estado, carga: { estado: "listo" }, detalle: evento.detalle };
    case "recarga-fallida":
      return { ...estado, recargaFallida: evento.mensaje };
    case "reintentando-recarga":
      return { ...estado, recargaFallida: null };
    case "editar":
      if (rechazoDeCambio(estado, evento.cambio) !== null) return estado;
      return { ...estado, formulario: aplicarCambio(estado.formulario, evento.cambio) };
    case "descartar-cambios":
      return estado.detalle ? { ...estado, formulario: formularioDesdeDetalle(estado.detalle) } : estado;
    case "enviar":
      // Una acción nueva sustituye al aviso anterior.
      return { ...estado, enviando: evento.accion, aviso: null, recargaFallida: null };
    case "guardado":
      return {
        ...estado,
        carga: { estado: "listo" },
        detalle: evento.detalle,
        formulario: formularioDesdeDetalle(evento.detalle),
        enviando: null,
        // Sin aviso: "enviar" ya quitó el anterior y un guardado correcto no deja ninguno.
      };
    case "respuesta-accion": {
      // La respuesta de aprobar/rechazar es la del listado (sin mensaje ni etiqueta de la
      // explotación): se aplica ya para pasar a solo lectura, y la recarga completa el resto.
      if (!estado.detalle) return { ...estado, aviso: evento.aviso };
      const { tramite } = evento;
      const detalle: TramiteDetalle = {
        ...estado.detalle,
        estado: tramite.estado,
        tipoTramite: tramite.tipoTramite,
        explotacionId: tramite.explotacionId,
        motivoError: tramite.motivoError,
        crotales: tramite.crotales,
        version: tramite.version,
      };
      return { ...estado, detalle, formulario: formularioDesdeDetalle(detalle), aviso: evento.aviso };
    }
    case "aviso":
      return { ...estado, aviso: evento.aviso };
    case "fin-envio":
      return { ...estado, enviando: null };
    case "descartar-aviso":
      return { ...estado, aviso: null };
  }
}

// ---------------------------------------------------------------------------------------------
// Hook

const CONTEXTO_DE_ACCION: Record<AccionRevision, ContextoError> = {
  guardar: "guardar-tramite",
  aprobar: "aprobar-tramite",
  rechazar: "rechazar-tramite",
};

const TEXTO_EXITO: Record<"aprobar" | "rechazar", string> = {
  aprobar: "Trámite aprobado.",
  rechazar: "Trámite rechazado.",
};

/** PATCH 404 y el trámite sí existe: el backend responde igual para una explotación ajena o que ya
 * no existe, así que la elegida era el problema (hueco "404 de PATCH ambiguo"). */
const TEXTO_EXPLOTACION_NO_DISPONIBLE = "La explotación elegida ya no está disponible. Elige otra.";

/** PATCH 404 y la recarga trae otra versión o un trámite ya no pendiente (N1 de la revisión 9a):
 * conservar el formulario lo compararía contra datos que el usuario no ha visto, así que se trata
 * como un 409. */
const TEXTO_TRAMITE_CAMBIADO =
  "El trámite ha cambiado mientras lo editabas. Se han descartado tus cambios; revisa los datos actuales.";

/** Estado al que lleva cada acción: si la recarga tras un fallo incierto ya lo muestra, se aplicó. */
const ESTADO_TRAS: Record<"aprobar" | "rechazar", string> = {
  aprobar: "APROBADO",
  rechazar: "RECHAZADO",
};

/** N2 de la revisión 9a: red o 5xx al aprobar/rechazar, y la recarga muestra el trámite ya cerrado.
 * Decir "Inténtalo de nuevo" contradiría lo que se ve: se dice lo que consta. */
function etiquetaEnFrase(estado: string): string {
  const etiqueta = presentacionEstado(estado).etiqueta;
  return `${etiqueta.charAt(0).toLowerCase()}${etiqueta.slice(1)}`;
}

function avisoResultadoIncierto(accion: "aprobar" | "rechazar", estado: string): AvisoRevision {
  // Justo lo pedido: se aplicó, aunque no llegara la respuesta.
  if (estado === ESTADO_TRAS[accion]) {
    return {
      tipo: "exito",
      accion,
      mensaje: `No hubo respuesta a tiempo, pero el trámite consta como ${etiquetaEnFrase(estado)}.`,
    };
  }
  // Otro estado (m4 de 9b): hoy RECHAZADO; tras 3c, también EN_PROCESO o EJECUTADO_OVZ después de un
  // aprobar que sí se aplicó. No se afirma que "no se ha aprobado": solo lo que consta.
  return {
    tipo: "estado-cambiado",
    accion,
    mensaje: `No hubo respuesta a tiempo. El trámite consta ahora como ${etiquetaEnFrase(estado)}.`,
  };
}

type ResultadoPedido = { ok: true; detalle: TramiteDetalle } | { ok: false; error: ErrorApi };

/** Vida de un `tramiteId` montado. Lo que termine después de cerrarla no toca el estado. */
interface Sesion {
  /** El trámite de la sesión: un manejador de otro trámite (closure viejo) no puede usarla. */
  tramiteId: number | null;
  activa: boolean;
  /** Envío único síncrono: el estado de React llega un render tarde para dos clics seguidos. */
  enviando: boolean;
  recargas: Set<AbortController>;
}

function cargaFallida(err: unknown): CargaRevision {
  const error = aErrorApi(err);
  const mensaje = mensajeDeError(error, "detalle-tramite");
  return error.tipo === "no-encontrado" ? { estado: "no-encontrado", mensaje } : { estado: "error", error, mensaje };
}

function tipoDeAviso(error: ErrorApi): TipoAvisoRevision {
  switch (error.tipo) {
    case "conflicto":
    case "validacion":
    case "prohibido":
    case "no-encontrado":
      return error.tipo;
    default:
      return "error";
  }
}

export function useRevisionTramite(
  tramiteId: number | null,
  { onCambiado }: OpcionesRevisionTramite,
): RevisionTramite {
  const [estado, dispatch] = useReducer(reducer, tramiteId, estadoInicial);
  const [intentoCarga, setIntentoCarga] = useState(0);
  const sesionRef = useRef<Sesion | null>(null);
  const onCambiadoRef = useRef(onCambiado);

  useLayoutEffect(() => {
    onCambiadoRef.current = onCambiado;
  });

  // Al cambiar de trámite, el estado se reinicia en el mismo render: nunca se ve (ni un render)
  // el detalle o el formulario del anterior bajo el id nuevo.
  let actual = estado;
  if (estado.tramiteId !== tramiteId) {
    actual = estadoInicial(tramiteId);
    dispatch({ tipo: "reiniciar", tramiteId });
  }

  // Una sesión por trámite: al cambiar de id o desmontar se cierra y se abortan sus recargas.
  useEffect(() => {
    const sesion: Sesion = { tramiteId, activa: true, enviando: false, recargas: new Set() };
    sesionRef.current = sesion;
    return () => {
      sesion.activa = false;
      sesion.recargas.forEach((controlador) => controlador.abort());
    };
  }, [tramiteId]);

  // Carga inicial y "Reintentar". Se aborta al cambiar de id o desmontar; cancelar no es un error.
  useEffect(() => {
    if (tramiteId === null) return;
    const controlador = new AbortController();
    obtenerDetalleTramite(tramiteId, controlador.signal)
      .then((detalle) => {
        if (!controlador.signal.aborted) dispatch({ tipo: "cargado", detalle });
      })
      .catch((err: unknown) => {
        if (controlador.signal.aborted || esCancelacion(err)) return;
        dispatch({ tipo: "carga-fallida", carga: cargaFallida(err) });
      });
    return () => controlador.abort();
  }, [tramiteId, intentoCarga]);

  const { carga, detalle, formulario, enviando, aviso, recargaFallida } = actual;
  const editable = detalle !== null && carga.estado === "listo" && esEditable(detalle.estado);
  const sucio = detalle !== null && estaSucio(detalle, formulario);
  const libre = enviando === null;
  const puedeGuardar = editable && sucio && libre;
  const puedeAprobar = editable && !sucio && libre;
  const puedeRechazar = puedeAprobar;

  /**
   * Pide el detalle dentro de la sesión (la recarga se aborta si la sesión se cierra). null si la
   * sesión ya no está activa cuando llega la respuesta, o si se canceló: nadie debe escribirla.
   */
  async function pedirDetalle(sesion: Sesion, id: number): Promise<ResultadoPedido | null> {
    const controlador = new AbortController();
    sesion.recargas.add(controlador);
    try {
      const nuevo = await obtenerDetalleTramite(id, controlador.signal);
      return sesion.activa ? { ok: true, detalle: nuevo } : null;
    } catch (err) {
      if (!sesion.activa || esCancelacion(err)) return null;
      return { ok: false, error: aErrorApi(err) };
    } finally {
      sesion.recargas.delete(controlador);
    }
  }

  /** Recarga que descarta el formulario; si falla, no queda ningún detalle a la vista (el que había
   * puede estar desfasado): 409 y resultado incierto de aprobar/rechazar. */
  async function recargarEstricto(sesion: Sesion, id: number): Promise<ResultadoPedido | null> {
    const resultado = await pedirDetalle(sesion, id);
    if (!resultado) return null;
    dispatch(
      resultado.ok
        ? { tipo: "cargado", detalle: resultado.detalle }
        : { tipo: "carga-fallida", carga: cargaFallida(resultado.error) },
    );
    return resultado;
  }

  /** Tras un 200 de aprobar/rechazar: el detalle fusionado ya es correcto, así que si la recarga
   * falla se conserva y solo se avisa (no bloqueante). */
  async function recargarTrasExito(sesion: Sesion, id: number): Promise<void> {
    const resultado = await pedirDetalle(sesion, id);
    if (!resultado) return;
    dispatch(
      resultado.ok
        ? { tipo: "cargado", detalle: resultado.detalle }
        : { tipo: "recarga-fallida", mensaje: mensajeDeError(resultado.error, "detalle-tramite") },
    );
  }

  function reintentarRecarga(): void {
    const sesion = sesionRef.current;
    if (
      recargaFallida === null ||
      tramiteId === null ||
      !sesion ||
      !sesion.activa ||
      sesion.tramiteId !== tramiteId ||
      sesion.recargas.size > 0
    ) {
      return;
    }
    dispatch({ tipo: "reintentando-recarga" });
    void recargarTrasExito(sesion, tramiteId);
  }

  /**
   * 404 de una acción: se recarga para saber qué pasó.
   * - La recarga también da 404 → el trámite ya no existe (o no es de la gestoría): no-encontrado.
   * - Tras un PATCH, la recarga va bien y el trámite sigue igual (misma versión que se envió y aún
   *   pendiente) → el problema era la explotación elegida: se conservan las ediciones con un aviso,
   *   y la cola no se refresca (en el servidor no cambió nada).
   * - Tras un PATCH, la recarga trae otra versión o ya no está pendiente → como un 409 (N1).
   */
  async function trasNoEncontrado(
    accion: AccionRevision,
    mensaje: string,
    sesion: Sesion,
    id: number,
    versionEnviada: number | undefined,
  ) {
    const resultado = sesion.activa ? await pedirDetalle(sesion, id) : null;
    if (resultado?.ok && accion === "guardar") {
      const mismoTramite =
        resultado.detalle.version === versionEnviada && esEditable(resultado.detalle.estado);
      if (!mismoTramite) {
        onCambiadoRef.current();
        dispatch({ tipo: "cargado", detalle: resultado.detalle });
        dispatch({
          tipo: "aviso",
          aviso: { tipo: "conflicto", accion, mensaje: TEXTO_TRAMITE_CAMBIADO },
        });
        return;
      }
      dispatch({ tipo: "detalle-refrescado", detalle: resultado.detalle });
      dispatch({
        tipo: "aviso",
        aviso: { tipo: "no-encontrado", accion, mensaje: TEXTO_EXPLOTACION_NO_DISPONIBLE },
      });
      return;
    }
    onCambiadoRef.current();
    if (!resultado) return;
    if (resultado.ok) {
      // Aprobar/rechazar dio 404 pero el trámite se puede leer: no debería pasar. Se enseña lo
      // fresco y el texto del 404, sin inventar otra causa.
      dispatch({ tipo: "cargado", detalle: resultado.detalle });
      dispatch({ tipo: "aviso", aviso: { tipo: "no-encontrado", accion, mensaje } });
      return;
    }
    if (resultado.error.tipo !== "no-encontrado") {
      dispatch({ tipo: "aviso", aviso: { tipo: "no-encontrado", accion, mensaje } });
    }
    // 404 confirmado: el estado no-encontrado ya lleva el texto (sin repetirlo en `aviso`).
    dispatch({ tipo: "carga-fallida", carga: cargaFallida(resultado.error) });
  }

  /** Decisiones 10–13. Un 409 recarga (y descarta el formulario) sin reintentar nunca la acción. */
  async function manejarError(
    accion: AccionRevision,
    err: unknown,
    sesion: Sesion,
    id: number,
    versionEnviada: number | undefined,
  ) {
    const error = aErrorApi(err);
    const tipo = tipoDeAviso(error);
    const mensaje = mensajeDeError(error, CONTEXTO_DE_ACCION[accion]);
    if (tipo === "no-encontrado") {
      await trasNoEncontrado(accion, mensaje, sesion, id, versionEnviada);
      return;
    }
    // Red o 5xx al aprobar/rechazar: puede que el servidor sí lo aplicara. Se refresca la cola y se
    // recarga el detalle para enseñar el estado real (M5). Guardar no: se perderían las ediciones.
    const incierto = accion !== "guardar" && (error.tipo === "red" || error.tipo === "servidor");
    // 409 (el de "resolución cambiada" guarda datos) e incierto cambian lo que la cola debe enseñar.
    if (tipo === "conflicto" || incierto) onCambiadoRef.current();
    if (!sesion.activa) return;
    dispatch({ tipo: "aviso", aviso: { tipo, accion, mensaje } });
    if (tipo !== "conflicto" && !incierto) return;
    const resultado = await recargarEstricto(sesion, id);
    if (incierto && resultado?.ok && !esEditable(resultado.detalle.estado)) {
      // N2: ya no está pendiente, así que no se invita a reintentar.
      dispatch({ tipo: "aviso", aviso: avisoResultadoIncierto(accion, resultado.detalle.estado) });
    }
  }

  /**
   * Envío único: con otra acción en curso (en esta sesión), o si no se puede, no hace nada. La
   * recarga que sigue a la acción forma parte del envío: `enviando` sigue puesto hasta que termina.
   * Un manejador de otro trámite (closure de un render anterior) no usa la sesión actual.
   */
  async function ejecutar(
    accion: AccionRevision,
    permitido: boolean,
    peticion: (sesion: Sesion, id: number) => Promise<void>,
    /** Versión que lleva la petición (solo guardar): para saber, tras un 404, si el trámite cambió. */
    versionEnviada?: number,
  ): Promise<void> {
    const sesion = sesionRef.current;
    if (
      !permitido ||
      tramiteId === null ||
      !sesion ||
      !sesion.activa ||
      sesion.tramiteId !== tramiteId ||
      sesion.enviando
    ) {
      return;
    }
    sesion.enviando = true;
    dispatch({ tipo: "enviar", accion });
    try {
      await peticion(sesion, tramiteId);
    } catch (err) {
      await manejarError(accion, err, sesion, tramiteId, versionEnviada);
    } finally {
      sesion.enviando = false;
      if (sesion.activa) dispatch({ tipo: "fin-envio" });
    }
  }

  function guardar(): Promise<void> {
    const patch = detalle ? construirPatch(detalle, formulario) : null;
    return ejecutar(
      "guardar",
      puedeGuardar && patch !== null,
      async (sesion, id) => {
        const nuevo = await actualizarTramite(id, patch!);
        onCambiadoRef.current();
        if (sesion.activa) dispatch({ tipo: "guardado", detalle: nuevo });
      },
      patch?.version,
    );
  }

  function cerrarRevision(accion: "aprobar" | "rechazar", permitido: boolean): Promise<void> {
    const version = detalle?.version;
    return ejecutar(accion, permitido && version !== undefined, async (sesion, id) => {
      const tramite = accion === "aprobar" ? await aprobarTramite(id, version!) : await rechazarTramite(id);
      onCambiadoRef.current();
      if (!sesion.activa) return;
      dispatch({
        tipo: "respuesta-accion",
        tramite,
        aviso: { tipo: "exito", accion, mensaje: TEXTO_EXITO[accion] },
      });
      await recargarTrasExito(sesion, id);
    });
  }

  function editar(cambio: Cambio): ResultadoCambio {
    const motivo = rechazoDeCambio(actual, cambio);
    if (motivo !== null) return { aceptado: false, motivo };
    dispatch({ tipo: "editar", cambio });
    return { aceptado: true };
  }

  return {
    carga,
    detalle,
    reintentarCarga: () => {
      dispatch({ tipo: "reintentar" });
      setIntentoCarga((n) => n + 1);
    },
    recargaFallida,
    reintentarRecarga,
    explotacionAsignada:
      detalle && detalle.explotacionId !== null
        ? {
            id: detalle.explotacionId,
            codigoRega: detalle.explotacionCodigoRega,
            nombre: detalle.explotacionNombre,
          }
        : null,

    formulario,
    editable,
    sucio,
    puedeQuitarExplotacion: detalle?.explotacionId == null,
    puedeQuitarTipo: detalle?.tipoTramite == null,
    cambiarExplotacion: (valor) => editar({ campo: "explotacion", valor }),
    cambiarTipoTramite: (valor) => editar({ campo: "tipo", valor }),
    anadirCrotal: (valor = "") => editar({ campo: "anadir-crotal", valor }),
    cambiarCrotal: (indice, valor) => editar({ campo: "cambiar-crotal", indice, valor }),
    quitarCrotal: (indice) => editar({ campo: "quitar-crotal", indice }),
    descartarCambios: () => dispatch({ tipo: "descartar-cambios" }),

    enviando,
    puedeGuardar,
    puedeAprobar,
    puedeRechazar,
    avisoCambiosSinGuardar: editable && sucio ? AVISO_CAMBIOS_SIN_GUARDAR : null,
    guardar,
    aprobar: () => cerrarRevision("aprobar", puedeAprobar),
    rechazar: () => cerrarRevision("rechazar", puedeRechazar),

    aviso,
    descartarAviso: () => dispatch({ tipo: "descartar-aviso" }),
  };
}
