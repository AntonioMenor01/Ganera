import { CircleAlertIcon, CircleCheckIcon, TriangleAlertIcon, XIcon } from "lucide-react";
import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { AvisosExtraccion as AvisosExtraccionDatos } from "./etiquetas";
import type { AccionRevision, AvisoRevision } from "./useRevisionTramite";

/**
 * Avisos del modal, entre la cabecera y las columnas (contrato de 9b). Los errores son `alert`
 * (se anuncian al aparecer); el éxito va en una región `status` que existe siempre, para que el
 * lector de pantalla lo anuncie también (una región viva insertada ya con texto puede no leerse).
 * Todos persisten hasta la siguiente acción o hasta que se cierran: un 409 nunca desaparece solo.
 *
 * Debajo, los avisos de extracción (B1, D8): fijos, sin botón de cerrar, porque describen los datos
 * del trámite y no el resultado de una acción. Son texto estático, sin `alert` ni `status`: están ya
 * al abrir el modal (una región viva insertada llena puede no leerse, y un `alert` interrumpiría la
 * lectura del título, que es donde está el foco); como van justo tras el título en el orden de
 * lectura, el lector de pantalla los lee al recorrer el modal. Tampoco `role="note"`: los lectores
 * apenas lo anuncian y no aportaría nada al texto, que ya dice qué pasa.
 */

const TITULO_ERROR: Record<AccionRevision, string> = {
  guardar: "No se han guardado los cambios",
  aprobar: "No se ha aprobado el trámite",
  rechazar: "No se ha rechazado el trámite",
};

function BotonCerrarAviso({ onClick }: { onClick: () => void }) {
  return (
    <AlertAction>
      <Button variant="ghost" size="icon-sm" aria-label="Cerrar aviso" onClick={onClick}>
        <XIcon aria-hidden />
      </Button>
    </AlertAction>
  );
}

interface AvisosRevisionProps {
  aviso: AvisoRevision | null;
  /** Avisos de extracción del trámite cargado (`avisosExtraccion`), o null si no hay detalle. */
  extraccion?: AvisosExtraccionDatos | null;
  onDescartarAviso: () => void;
  recargaFallida: string | null;
  onReintentarRecarga: () => void;
}

export function AvisosRevision({
  aviso,
  extraccion = null,
  onDescartarAviso,
  recargaFallida,
  onReintentarRecarga,
}: AvisosRevisionProps) {
  const exito = aviso?.tipo === "exito" ? aviso : null;
  const error = aviso && aviso.tipo !== "exito" ? aviso : null;
  const hayExtraccion = Boolean(extraccion?.extraccion || extraccion?.descartados);

  return (
    <div className={cn("flex flex-col gap-2", (aviso || recargaFallida || hayExtraccion) && "mb-4")}>
      {error && (
        <Alert variant={error.tipo === "estado-cambiado" ? "default" : "destructive"} data-aviso={error.tipo}>
          {/* Solo el error lleva icono; el aviso neutro de cambio de estado, no. */}
          {error.tipo !== "estado-cambiado" && <CircleAlertIcon />}
          {/* m4: si consta otro estado, el título no afirma que la acción fallara: solo que cambió. */}
          <AlertTitle>
            {error.tipo === "estado-cambiado" ? "El trámite ha cambiado de estado" : TITULO_ERROR[error.accion]}
          </AlertTitle>
          <AlertDescription>
            {/* 409/400/403: el motivo del backend, tal cual (decisión 10). El 403 de aprobar ya no
                lleva enlace: no hay página de pago en la app, el motivo pide contactar con Ganera. */}
            <p>{error.mensaje}</p>
          </AlertDescription>
          <BotonCerrarAviso onClick={onDescartarAviso} />
        </Alert>
      )}

      <div role="status" className="empty:hidden">
        {exito && (
          <div className="relative flex items-start gap-2 rounded-lg bg-success py-2 pr-12 pl-2.5 text-sm text-success-foreground">
            <CircleCheckIcon aria-hidden className="mt-0.5 size-4 shrink-0" />
            <p>{exito.mensaje}</p>
            <div className="absolute top-1 right-1">
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="Cerrar aviso"
                onClick={onDescartarAviso}
                className="text-success-foreground hover:bg-success-foreground/10 hover:text-success-foreground"
              >
                <XIcon aria-hidden />
              </Button>
            </div>
          </div>
        )}
      </div>

      {recargaFallida && (
        <p role="alert" className="flex flex-wrap items-center gap-x-2 text-sm text-muted-foreground">
          <span>{recargaFallida}</span>
          <Button variant="link" size="sm" className="h-6 px-0" onClick={onReintentarRecarga}>
            Reintentar
          </Button>
        </p>
      )}

      {hayExtraccion && extraccion && (
        <div data-avisos-extraccion className="flex flex-col gap-2">
          {extraccion.extraccion && (
            <AvisoExtraccion texto={extraccion.extraccion.modal} tono={extraccion.extraccion.tono} />
          )}
          {extraccion.descartados && <AvisoExtraccion texto={extraccion.descartados.modal} tono="aviso" />}
        </div>
      )}
    </div>
  );
}

/** `aviso`: el par ámbar de aviso (trabajo pendiente, no un error del usuario: nunca rojo), con
 * icono decorativo, como el bloque de éxito de arriba. `neutro`: una línea en Gris Texto, sin
 * recuadro ni icono (con borde y del alto de un campo se leía como un input vacío). */
function AvisoExtraccion({ texto, tono }: { texto: string; tono: "aviso" | "neutro" }) {
  if (tono === "neutro") {
    return (
      <p data-aviso-extraccion className="text-sm text-muted-foreground">
        {texto}
      </p>
    );
  }
  return (
    <div
      data-aviso-extraccion
      className="flex items-start gap-2 rounded-lg bg-warning px-2.5 py-2 text-sm text-warning-foreground"
    >
      <TriangleAlertIcon aria-hidden className="mt-0.5 size-4 shrink-0" />
      <p>{texto}</p>
    </div>
  );
}
