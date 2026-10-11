import { useEffect, useRef, useState } from "react";
import { aErrorApi, mensajeDeError, TEXTO_TRAMITE_NO_ENCONTRADO } from "@/shared/api/errores";
import { responderPorWhatsapp } from "./api";
import { claveIdempotencia, problemaDelTexto, textoPropuesto } from "./respuestaWhatsapp";
import type { RespuestaWhatsapp, TramiteDetalle } from "./types";
import type { ResultadoRecargaWhatsapp } from "./useRevisionTramite";

/**
 * Panel «Responder por WhatsApp» del modal de revisión (B2 reducido, D4/D7/D9/D13).
 *
 * Independiente del formulario del trámite: se puede responder con cambios sin guardar, y responder
 * no toca la `version` ni descarta nada (solo cambia el bloque `whatsapp` del detalle, por las dos
 * funciones que recibe de `useRevisionTramite`).
 *
 * Idempotencia (D7): abrir el panel genera una clave (`claveIdempotencia()`); cerrar o un éxito la
 * descartan. Tras un error de red o un 5xx sin motivo la clave se CONSERVA: el reintento lleva la
 * misma y el servidor devuelve la guardada sin mandarla otra vez. Tras un 409 se genera otra: un 409
 * nunca deja nada guardado con esa clave (o es «clave con otro texto», que exige una nueva, o es una
 * comprobación previa al envío). Una petición a la vez (guarda síncrona: el estado de React llega un
 * render tarde para un doble clic).
 */

/** Red o 5xx sin motivo: puede que el servidor sí lo mandara. Reintentar es seguro (misma clave). */
export const TEXTO_RESULTADO_INCIERTO =
  "No se ha podido comprobar si ha salido. Puedes reintentar: no se mandará dos veces.";
export const TEXTO_RESPUESTA_ACEPTADA = "Respuesta aceptada por WhatsApp.";
/** Repetición de una clave cuya fila sigue saliendo (hace menos de 2 minutos). */
export const TEXTO_RESPUESTA_EN_CAMINO = "La respuesta está saliendo. Su estado se verá en el historial.";
/** Reserva si el backend no mandara `motivoError` (lo manda siempre en FALLIDO y SIN_CONFIRMAR). */
const TEXTO_FALLIDA_RESERVA = "WhatsApp no ha aceptado el mensaje.";
const TEXTO_SIN_CONFIRMAR_RESERVA = "No sabemos si ha llegado. Compruébalo antes de volver a enviarlo.";

export type TipoErrorRespuesta = "validacion" | "conflicto" | "no-encontrado" | "incierto" | "otro";

export interface ErrorRespuesta {
  tipo: TipoErrorRespuesta;
  mensaje: string;
}

/** Resultado de la última respuesta mandada (tras un 201/200). El panel ya está cerrado. */
export type ResultadoRespuesta =
  | { tipo: "aceptada"; mensaje: string }
  | { tipo: "en-camino"; mensaje: string }
  | { tipo: "fallida"; mensaje: string }
  | { tipo: "sin-confirmar"; mensaje: string };

export interface RespuestaWhatsappPanel {
  /** Se ofrece «Responder por WhatsApp»: hay bloque, el servidor no da motivo para impedirlo y en
   * este estado hay texto propuesto. */
  puedeResponder: boolean;
  abierto: boolean;
  texto: string;
  /** El panel está abierto y el texto ya no es el propuesto: cerrar el modal lo perdería (N8). */
  editado: boolean;
  /** Por qué no se puede enviar el texto (mismos textos que los 400), o null. */
  problema: string | null;
  enviando: boolean;
  puedeEnviar: boolean;
  error: ErrorRespuesta | null;
  resultado: ResultadoRespuesta | null;
  abrir: () => void;
  cancelar: () => void;
  cambiarTexto: (texto: string) => void;
  enviar: () => Promise<void>;
  descartarResultado: () => void;
}

interface Dependencias {
  insertarRespuestaWhatsapp: (respuesta: RespuestaWhatsapp) => void;
  recargarWhatsapp: () => Promise<ResultadoRecargaWhatsapp>;
}

function nuevaClave(): string {
  return claveIdempotencia();
}

function resultadoDe(respuesta: RespuestaWhatsapp): ResultadoRespuesta {
  switch (respuesta.estado) {
    case "FALLIDO":
      return { tipo: "fallida", mensaje: respuesta.motivoError ?? TEXTO_FALLIDA_RESERVA };
    case "SIN_CONFIRMAR":
      return { tipo: "sin-confirmar", mensaje: respuesta.motivoError ?? TEXTO_SIN_CONFIRMAR_RESERVA };
    case "ENVIANDO":
      return { tipo: "en-camino", mensaje: TEXTO_RESPUESTA_EN_CAMINO };
    default:
      // ENVIADO, ENTREGADO, LEIDO (y uno desconocido, que el historial etiqueta tal cual).
      return { tipo: "aceptada", mensaje: TEXTO_RESPUESTA_ACEPTADA };
  }
}

