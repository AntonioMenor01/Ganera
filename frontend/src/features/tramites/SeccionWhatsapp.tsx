import { useEffect, useId, useRef } from "react";
import { CircleAlertIcon, CircleCheckIcon, TriangleAlertIcon } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { presentacionRespuestaWhatsapp } from "./etiquetas";
import { fechaHoraMadrid } from "./fechaMadrid";
import { HUECO, MAX_CARACTERES, estadoVentana } from "./respuestaWhatsapp";
import type { RespuestaWhatsapp, WhatsappTramite } from "./types";
import type { RespuestaWhatsappPanel } from "./useRespuestaWhatsapp";

/**
 * Sección «WhatsApp» del modal de revisión (B2 reducido, D13), debajo del mensaje original: a quién
 * se responde, la ventana de 24 h, el historial de respuestas y el panel en línea para responder.
 *
 * Vocabulario (test de honestidad): nada visible dice «enviado» ni derivados. Lo que WhatsApp aceptó
 * es «Aceptado por WhatsApp»; el botón es «Enviar por WhatsApp» (un verbo en infinitivo, no un estado).
 * Lo decide todo el servidor; aquí solo se refleja (la ventana se recalcula con la hora del equipo
 * solo para avisar de que se cierra).
 */

/** Texto del 409 de ventana cerrada del backend: se enseña igual si, con el modal abierto, la hora
 * del equipo ya ha pasado el final de la ventana que dio el servidor. */
export const TEXTO_VENTANA_CERRADA =
  "Han pasado más de 24 horas desde su último mensaje. WhatsApp no deja escribirle hasta que vuelva a escribir.";

/** Confirmación en línea al cerrar el modal con una respuesta escrita y sin mandar (N8). La decide
 * el modal (que es quien se cierra); aquí solo se pinta, en el sitio de los botones del panel. */
export interface DescarteRespuesta {
  visible: boolean;
  onSeguirEscribiendo: () => void;
  onDescartarYCerrar: () => void;
}

export const PREGUNTA_DESCARTAR_RESPUESTA = "Tienes una respuesta sin enviar.";

interface SeccionWhatsappProps {
  whatsapp: WhatsappTramite;
  panel: RespuestaWhatsappPanel;
  descarte?: DescarteRespuesta;
  /** Hora de referencia de la ventana (inyectable para los tests). */
  ahora?: Date;
}

export function SeccionWhatsapp({ whatsapp, panel, descarte, ahora = new Date() }: SeccionWhatsappProps) {
  const idTitulo = useId();
  const idHistorial = useId();
  const ventana = estadoVentana(whatsapp.ventanaAbiertaHasta, ahora);
  const motivoBloqueo =
    whatsapp.motivoNoDisponible ?? (ventana.tipo === "cerrada" ? TEXTO_VENTANA_CERRADA : null);
  const ofrecerBoton = panel.puedeResponder && motivoBloqueo === null && !panel.abierto;

  const botonResponderRef = useRef<HTMLButtonElement | null>(null);
  // Al cerrar el panel (Cancelar o un envío correcto) el foco vuelve a «Responder por WhatsApp»,
  // si sigue ahí; nunca se queda en un botón que ha desaparecido.
  const abiertoPrevio = useRef(panel.abierto);
  useEffect(() => {
    const antes = abiertoPrevio.current;
    abiertoPrevio.current = panel.abierto;
    if (antes && !panel.abierto) botonResponderRef.current?.focus();
  }, [panel.abierto]);

  return (
    <section aria-labelledby={idTitulo} data-seccion-whatsapp className="flex min-w-0 flex-col gap-3">
      <h3 id={idTitulo} className="text-sm font-bold text-muted-foreground">
        WhatsApp
      </h3>
      <p className="text-sm wrap-anywhere">
        <span className="font-medium">{whatsapp.destinatarioNombre}</span>
        {" · "}
        <span className="tabular-nums">{whatsapp.telefonoEnmascarado}</span>
      </p>

      {motivoBloqueo !== null ? (
        <AvisoAmbar texto={motivoBloqueo} />
      ) : ventana.tipo === "cierra-pronto" ? (
        <AvisoAmbar texto={`La ventana se cierra a las ${ventana.hora}`} />
      ) : ventana.tipo === "abierta" ? (
        <p data-ventana-whatsapp className="text-sm text-muted-foreground">
          Puedes escribirle hasta el {ventana.hasta}
        </p>
      ) : null}

      {whatsapp.respuestas.length > 0 && (
        <div className="flex min-w-0 flex-col gap-2">
          <h4 id={idHistorial} className="text-sm font-bold text-muted-foreground">
            Respuestas por WhatsApp
          </h4>
          <ol aria-labelledby={idHistorial} className="flex min-w-0 flex-col gap-2">
            {whatsapp.respuestas.map((respuesta) => (
              <FilaRespuesta key={respuesta.id} respuesta={respuesta} />
            ))}
          </ol>
        </div>
      )}

      <ResultadoRespuesta panel={panel} />

      {panel.abierto ? (
        // Bloqueado también si la ventana se ha cerrado según la hora del equipo con el panel abierto
        // (N1): el aviso ya lo dice; el servidor lo rechazaría con 409 de todas formas.
        <PanelRespuesta panel={panel} bloqueado={motivoBloqueo !== null} descarte={descarte} />
      ) : (
        ofrecerBoton && (
          <div>
            <Button ref={botonResponderRef} variant="outline" onClick={panel.abrir}>
              Responder por WhatsApp
            </Button>
          </div>
        )
      )}
    </section>
  );
}

