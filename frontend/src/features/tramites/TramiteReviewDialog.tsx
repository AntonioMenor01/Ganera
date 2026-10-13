import { useEffect, useId, useRef, useState, type ReactNode, type RefObject } from "react";
import { CircleAlertIcon, CircleCheckIcon, ExternalLinkIcon, XIcon } from "lucide-react";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { avisosExtraccion, esDescartado, etiquetaTipoTramite, TIPOS_TRAMITE, type TipoTramite } from "./etiquetas";
import {
  PISTA_DESCARTAR_SIN_GUARDAR,
  PISTA_DESHACER,
  TEXTO_BOTON_DESCARTANDO,
  TEXTO_BOTON_DESCARTAR,
  TEXTO_BOTON_DESHACER,
  TEXTO_BOTON_DESHACIENDO,
  lineasHistorialDescarte,
  textoBandaDescartado,
} from "./descarte";
import { BadgeEstadoTramite } from "./BadgesTramite";
import { AvisosRevision } from "./AvisosRevision";
import { CampoExplotacion, TextoExplotacion } from "./CampoExplotacion";
import { BadgeSinGuardar, CrotalesSoloLectura, ListaCrotalesEditable } from "./ListaCrotales";
import { abrirFichaOvz } from "./abrirFichaOvz";
import {
  PISTA_MARCAR_HECHO_EN_OVZ,
  PREGUNTA_QUITAR_MARCA,
  TEXTO_BOTON_MARCANDO,
  TEXTO_BOTON_MARCAR,
  TEXTO_BOTON_QUITANDO_MARCA,
  TEXTO_BOTON_QUITAR_MARCA,
  TEXTO_CONFIRMAR_QUITAR_MARCA,
  textoHechoEnOvz,
} from "./hechoEnOvz";
import { useRevisionTramite, type AccionRevision, type RevisionTramite } from "./useRevisionTramite";
import { useRespuestaWhatsapp, type RespuestaWhatsappPanel } from "./useRespuestaWhatsapp";
import { SeccionWhatsapp, type DescarteRespuesta } from "./SeccionWhatsapp";
import type { TramiteDetalle } from "./types";

/**
 * Modal de revisión de un trámite (Task 9b; contrato en
 * .impeccable/surfaces/features-tramites-tramitereviewdialog-tsx-930a9a22.md).
 *
 * Una mesa de cotejo: a la izquierda el mensaje de WhatsApp, a la derecha lo que Ganera entendió.
 * Toda la lógica (qué se envía, cuándo, qué pasa con cada error) vive en useRevisionTramite; aquí
 * solo se pinta. "Aprobar" solo cambia el estado del trámite en Ganera: nada de lo que se ve aquí
 * sugiere que ocurra algo en OVZ.net (Prompt 3c).
 *
 * Hecho en OVZ (2026-10-07): en `APROBADO` el pie ofrece «Marcar como hecho en OVZ» (sin confirmación)
 * junto a la ficha; en `HECHO_EN_OVZ` el cuerpo lleva la franja de quién y cuándo, y el pie «Quitar la
 * marca» (confirmación en línea, como Rechazar). Los dos solo dejan constancia de lo que hizo la
 * persona: Ganera no envía nada a OVZ.
 *
 * SIN_TRAMITE (2026-10-12): si el backend marca el trámite `descartable`, el pie ofrece «Descartar»
 * junto a Rechazar, de un clic (sin confirmación: se deshace). Un descartado se ve en solo lectura con
 * una banda (quién y cuándo) y «Deshacer», también de un clic. El historial de descartes sale en
 * cualquier estado si hay eventos. Descartar no envía nada por WhatsApp ni a OVZ.
 */

interface TramiteReviewDialogProps {
  tramiteId: number | null;
  onClose: () => void;
  onCambiado: () => void;
}

/** Confirmación en línea en la barra de acciones (decisión 31): nunca un segundo modal. */
type Confirmacion = "rechazar" | "cerrar" | "quitar-marca" | "descartar-respuesta" | null;

const TEXTO_EN_CURSO: Record<AccionRevision, string> = {
  guardar: "Guardando…",
  aprobar: "Aprobando…",
  rechazar: "Rechazando…",
  marcar: TEXTO_BOTON_MARCANDO,
  desmarcar: TEXTO_BOTON_QUITANDO_MARCA,
  descartar: TEXTO_BOTON_DESCARTANDO,
  deshacer: TEXTO_BOTON_DESHACIENDO,
};

const TEXTO_SIN_TIPO = "Sin determinar todavía";

