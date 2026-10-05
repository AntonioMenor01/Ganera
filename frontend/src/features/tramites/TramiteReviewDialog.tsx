import { useEffect, useId, useRef, useState, type ReactNode, type RefObject } from "react";
import { CircleAlertIcon, XIcon } from "lucide-react";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { avisosExtraccion, etiquetaTipoTramite, TIPOS_TRAMITE, type TipoTramite } from "./etiquetas";
import { BadgeEstadoTramite } from "./BadgesTramite";
import { AvisosRevision } from "./AvisosRevision";
import { CampoExplotacion, TextoExplotacion } from "./CampoExplotacion";
import { BadgeSinGuardar, CrotalesSoloLectura, ListaCrotalesEditable } from "./ListaCrotales";
import { useRevisionTramite, type AccionRevision, type RevisionTramite } from "./useRevisionTramite";

/**
 * Modal de revisión de un trámite (Task 9b; contrato en
 * .impeccable/surfaces/features-tramites-tramitereviewdialog-tsx-930a9a22.md).
 *
 * Una mesa de cotejo: a la izquierda el mensaje de WhatsApp, a la derecha lo que Ganera entendió.
 * Toda la lógica (qué se envía, cuándo, qué pasa con cada error) vive en useRevisionTramite; aquí
 * solo se pinta. "Aprobar" solo cambia el estado del trámite en Ganera: nada de lo que se ve aquí
 * sugiere que ocurra algo en OVZ.net (Prompt 3c).
 */

interface TramiteReviewDialogProps {
  tramiteId: number | null;
  onClose: () => void;
  onCambiado: () => void;
}

/** Confirmación en línea en la barra de acciones (decisión 31): nunca un segundo modal. */
type Confirmacion = "rechazar" | "cerrar" | null;

const TEXTO_EN_CURSO: Record<AccionRevision, string> = {
  guardar: "Guardando…",
  aprobar: "Aprobando…",
  rechazar: "Rechazando…",
};

const TEXTO_SIN_TIPO = "Sin determinar todavía";

export function TramiteReviewDialog({
  tramiteId,
  onClose,
  onCambiado,
}: TramiteReviewDialogProps) {
  const revision = useRevisionTramite(tramiteId, { onCambiado });
  const { carga, detalle, editable, sucio, enviando } = revision;

  const [confirmacion, setConfirmacion] = useState<Confirmacion>(null);
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
  const confirmacionVisible: Confirmacion =
    confirmacion === "rechazar" && revision.puedeRechazar
      ? "rechazar"
      : confirmacion === "cerrar" && editable && sucio && enviando === null
        ? "cerrar"
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
  };

  const tituloRef = useRef<HTMLHeadingElement | null>(null);
  const pieRef = useRef<HTMLDivElement | null>(null);
  const rechazarRef = useRef<HTMLButtonElement | null>(null);
  const guardarRef = useRef<HTMLButtonElement | null>(null);
  const aprobarRef = useRef<HTMLButtonElement | null>(null);
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
  // ese botón si sigue activo; tras cualquier intento de Aprobar va SIEMPRE al título: si falló
  // (p. ej. un 409 que acaba de cambiar la resolución de los crotales), un segundo Enter no debe
  // aprobar lo que aún no se ha leído. El motivo lo anuncia el role="alert" del aviso.
  const enviandoPrevio = useRef(enviando);
  useEffect(() => {
    const antes = enviandoPrevio.current;
    enviandoPrevio.current = enviando;
    if (antes === null || enviando !== null) return;
    const activo = document.activeElement;
    const perdido = !activo || activo === document.body || pieRef.current?.contains(activo);
    if (!perdido) return;
    const boton = antes === "aprobar" ? null : { guardar: guardarRef, rechazar: rechazarRef }[antes].current;
    (boton && !boton.disabled ? boton : tituloRef.current)?.focus();
  }, [enviando]);

  function pedirCierre() {
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
    focoPendiente.current = confirmacion === "rechazar" ? rechazarRef : tituloRef;
    setConfirmacion(null);
  }

  function confirmarRechazo() {
    // El botón que tenía el foco desaparece: el foco va al título mientras se rechaza.
    focoPendiente.current = tituloRef;
    setConfirmacion(null);
    void revision.rechazar();
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
        className="top-0 left-0 flex h-dvh w-full max-w-none translate-x-0 translate-y-0 flex-col gap-0 overflow-hidden rounded-none p-0 ring-0 sm:max-w-none md:top-[8dvh] md:left-1/2 md:h-auto md:max-h-[84dvh] md:max-w-4xl md:-translate-x-1/2 md:rounded-xl md:ring-1"
      >
        <header className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1 border-b py-3 pr-12 pl-4">
          <DialogTitle
            ref={tituloRef}
            tabIndex={-1}
            className="rounded-sm text-base font-semibold tabular-nums outline-none"
          >
            Trámite #{idVisible}
          </DialogTitle>
          {detalle && <BadgeEstadoTramite estado={detalle.estado} />}
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
            <Cuerpo revision={revision} ids={ids} />
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
                refs={{ rechazar: rechazarRef, guardar: guardarRef, aprobar: aprobarRef }}
                onRechazar={() => setConfirmacion("rechazar")}
              />
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
}

function Cuerpo({ revision, ids }: { revision: RevisionTramite; ids: IdsRevision }) {
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
        <div
          data-columnas-revision
          className="grid grid-cols-1 gap-6 md:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]"
        >
          {/* Izquierda (arriba en móvil): lo que escribió el ganadero. En escritorio se queda a la
              vista mientras se recorren los crotales, para cotejar. */}
          <section aria-labelledby={ids.mensaje} className="flex min-w-0 flex-col gap-2 md:sticky md:top-0 md:self-start">
            <h3 id={ids.mensaje} className="text-sm font-semibold text-muted-foreground">
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
          </section>
        </div>
      );
  }
}

const CLASE_ETIQUETA = "text-sm font-semibold text-muted-foreground";

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
}

function BarraAcciones({
  revision,
  idAvisoSucio,
  refs,
  onRechazar,
}: {
  revision: RevisionTramite;
  idAvisoSucio: string;
  refs: RefsAcciones;
  onRechazar: () => void;
}) {
  const { enviando, avisoCambiosSinGuardar } = revision;
  const descritoPor = avisoCambiosSinGuardar ? idAvisoSucio : undefined;
  return (
    <div className="flex flex-col gap-2">
      {avisoCambiosSinGuardar && (
        <div className="flex flex-wrap items-center justify-end gap-x-3 gap-y-1 text-sm">
          {/* Decisión 24: texto exacto. Aprobar aprueba lo guardado, no lo que hay en pantalla. */}
          <p id={idAvisoSucio} className="text-muted-foreground">
            {avisoCambiosSinGuardar}
          </p>
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
      <p id={idPregunta} className="mr-auto text-sm font-semibold">
        {pregunta}
      </p>
      <div className="flex flex-wrap items-center gap-2">{children}</div>
    </div>
  );
}