/** El par ámbar de aviso, fijo y sin `role` (es un hecho de los datos, patrón de AvisosRevision). */
function AvisoAmbar({ texto }: { texto: string }) {
  return (
    <div
      data-aviso-whatsapp
      className="flex items-start gap-2 rounded-lg bg-warning px-2.5 py-2 text-sm text-warning-foreground"
    >
      <TriangleAlertIcon aria-hidden className="mt-0.5 size-4 shrink-0" />
      <p className="min-w-0 wrap-anywhere">{texto}</p>
    </div>
  );
}

function FilaRespuesta({ respuesta }: { respuesta: RespuestaWhatsapp }) {
  const { etiqueta, variante } = presentacionRespuestaWhatsapp(respuesta.estado);
  const fecha = fechaHoraMadrid(respuesta.enviadoEn);
  const quien = respuesta.enviadoPorNombre?.trim();
  return (
    <li className="flex min-w-0 flex-col gap-1 rounded-lg border px-3 py-2">
      <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground">
        <span className="tabular-nums">{fecha ?? "—"}</span>
        {quien && (
          <>
            <span aria-hidden="true">·</span>
            <span className="min-w-0 wrap-anywhere">{quien}</span>
          </>
        )}
        <Badge variant={variante} className="ml-auto">
          {etiqueta}
        </Badge>
      </div>
      <p className="text-sm whitespace-pre-wrap wrap-anywhere">{respuesta.texto}</p>
      {respuesta.motivoError && <p className="text-sm text-muted-foreground wrap-anywhere">{respuesta.motivoError}</p>}
    </li>
  );
}

/** Resultado de la última respuesta: la aceptada y la sin confirmar en una región `status` siempre
 * montada (para que se anuncien); la que no ha llegado, un Alert destructivo. */
function ResultadoRespuesta({ panel }: { panel: RespuestaWhatsappPanel }) {
  const { resultado } = panel;
  return (
    <>
      <div role="status" className="empty:hidden">
        {(resultado?.tipo === "aceptada" || resultado?.tipo === "en-camino") && (
          <p data-resultado-whatsapp className="flex items-start gap-2 text-sm text-muted-foreground">
            <CircleCheckIcon aria-hidden className="mt-0.5 size-4 shrink-0" />
            <span>{resultado.mensaje}</span>
          </p>
        )}
        {resultado?.tipo === "sin-confirmar" && <AvisoAmbar texto={resultado.mensaje} />}
      </div>
      {resultado?.tipo === "fallida" && (
        <Alert variant="destructive" data-resultado-whatsapp>
          <CircleAlertIcon />
          <AlertTitle>La respuesta no ha llegado</AlertTitle>
          <AlertDescription>
            <p>{resultado.mensaje}</p>
          </AlertDescription>
        </Alert>
      )}
    </>
  );
}