export function TramiteReviewDialog({
  tramiteId,
  onClose,
  onCambiado,
}: TramiteReviewDialogProps) {
  const revision = useRevisionTramite(tramiteId, { onCambiado });
  const { carga, detalle, editable, sucio, enviando } = revision;
  // B2 reducido: el panel de respuesta por WhatsApp es independiente del formulario del trámite.
  const respuesta = useRespuestaWhatsapp(tramiteId, carga.estado === "listo" ? detalle : null, revision);

  const [confirmacion, setConfirmacion] = useState<Confirmacion>(null);
  // El panel de WhatsApp solo cuenta mientras se ve: si el trámite pasa a «no encontrado» (p. ej. un
  // 404 al guardar con el panel abierto), el modal se cierra como siempre (M1b de la revisión).
  const panelVisible = carga.estado === "listo" && respuesta.abierto;
  // El número que se ve en el título: se conserva durante la animación de cierre (id ya null).
  const [idVisible, setIdVisible] = useState(tramiteId);
  const [idPrevio, setIdPrevio] = useState(tramiteId);
  if (idPrevio !== tramiteId) {
    setIdPrevio(tramiteId);
    if (tramiteId !== null) setIdVisible(tramiteId);
    setConfirmacion(null);
  }
  // Una confirmación solo existe mientras sigue teniendo sentido:
  // - "rechazar", mientras se puede rechazar: si el usuario edita con ella abierta, se retira y la
  //   barra vuelve con Rechazar desactivado y "Guarda antes de aprobar" (I2: un "Sí, rechazar" que
  //   no enviaría nada nunca se ofrece). Se prefirió a bloquear los campos: no añade un segundo
  //   modo de "desactivado" y la barra ya explica por qué no se puede rechazar.
  // - "cerrar", mientras hay cambios sin guardar y nada en curso.
  // - "quitar-marca", mientras se puede quitar la marca.
  // - "descartar-respuesta" (N8), mientras el panel de WhatsApp sigue abierto, con texto editado y
  //   sin una respuesta en vuelo.
  const confirmacionVisible: Confirmacion =
    confirmacion === "rechazar" && revision.puedeRechazar
      ? "rechazar"
      : confirmacion === "cerrar" && editable && sucio && enviando === null
        ? "cerrar"
        : confirmacion === "quitar-marca" && revision.puedeDesmarcarHechoEnOvz
          ? "quitar-marca"
          : confirmacion === "descartar-respuesta" && panelVisible && respuesta.editado && !respuesta.enviando
            ? "descartar-respuesta"
            : null;
  // I1: si deja de verse, se olvida (no queda armada para reaparecer y robar el foco después).
  if (confirmacion !== null && confirmacionVisible === null) setConfirmacion(null);

  const ids = {
    mensaje: useId(),
    explotacion: useId(),
    tipo: useId(),
    crotales: useId(),
    avisoSucio: useId(),
    pregunta: useId(),
    pistaMarcar: useId(),
    banda: useId(),
    pistaDeshacer: useId(),
    historial: useId(),
  };

  const tituloRef = useRef<HTMLHeadingElement | null>(null);
  const pieRef = useRef<HTMLDivElement | null>(null);
  const rechazarRef = useRef<HTMLButtonElement | null>(null);
  const guardarRef = useRef<HTMLButtonElement | null>(null);
  const aprobarRef = useRef<HTMLButtonElement | null>(null);
  const quitarMarcaRef = useRef<HTMLButtonElement | null>(null);
  const descartarRef = useRef<HTMLButtonElement | null>(null);
  const deshacerRef = useRef<HTMLButtonElement | null>(null);
  const primeraDeConfirmacionRef = useRef<HTMLButtonElement | null>(null);
  /** A dónde va el foco tras el próximo render (al cancelar una confirmación). */
  const focoPendiente = useRef<RefObject<HTMLElement | null> | null>(null);

  useEffect(() => {
    const destino = focoPendiente.current;
    if (!destino) return;
    focoPendiente.current = null;
    (destino.current ?? tituloRef.current)?.focus();
  });

  // Al abrir una confirmación, el foco va a su primera opción (la que no hace nada).
  useEffect(() => {
    if (confirmacionVisible) primeraDeConfirmacionRef.current?.focus();
  }, [confirmacionVisible]);

  // Al terminar una acción, el foco no se queda perdido en un botón que se desactivó o
  // desapareció (tras aprobar, la barra de acciones ya no existe). Tras Guardar o Rechazar vuelve a
  // ese botón si sigue activo; tras cualquier intento de Aprobar (y de marcar o quitar la marca, cuyo
  // botón desaparece al cambiar el estado) va SIEMPRE al título: si falló
  // (p. ej. un 409 que acaba de cambiar la resolución de los crotales), un segundo Enter no debe
  // aprobar lo que aún no se ha leído. El motivo lo anuncia el role="alert" del aviso.
  const enviandoPrevio = useRef(enviando);
  // El aviso de la última acción, leído al terminar un envío sin hacer depender el efecto de él (se
  // actualiza en un efecto anterior del mismo commit).
  const avisoActual = useRef(revision.aviso);
  useEffect(() => {
    avisoActual.current = revision.aviso;
  });
  useEffect(() => {
    const antes = enviandoPrevio.current;
    enviandoPrevio.current = enviando;
    if (antes === null || enviando !== null) return;
    const activo = document.activeElement;
    const perdido = !activo || activo === document.body || pieRef.current?.contains(activo);
    // SIN_TRAMITE: mientras se descarta o se deshace, el foco espera en el título. Al terminar bien (sin
    // aviso), pasa al botón que deshace lo hecho: «Deshacer» tras descartar, «Descartar» tras deshacer.
    // Si falló, se queda en el título (el motivo lo anuncia el aviso), como tras aprobar.
    if (antes === "descartar" || antes === "deshacer") {
      if (!perdido && activo !== tituloRef.current) return;
      const destino = avisoActual.current === null ? (antes === "descartar" ? deshacerRef : descartarRef).current : null;
      (destino && !destino.disabled ? destino : tituloRef.current)?.focus();
      return;
    }
    if (!perdido) return;
    const boton = antes === "guardar" ? guardarRef.current : antes === "rechazar" ? rechazarRef.current : null;
    (boton && !boton.disabled ? boton : tituloRef.current)?.focus();
  }, [enviando]);

  function pedirCierre() {
    // Una respuesta de WhatsApp escrita y sin mandar también pregunta antes (N8), en el propio panel.
    // Si el texto es el propuesto tal cual, no hay nada que perder y se cierra sin preguntar.
    if (panelVisible && respuesta.editado && !respuesta.enviando) {
      setConfirmacion("descartar-respuesta");
      return;
    }
    cerrarSinPreguntarPorRespuesta();
  }

  function cerrarSinPreguntarPorRespuesta() {
    // Cerrar con cambios sin guardar pregunta antes (en línea): descartarlos en silencio perdería
    // trabajo con un Esc o un clic fuera, y guardarlos solos cambiaría el trámite sin querer.
    // Con una petición en curso y cambios sin guardar no se hace nada (ni se deja la pregunta
    // armada para cuando termine: I1).
    if (editable && sucio && enviando !== null) return;
    if (editable && sucio) {
      setConfirmacion("cerrar");
      return;
    }
    onClose();
  }

  function cancelarConfirmacion() {
    // La de la respuesta devuelve el foco al texto desde el propio panel.
    focoPendiente.current =
      confirmacion === "descartar-respuesta"
        ? null
        : confirmacion === "rechazar"
          ? rechazarRef
          : confirmacion === "quitar-marca"
            ? quitarMarcaRef
            : tituloRef;
    setConfirmacion(null);
  }

  function descartarRespuestaYCerrar() {
    respuesta.cancelar();
    setConfirmacion(null);
    // Si además hay cambios sin guardar en el trámite, sigue su propia pregunta.
    cerrarSinPreguntarPorRespuesta();
  }

  function confirmarQuitarMarca() {
    // El botón que tenía el foco desaparece: el foco va al título mientras se quita la marca.
    focoPendiente.current = tituloRef;
    setConfirmacion(null);
    void revision.desmarcarHechoEnOvz();
  }

  function confirmarRechazo() {
    // El botón que tenía el foco desaparece: el foco va al título mientras se rechaza.
    focoPendiente.current = tituloRef;
    setConfirmacion(null);
    void revision.rechazar();
  }

  /** SIN_TRAMITE: un clic, sin confirmación (se deshace). El botón pasa a «Descartando…» desactivado:
   * el foco espera en el título hasta que termina (luego va a «Deshacer»). */
  function descartar() {
    focoPendiente.current = tituloRef;
    void revision.descartar();
  }

  function deshacerDescarte() {
    focoPendiente.current = tituloRef;
    void revision.deshacerDescarte();
  }

  function descartarYCerrar() {
    revision.descartarCambios();
    setConfirmacion(null);
    onClose();
  }

  return (
    <Dialog
      open={tramiteId !== null}
      onOpenChange={(abierto, detalles) => {
        if (abierto) return;
        // Esc o clic fuera con una confirmación abierta: se cancela la confirmación, no el modal.
        if (confirmacionVisible) {
          detalles.cancel();
          cancelarConfirmacion();
          return;
        }
        // Igual con el panel de respuesta por WhatsApp abierto: se cierra el panel, no el modal (con
        // una respuesta en vuelo no se hace nada). Con texto editado, antes se pregunta (N8).
        if (panelVisible) {
          detalles.cancel();
          if (respuesta.editado && !respuesta.enviando) setConfirmacion("descartar-respuesta");
          else respuesta.cancelar();
          return;
        }
        if (editable && sucio) detalles.cancel();
        pedirCierre();
      }}
    >
      <DialogContent
        showCloseButton={false}
        initialFocus={tituloRef}
        // Móvil: pantalla completa. Desde md: modal ancho con el borde superior fijo (no centrado
        // en vertical: al aparecer un aviso o "Guarda antes de aprobar" crece hacia abajo, sin
        // mover lo que se está editando), cuerpo con scroll y cabecera y pie fijos.
        className="top-0 left-0 flex h-dvh w-full max-w-none translate-x-0 translate-y-0 flex-col gap-0 overflow-hidden rounded-none p-0 sm:max-w-none md:top-[8dvh] md:left-1/2 md:h-auto md:max-h-[84dvh] md:max-w-4xl md:-translate-x-1/2 md:rounded-xl"
      >
        <header className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1 border-b py-3 pr-12 pl-4">
          <DialogTitle
            ref={tituloRef}
            tabIndex={-1}
            className="rounded-sm text-base font-bold tabular-nums outline-none"
          >
            Trámite #{idVisible}
          </DialogTitle>
          {detalle && <BadgeEstadoTramite estado={detalle.estado} motivoRechazo={detalle.motivoRechazo} />}
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto" data-cuerpo-revision>
          <div className="p-4">
            <AvisosRevision
              aviso={revision.aviso}
              extraccion={detalle && carga.estado === "listo" ? avisosExtraccion(detalle) : null}
              onDescartarAviso={revision.descartarAviso}
              recargaFallida={revision.recargaFallida}
              onReintentarRecarga={revision.reintentarRecarga}
            />
            <Cuerpo
              revision={revision}
              ids={ids}
              respuesta={respuesta}
              descarte={{
                visible: confirmacionVisible === "descartar-respuesta",
                onSeguirEscribiendo: cancelarConfirmacion,
                onDescartarYCerrar: descartarRespuestaYCerrar,
              }}
              deshacer={{ ref: deshacerRef, onDeshacer: deshacerDescarte }}
            />
          </div>
        </div>

        {editable && carga.estado === "listo" && (
          <div
            ref={pieRef}
            className="border-t bg-muted/50 px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]"
          >
            {confirmacionVisible === "rechazar" ? (
              <BarraConfirmacion
                idPregunta={ids.pregunta}
                pregunta={`¿Rechazar el trámite #${idVisible}? No se puede deshacer.`}
              >
                <Button ref={primeraDeConfirmacionRef} variant="outline" onClick={cancelarConfirmacion}>
                  Cancelar
                </Button>
                <Button variant="destructive" onClick={confirmarRechazo}>
                  Sí, rechazar
                </Button>
              </BarraConfirmacion>
            ) : confirmacionVisible === "cerrar" ? (
              <BarraConfirmacion idPregunta={ids.pregunta} pregunta="Tienes cambios sin guardar.">
                <Button ref={primeraDeConfirmacionRef} variant="outline" onClick={cancelarConfirmacion}>
                  Seguir editando
                </Button>
                <Button variant="destructive" onClick={descartarYCerrar}>
                  Descartar y cerrar
                </Button>
              </BarraConfirmacion>
            ) : (
              <BarraAcciones
                revision={revision}
                idAvisoSucio={ids.avisoSucio}
                refs={{ rechazar: rechazarRef, guardar: guardarRef, aprobar: aprobarRef, descartar: descartarRef }}
                onRechazar={() => setConfirmacion("rechazar")}
                onDescartar={descartar}
                onVistaPrevia={() => detalle && abrirFichaOvz(detalle.id)}
              />
            )}
          </div>
        )}

        {/* Ficha OVZ (T5): un trámite aprobado se pasa a OVZ a mano desde su ficha, en otra ventana.
            Hecho en OVZ: cuando la persona lo ha terminado allí, lo marca aquí (o quita la marca). */}
        {!editable && carga.estado === "listo" && detalle?.estado === "APROBADO" && (
          <div
            ref={pieRef}
            className="flex flex-col gap-2 border-t bg-muted/50 px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]"
          >
            <p id={ids.pistaMarcar} className="text-sm text-muted-foreground sm:text-right">
              {PISTA_MARCAR_HECHO_EN_OVZ}
            </p>
            <div className="flex flex-wrap items-center gap-2">
              <BotonAbrirFicha onClick={() => abrirFichaOvz(detalle.id)} />
              <Button
                className="ml-auto"
                onClick={() => void revision.marcarHechoEnOvz()}
                disabled={!revision.puedeMarcarHechoEnOvz}
                aria-describedby={ids.pistaMarcar}
              >
                {enviando === "marcar" ? TEXTO_EN_CURSO.marcar : TEXTO_BOTON_MARCAR}
              </Button>
            </div>
          </div>
        )}

        {!editable && carga.estado === "listo" && detalle?.estado === "HECHO_EN_OVZ" && (
          <div
            ref={pieRef}
            className="border-t bg-muted/50 px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]"
          >
            {confirmacionVisible === "quitar-marca" ? (
              <BarraConfirmacion idPregunta={ids.pregunta} pregunta={PREGUNTA_QUITAR_MARCA}>
                <Button ref={primeraDeConfirmacionRef} variant="outline" onClick={cancelarConfirmacion}>
                  Cancelar
                </Button>
                <Button variant="destructive" onClick={confirmarQuitarMarca}>
                  {TEXTO_CONFIRMAR_QUITAR_MARCA}
                </Button>
              </BarraConfirmacion>
            ) : (
              <div className="flex flex-wrap items-center gap-2">
                <Button
                  ref={quitarMarcaRef}
                  variant="outline"
                  onClick={() => setConfirmacion("quitar-marca")}
                  disabled={!revision.puedeDesmarcarHechoEnOvz}
                >
                  {enviando === "desmarcar" ? TEXTO_EN_CURSO.desmarcar : TEXTO_BOTON_QUITAR_MARCA}
                </Button>
                <BotonAbrirFicha className="ml-auto" onClick={() => abrirFichaOvz(detalle.id)} />
              </div>
            )}
          </div>
        )}

        {/* Al final del DOM (como el cierre de DialogContent) para que el orden de tabulación sea
            mensaje → campos → crotales → acciones; visualmente arriba a la derecha. */}
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="Cerrar"
          onClick={pedirCierre}
          className="absolute top-2.5 right-2.5"
        >
          <XIcon aria-hidden />
        </Button>
      </DialogContent>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------------------------

interface IdsRevision {
  mensaje: string;
  explotacion: string;
  tipo: string;
  crotales: string;
  banda: string;
  pistaDeshacer: string;
  historial: string;
}

/** «Deshacer» de la banda de un descartado (SIN_TRAMITE). */
interface DeshacerDescarte {
  ref: RefObject<HTMLButtonElement | null>;
  onDeshacer: () => void;
}

function Cuerpo({
  revision,
  ids,
  respuesta,
  descarte,
  deshacer,
}: {
  revision: RevisionTramite;
  ids: IdsRevision;
  respuesta: RespuestaWhatsappPanel;
  descarte: DescarteRespuesta;
  deshacer: DeshacerDescarte;
}) {
  const { carga, detalle } = revision;
  switch (carga.estado) {
    case "inactivo":
      return null;
    case "cargando":
      return <EsqueletoRevision />;
    case "error":
      return (
        <Alert variant="destructive">
          <CircleAlertIcon />
          <AlertTitle>No se ha podido cargar el trámite</AlertTitle>
          <AlertDescription>
            <p>{carga.mensaje}</p>
            <Button variant="outline" size="sm" onClick={revision.reintentarCarga} className="mt-2">
              Reintentar
            </Button>
          </AlertDescription>
        </Alert>
      );
    case "no-encontrado":
      return (
        <Alert>
          <AlertTitle>Trámite no disponible</AlertTitle>
          <AlertDescription>
            <p>{carga.mensaje}</p>
          </AlertDescription>
        </Alert>
      );
    case "listo":
      if (!detalle) return null;
      return (
        <>
          {detalle.estado === "HECHO_EN_OVZ" && <FranjaHechoEnOvz detalle={detalle} />}
          {esDescartado(detalle) && (
            <BandaDescartado revision={revision} ids={ids} deshacer={deshacer} />
          )}
          <div
            data-columnas-revision
            className="grid grid-cols-1 gap-6 md:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]"
          >
            {/* Izquierda (arriba en móvil): lo que escribió el ganadero. En escritorio se queda a la
                vista mientras se recorren los crotales, para cotejar. */}
            {/* B2 reducido: con la sección de WhatsApp debajo del mensaje, la columna deja de ser fija
                (podría ser más alta que el modal y su final quedaría fuera de la vista). Sin ella, el
                mensaje sigue siendo la columna, fija en escritorio para cotejar. */}
            {detalle.whatsapp ? (
              <div data-columna-mensaje className="flex min-w-0 flex-col gap-6 md:self-start">
                <MensajeOriginal detalle={detalle} idTitulo={ids.mensaje} />
                <SeccionWhatsapp whatsapp={detalle.whatsapp} panel={respuesta} descarte={descarte} />
              </div>
            ) : (
              <MensajeOriginal
                detalle={detalle}
                idTitulo={ids.mensaje}
                className="md:sticky md:top-0 md:self-start"
              />
            )}
  
            {/* Derecha: lo que Ganera entendió. */}
            <section aria-label="Datos del trámite" className="flex min-w-0 flex-col gap-5">
              {detalle.motivoError && (
                <Alert variant="destructive">
                  <CircleAlertIcon />
                  <AlertTitle>Motivo del error</AlertTitle>
                  <AlertDescription>{detalle.motivoError}</AlertDescription>
                </Alert>
              )}
              {revision.editable ? (
                <DatosEditables revision={revision} ids={ids} />
              ) : (
                <DatosSoloLectura revision={revision} />
              )}
              <HistorialDescarte detalle={detalle} idTitulo={ids.historial} />
            </section>
          </div>
        </>
      );
  }
}

/** Lo que escribió el ganadero (columna izquierda, arriba en móvil). */
function MensajeOriginal({
  detalle,
  idTitulo,
  className,
}: {
  detalle: TramiteDetalle;
  idTitulo: string;
  className?: string;
}) {
  return (
    <section aria-labelledby={idTitulo} className={cn("flex min-w-0 flex-col gap-2", className)}>
      <h3 id={idTitulo} className="text-sm font-bold text-muted-foreground">
        Mensaje de WhatsApp
      </h3>
      {detalle.mensajeOriginal ? (
        <blockquote className="rounded-lg bg-muted px-3 py-2.5 whitespace-pre-wrap wrap-anywhere">
          {detalle.mensajeOriginal}
        </blockquote>
      ) : (
        <p className="text-muted-foreground">Aún no hay mensaje: este trámite no llegó por WhatsApp.</p>
      )}
    </section>
  );
}

/** Hecho en OVZ: quién y cuándo lo marcó, en el par de éxito (nunca rojo), encima de los datos. */
function FranjaHechoEnOvz({ detalle }: { detalle: TramiteDetalle }) {
  return (
    <p
      data-franja-hecho-en-ovz
      className="mb-4 flex items-start gap-2 rounded-lg bg-success px-3 py-2 text-sm text-success-foreground"
    >
      <CircleCheckIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      <span className="font-medium">{textoHechoEnOvz(detalle)}</span>
    </p>
  );
}

/** SIN_TRAMITE: banda de un descartado, en el par neutro (no es un error ni un éxito), con quién y
 * cuándo y «Deshacer» de un clic (sin caducidad). Su descripción accesible es la banda y la pista. */
function BandaDescartado({
  revision,
  ids,
  deshacer,
}: {
  revision: RevisionTramite;
  ids: IdsRevision;
  deshacer: DeshacerDescarte;
}) {
  const { detalle, enviando } = revision;
  if (!detalle) return null;
  return (
    <div
      data-banda-descartado
      className="mb-4 flex flex-col gap-2 rounded-lg bg-muted px-3 py-2 text-sm sm:flex-row sm:items-center sm:gap-4"
    >
      <p id={ids.banda} className="min-w-0 font-medium">
        {textoBandaDescartado(detalle.historialDescarte)}
      </p>
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 sm:ml-auto sm:shrink-0">
        <Button
          ref={deshacer.ref}
          variant="outline"
          size="sm"
          onClick={deshacer.onDeshacer}
          disabled={!revision.puedeDeshacerDescarte}
          aria-describedby={`${ids.banda} ${ids.pistaDeshacer}`}
        >
          {enviando === "deshacer" ? TEXTO_EN_CURSO.deshacer : TEXTO_BOTON_DESHACER}
        </Button>
        <p id={ids.pistaDeshacer} className="text-muted-foreground">
          {PISTA_DESHACER}
        </p>
      </div>
    </div>
  );
}

/** SIN_TRAMITE (A6): quién descartó y quién lo deshizo, y cuándo (hora de Madrid). En cualquier estado,
 * solo si hay eventos. */
function HistorialDescarte({ detalle, idTitulo }: { detalle: TramiteDetalle; idTitulo: string }) {
  const lineas = lineasHistorialDescarte(detalle.historialDescarte);
  if (lineas.length === 0) return null;
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <h4 id={idTitulo} className={CLASE_ETIQUETA}>
        Historial
      </h4>
      <ul aria-labelledby={idTitulo} className="flex flex-col gap-1 text-sm text-muted-foreground">
        {lineas.map((linea, indice) => (
          <li key={indice}>{linea}</li>
        ))}
      </ul>
    </div>
  );
}

/** «Abrir ficha para OVZ» (ficha OVZ, T5): en APROBADO y en HECHO_EN_OVZ. */
function BotonAbrirFicha({ onClick, className }: { onClick: () => void; className?: string }) {
  return (
    <Button
      variant="outline"
      className={className}
      aria-label="Abrir ficha para OVZ (se abre en otra ventana)"
      onClick={onClick}
    >
      Abrir ficha para OVZ
      <ExternalLinkIcon aria-hidden />
    </Button>
  );
}

const CLASE_ETIQUETA = "text-sm font-medium text-muted-foreground";

function Campo({
  etiqueta,
  sinGuardar = false,
  children,
}: {
  etiqueta: ReactNode;
  sinGuardar?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <div className="flex min-h-5 items-center gap-2">
        {etiqueta}
        {sinGuardar && <BadgeSinGuardar />}
      </div>
      {children}
    </div>
  );
}

function DatosEditables({ revision, ids }: { revision: RevisionTramite; ids: IdsRevision }) {
  const { detalle, formulario, enviando } = revision;
  if (!detalle) return null;
  const ocupado = enviando !== null;
  const explotacionCambiada = formulario.explotacionId !== detalle.explotacionId;
  const idEtiquetaTipo = `${ids.tipo}-etiqueta`;

  return (
    <>
      <Campo
        etiqueta={
          <label htmlFor={ids.explotacion} className={CLASE_ETIQUETA}>
            Explotación
          </label>
        }
        sinGuardar={explotacionCambiada}
      >
        <CampoExplotacion
          idCampo={ids.explotacion}
          explotacionId={formulario.explotacionId}
          asignada={revision.explotacionAsignada}
          onCambiar={(id) => revision.cambiarExplotacion(id)}
          deshabilitado={ocupado}
        />
      </Campo>

      <Campo
        etiqueta={
          <span id={idEtiquetaTipo} className={CLASE_ETIQUETA}>
            Tipo de trámite
          </span>
        }
        sinGuardar={formulario.tipoTramite !== detalle.tipoTramite}
      >
        <Select
          value={formulario.tipoTramite}
          onValueChange={(valor) => {
            // H5: sin opción "ninguno"; un null nunca llega a vaciar el tipo.
            if (valor) revision.cambiarTipoTramite(valor as TipoTramite);
          }}
          disabled={ocupado}
        >
          <SelectTrigger id={ids.tipo} aria-labelledby={idEtiquetaTipo} className="w-full sm:w-56">
            <SelectValue>
              {(valor: string | null) => (valor ? etiquetaTipoTramite(valor) : TEXTO_SIN_TIPO)}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            {(Object.keys(TIPOS_TRAMITE) as TipoTramite[]).map((tipo) => (
              <SelectItem key={tipo} value={tipo}>
                {TIPOS_TRAMITE[tipo]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </Campo>

      <Campo
        etiqueta={
          <h4 id={ids.crotales} className={CLASE_ETIQUETA}>
            Crotales
          </h4>
        }
      >
        <ListaCrotalesEditable
          valores={formulario.crotales}
          guardados={detalle.crotales}
          explotacionCambiada={explotacionCambiada}
          deshabilitado={ocupado}
          onCambiar={(indice, valor) => revision.cambiarCrotal(indice, valor)}
          onQuitar={(indice) => revision.quitarCrotal(indice)}
          onAnadir={() => revision.anadirCrotal()}
        />
      </Campo>
    </>
  );
}

/** Mismo orden y sitio que el formulario, con texto: nada editable ni ninguna acción. Se pinta
 * desde lo GUARDADO (`detalle`), nunca desde el formulario (N1 de la revisión 9a). */
function DatosSoloLectura({ revision }: { revision: RevisionTramite }) {
  const { detalle } = revision;
  if (!detalle) return null;
  return (
    <dl className="flex flex-col gap-5">
      <div className="flex min-w-0 flex-col gap-1.5">
        <dt className={CLASE_ETIQUETA}>Explotación</dt>
        <dd>
          <TextoExplotacion asignada={revision.explotacionAsignada} />
        </dd>
      </div>
      <div className="flex min-w-0 flex-col gap-1.5">
        <dt className={CLASE_ETIQUETA}>Tipo de trámite</dt>
        <dd>
          {detalle.tipoTramite ? (
            etiquetaTipoTramite(detalle.tipoTramite)
          ) : (
            <span className="text-muted-foreground">{TEXTO_SIN_TIPO}</span>
          )}
        </dd>
      </div>
      <div className="flex min-w-0 flex-col gap-1.5">
        <dt className={CLASE_ETIQUETA}>Crotales</dt>
        <dd>
          <CrotalesSoloLectura crotales={detalle.crotales} />
        </dd>
      </div>
    </dl>
  );
}

function EsqueletoRevision() {
  return (
    <div className="grid grid-cols-1 gap-6 md:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
      <span role="status" className="sr-only">
        Cargando trámite…
      </span>
      <div aria-hidden className="flex flex-col gap-2">
        <span className="h-4 w-36 rounded-sm bg-muted motion-safe:animate-pulse" />
        <span className="h-24 rounded-lg bg-muted motion-safe:animate-pulse" />
      </div>
      <div aria-hidden className="flex flex-col gap-5">
        {[0, 1, 2].map((fila) => (
          <div key={fila} className="flex flex-col gap-1.5">
            <span className="h-4 w-28 rounded-sm bg-muted motion-safe:animate-pulse" />
            <span className="h-8 rounded-lg bg-muted motion-safe:animate-pulse" />
          </div>
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------------------------

interface RefsAcciones {
  rechazar: RefObject<HTMLButtonElement | null>;
  guardar: RefObject<HTMLButtonElement | null>;
  aprobar: RefObject<HTMLButtonElement | null>;
  descartar: RefObject<HTMLButtonElement | null>;
}

function BarraAcciones({
  revision,
  idAvisoSucio,
  refs,
  onRechazar,
  onDescartar,
  onVistaPrevia,
}: {
  revision: RevisionTramite;
  idAvisoSucio: string;
  refs: RefsAcciones;
  onRechazar: () => void;
  /** SIN_TRAMITE: un clic, sin confirmación. */
  onDescartar: () => void;
  /** Ficha OVZ (T5, D4): la ficha del trámite pendiente, sin botones de copiar. */
  onVistaPrevia: () => void;
}) {
  const { enviando, avisoCambiosSinGuardar } = revision;
  const descritoPor = avisoCambiosSinGuardar ? idAvisoSucio : undefined;
  const idPistaDescartar = useId();
  // SIN_TRAMITE: «Descartar» desactivado por cambios sin guardar lleva su propia pista.
  const pistaDescartar =
    avisoCambiosSinGuardar && revision.detalle?.descartable === true ? PISTA_DESCARTAR_SIN_GUARDAR : null;
  return (
    <div className="flex flex-col gap-2">
      {avisoCambiosSinGuardar && (
        <div className="flex flex-wrap items-center justify-end gap-x-3 gap-y-1 text-sm">
          {/* Decisión 24: texto exacto. Aprobar aprueba lo guardado, no lo que hay en pantalla. */}
          <p id={idAvisoSucio} className="text-muted-foreground">
            {avisoCambiosSinGuardar}
          </p>
          {pistaDescartar && (
            <p id={idPistaDescartar} className="text-muted-foreground">
              {pistaDescartar}
            </p>
          )}
          <Button variant="ghost" size="sm" onClick={revision.descartarCambios} disabled={enviando !== null}>
            Descartar cambios
          </Button>
        </div>
      )}
      <div className="flex flex-wrap items-center gap-2">
        <Button
          ref={refs.rechazar}
          variant="outline"
          onClick={onRechazar}
          disabled={!revision.puedeRechazar}
          aria-describedby={descritoPor}
        >
          {enviando === "rechazar" ? TEXTO_EN_CURSO.rechazar : "Rechazar"}
        </Button>
        {/* SIN_TRAMITE: solo si el BACKEND lo marca descartable (el frontend no recalcula la regla).
            Con cambios sin guardar se desactiva, como Aprobar: descartaría lo guardado. */}
        {revision.detalle?.descartable === true && (
          <Button
            ref={refs.descartar}
            variant="outline"
            onClick={onDescartar}
            disabled={!revision.puedeDescartar}
            aria-describedby={pistaDescartar ? idPistaDescartar : undefined}
          >
            {enviando === "descartar" ? TEXTO_EN_CURSO.descartar : TEXTO_BOTON_DESCARTAR}
          </Button>
        )}
        {/* Con cambios sin guardar se desactiva, como Aprobar: la ficha enseña lo guardado. */}
        <Button
          variant="ghost"
          onClick={onVistaPrevia}
          disabled={revision.sucio || enviando !== null}
          aria-describedby={descritoPor}
          aria-label="Vista previa de la ficha (se abre en otra ventana)"
        >
          Vista previa de la ficha
          <ExternalLinkIcon aria-hidden />
        </Button>
        <div className="ml-auto flex flex-wrap items-center gap-2">
          <Button
            ref={refs.guardar}
            variant="outline"
            onClick={() => void revision.guardar()}
            disabled={!revision.puedeGuardar}
          >
            {enviando === "guardar" ? TEXTO_EN_CURSO.guardar : "Guardar"}
          </Button>
          <Button
            ref={refs.aprobar}
            onClick={() => void revision.aprobar()}
            disabled={!revision.puedeAprobar}
            aria-describedby={descritoPor}
          >
            {enviando === "aprobar" ? TEXTO_EN_CURSO.aprobar : "Aprobar"}
          </Button>
        </div>
      </div>
    </div>
  );
}

function BarraConfirmacion({
  idPregunta,
  pregunta,
  children,
}: {
  idPregunta: string;
  pregunta: string;
  children: ReactNode;
}) {
  return (
    <div role="group" aria-labelledby={idPregunta} className="flex flex-wrap items-center gap-2">
      <p id={idPregunta} className="mr-auto text-sm font-medium">
        {pregunta}
      </p>
      <div className="flex flex-wrap items-center gap-2">{children}</div>
    </div>
  );
}
