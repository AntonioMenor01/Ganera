import { Link } from "react-router-dom";
import { CircleCheckIcon, XIcon } from "lucide-react";
import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { CLASE_ENLACE } from "@/shared/ui/enlace";
import type { AccionRevision, AvisoRevision } from "./useRevisionTramite";

/**
 * Avisos del modal, entre la cabecera y las columnas (contrato de 9b). Los errores son `alert`
 * (se anuncian al aparecer); el éxito va en una región `status` que existe siempre, para que el
 * lector de pantalla lo anuncie también (una región viva insertada ya con texto puede no leerse).
 * Todos persisten hasta la siguiente acción o hasta que se cierran: un 409 nunca desaparece solo.
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
  onDescartarAviso: () => void;
  recargaFallida: string | null;
  onReintentarRecarga: () => void;
}

export function AvisosRevision({
  aviso,
  onDescartarAviso,
  recargaFallida,
  onReintentarRecarga,
}: AvisosRevisionProps) {
  const exito = aviso?.tipo === "exito" ? aviso : null;
  const error = aviso && aviso.tipo !== "exito" ? aviso : null;

  return (
    <div className={cn("flex flex-col gap-2", (aviso || recargaFallida) && "mb-4")}>
      {error && (
        <Alert variant={error.tipo === "estado-cambiado" ? "default" : "destructive"} data-aviso={error.tipo}>
          {/* m4: si consta otro estado, el título no afirma que la acción fallara: solo que cambió. */}
          <AlertTitle>
            {error.tipo === "estado-cambiado" ? "El trámite ha cambiado de estado" : TITULO_ERROR[error.accion]}
          </AlertTitle>
          <AlertDescription>
            {/* 409/400: el motivo del backend, tal cual (decisión 10). */}
            <p>{error.mensaje}</p>
            {error.tipo === "prohibido" && (
              <p>
                <Link to="/facturacion" className={CLASE_ENLACE}>
                  Ir a Facturación
                </Link>
              </p>
            )}
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
    </div>
  );
}