function PanelRespuesta({
  panel,
  bloqueado,
  descarte,
}: {
  panel: RespuestaWhatsappPanel;
  bloqueado: boolean;
  descarte?: DescarteRespuesta;
}) {
  const idCampo = useId();
  const idContador = useId();
  const idPista = useId();
  const idPregunta = useId();
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const seguirRef = useRef<HTMLButtonElement | null>(null);
  const errorRef = useRef<HTMLDivElement | null>(null);

  // N2 (smoke de la T3): tras un error el foco va a la alerta, para que no se pierda en `body` cuando
  // «Enviar» se desactiva (p. ej. un 409 que cierra la ventana). Además se desplaza a la vista: Firefox
  // no desplaza el cuerpo del modal al enfocar (T3b). `?.` porque jsdom no tiene scrollIntoView.
  useEffect(() => {
    if (!panel.error) return;
    errorRef.current?.focus();
    errorRef.current?.scrollIntoView?.({ block: "nearest" });
  }, [panel.error]);
  const preguntando = descarte?.visible ?? false;

  // La pregunta de descartar: al aparecer, el foco va a la opción que no pierde nada; al retirarla
  // (Seguir escribiendo o Esc), vuelve al texto.
  const preguntandoPrevio = useRef(preguntando);
  useEffect(() => {
    const antes = preguntandoPrevio.current;
    preguntandoPrevio.current = preguntando;
    if (preguntando) seguirRef.current?.focus();
    else if (antes) textareaRef.current?.focus();
  }, [preguntando]);

  // Al abrir: foco al texto, con el hueco seleccionado si lo hay (escribir lo sustituye), o al final.
  useEffect(() => {
    const campo = textareaRef.current;
    if (!campo) return;
    campo.focus();
    const hueco = campo.value.indexOf(HUECO);
    if (hueco >= 0) campo.setSelectionRange(hueco, hueco + HUECO.length);
    else campo.setSelectionRange(campo.value.length, campo.value.length);
  }, []);

  const largo = panel.texto.length > MAX_CARACTERES;
  const descritoPor = [panel.problema ? idPista : null, idContador].filter(Boolean).join(" ");
  return (
    <div data-panel-whatsapp className="flex min-w-0 flex-col gap-2 rounded-lg border bg-muted/50 p-3">
      <label htmlFor={idCampo} className="text-sm font-medium text-muted-foreground">
        Respuesta
      </label>
      <Textarea
        ref={textareaRef}
        id={idCampo}
        rows={6}
        value={panel.texto}
        onChange={(evento) => panel.cambiarTexto(evento.target.value)}
        disabled={panel.enviando}
        aria-invalid={largo || undefined}
        aria-describedby={descritoPor}
        className="bg-background"
      />
      <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1 text-sm text-muted-foreground">
        <p id={idPista} className="min-w-0">
          {panel.problema}
        </p>
        <p id={idContador} className="ml-auto tabular-nums">
          {panel.texto.length} / {MAX_CARACTERES}
        </p>
      </div>
      {panel.error && (
        <Alert
          ref={errorRef}
          tabIndex={-1}
          variant="destructive"
          data-error-whatsapp={panel.error.tipo}
          className="outline-none focus-visible:ring-3 focus-visible:ring-ring"
        >
          <CircleAlertIcon />
          <AlertTitle>
            {panel.error.tipo === "incierto" ? "No sabemos si ha salido" : "No se ha mandado la respuesta"}
          </AlertTitle>
          <AlertDescription>
            <p>{panel.error.mensaje}</p>
          </AlertDescription>
        </Alert>
      )}
      {preguntando && descarte ? (
        <div role="group" aria-labelledby={idPregunta} className="flex flex-wrap items-center gap-2">
          <p id={idPregunta} className="mr-auto text-sm font-medium">
            {PREGUNTA_DESCARTAR_RESPUESTA}
          </p>
          <div className="flex flex-wrap items-center gap-2">
            <Button ref={seguirRef} variant="outline" onClick={descarte.onSeguirEscribiendo}>
              Seguir escribiendo
            </Button>
            <Button variant="destructive" onClick={descarte.onDescartarYCerrar}>
              Descartar y cerrar
            </Button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap items-center justify-end gap-2">
          <Button variant="outline" onClick={panel.cancelar} disabled={panel.enviando}>
            Cancelar
          </Button>
          <Button
            onClick={() => void panel.enviar()}
            // Mientras se envía no se desactiva de verdad: un botón `disabled` con el foco lo tira a
            // `body` (N2). Queda aria-disabled, con el mismo aspecto, y el clic no hace nada (la guarda
            // del hook impide una segunda petición).
            disabled={!panel.enviando && (!panel.puedeEnviar || bloqueado)}
            aria-disabled={panel.enviando || undefined}
            className="aria-disabled:pointer-events-none aria-disabled:opacity-50"
            aria-describedby={panel.problema ? idPista : undefined}
          >
            {panel.enviando ? "Enviando…" : "Enviar por WhatsApp"}
          </Button>
        </div>
      )}
    </div>
  );
}