export function useRespuestaWhatsapp(
  tramiteId: number | null,
  detalle: TramiteDetalle | null,
  { insertarRespuestaWhatsapp, recargarWhatsapp }: Dependencias,
): RespuestaWhatsappPanel {
  const [abierto, setAbierto] = useState(false);
  const [texto, setTexto] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<ErrorRespuesta | null>(null);
  const [resultado, setResultado] = useState<ResultadoRespuesta | null>(null);
  const claveRef = useRef<string | null>(null);
  /** Texto propuesto al abrir: con él se sabe si la persona ha escrito algo (N8). */
  const [propuestaAlAbrir, setPropuestaAlAbrir] = useState("");
  const enviandoRef = useRef(false);
  /** Trámite al que pertenece lo que está en vuelo: si cambia, su resultado se ignora. */
  const sesionRef = useRef(tramiteId);

  // Otro trámite (o modal cerrado): el panel empieza de cero.
  const [idPrevio, setIdPrevio] = useState(tramiteId);
  if (idPrevio !== tramiteId) {
    setIdPrevio(tramiteId);
    setAbierto(false);
    setTexto("");
    setEnviando(false);
    setError(null);
    setResultado(null);
  }
  useEffect(() => {
    sesionRef.current = tramiteId;
    claveRef.current = null;
    enviandoRef.current = false;
  }, [tramiteId]);

  const whatsapp = detalle?.whatsapp ?? null;
  const propuesto = detalle ? textoPropuesto(detalle) : "";
  const puedeResponder = whatsapp !== null && whatsapp.motivoNoDisponible === null && propuesto !== "";
  const problema = problemaDelTexto(texto);
  const disponible = whatsapp !== null && whatsapp.motivoNoDisponible === null;
  const puedeEnviar = abierto && !enviando && problema === null && disponible;

  function abrir() {
    if (!puedeResponder || abierto) return;
    claveRef.current = nuevaClave();
    setTexto(propuesto);
    setPropuestaAlAbrir(propuesto);
    setError(null);
    setResultado(null);
    setAbierto(true);
  }

  function cancelar() {
    if (enviandoRef.current) return;
    claveRef.current = null;
    setAbierto(false);
    setError(null);
  }

  async function enviar(): Promise<void> {
    if (!puedeEnviar || tramiteId === null || enviandoRef.current) return;
    const clave = claveRef.current ?? nuevaClave();
    claveRef.current = clave;
    const id = tramiteId;
    enviandoRef.current = true;
    setEnviando(true);
    setError(null);
    try {
      const respuesta = await responderPorWhatsapp(id, texto, clave);
      if (sesionRef.current !== id) return;
      insertarRespuestaWhatsapp(respuesta);
      claveRef.current = null;
      setAbierto(false);
      setTexto("");
      setResultado(resultadoDe(respuesta));
    } catch (err) {
      if (sesionRef.current !== id) return;
      const errorApi = aErrorApi(err);
      switch (errorApi.tipo) {
        case "validacion":
          // Nada se guardó; se conservan el texto y la clave.
          setError({ tipo: "validacion", mensaje: mensajeDeError(errorApi, "responder-whatsapp") });
          break;
        case "conflicto":
          claveRef.current = nuevaClave();
          setError({ tipo: "conflicto", mensaje: mensajeDeError(errorApi, "responder-whatsapp") });
          // El texto se queda en el panel; se refresca solo el bloque (ventana, motivo, historial).
          await recargarWhatsapp();
          break;
        case "no-encontrado": {
          const recarga = await recargarWhatsapp();
          // Si la recarga también da 404, el modal entero pasa a «no encontrado»: el panel se cierra
          // con él (si no, quedaría abierto por debajo y el primer Esc solo lo cerraría a él: M1).
          if (recarga !== "no-encontrado") {
            setError({ tipo: "no-encontrado", mensaje: TEXTO_TRAMITE_NO_ENCONTRADO });
          } else {
            claveRef.current = null;
            setAbierto(false);
            setTexto("");
          }
          break;
        }
        case "red":
        case "servidor":
          // Un 503 con motivo es «no configurado»: no se guardó nada. Sin motivo, quizá sí salió:
          // la misma clave se conserva para reintentar sin duplicar.
          setError(
            errorApi.motivo
              ? { tipo: "otro", mensaje: errorApi.motivo }
              : { tipo: "incierto", mensaje: TEXTO_RESULTADO_INCIERTO },
          );
          break;
        default:
          setError({ tipo: "otro", mensaje: mensajeDeError(errorApi, "responder-whatsapp") });
      }
    } finally {
      if (sesionRef.current === id) {
        enviandoRef.current = false;
        setEnviando(false);
      }
    }
  }

  return {
    puedeResponder,
    abierto,
    texto,
    editado: abierto && texto !== propuestaAlAbrir,
    problema,
    enviando,
    puedeEnviar,
    error,
    resultado,
    abrir,
    cancelar,
    cambiarTexto: (nuevo) => setTexto(nuevo),
    enviar,
    descartarResultado: () => setResultado(null),
  };
}
