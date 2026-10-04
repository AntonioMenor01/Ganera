import { TriangleAlertIcon } from "lucide-react";
import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { mensajeDeError } from "@/shared/api/errores";
import type { SuscripcionEstadoHook } from "./useSuscripcionEstado";

const MENSAJES_BLOQUEANTES: Record<string, string> = {
  TRIAL_EXPIRADO_SIN_PAGO:
    "El periodo de prueba ha terminado y no hay ningún pago activo, así que no se pueden aprobar trámites. Ponte en contacto con Ganera para regularizar la suscripción.",
  SUSPENDIDA:
    "La suscripción está suspendida y no se pueden aprobar trámites. Ponte en contacto con Ganera para regularizarla.",
};

const MENSAJES_AVISO: Record<string, string> = {
  IMPAGO_GRACIA:
    "El último pago ha fallado. Sigues teniendo acceso completo durante el periodo de gracia; ponte en contacto con Ganera para regularizarlo cuanto antes.",
};

const MENSAJE_SIN_SUSCRIPCION =
  "Todavía no tienes una suscripción activa. Ponte en contacto con Ganera para activarla.";

const TITULO_BLOQUEANTE = "No puedes aprobar trámites ahora mismo";

/** Persistente en el layout: null en estado significa "nunca tuvo Suscripcion" (mismo tratamiento
 * que un estado bloqueante: el backend tampoco deja aprobar). El pago ya no se hace desde la app,
 * así que ningún banner lleva botón ni enlace: el texto pide contactar con Ganera. La única acción
 * es "Reintentar" cuando falla la carga del estado. Ni bloqueante ni con aviso -> no se muestra. */
export function SuscripcionBanner({ suscripcion }: { suscripcion: SuscripcionEstadoHook }) {
  const { estado, cargando, error, recargar } = suscripcion;

  if (cargando) {
    return null;
  }

  // Sin el estado no se sabe si se puede aprobar: se dice, en vez de no enseñar nada.
  if (error) {
    return (
      <Alert variant="destructive" className="rounded-none border-x-0 border-t-0">
        <TriangleAlertIcon />
        <AlertTitle>No se ha podido comprobar tu suscripción</AlertTitle>
        <AlertDescription>{mensajeDeError(error, "suscripcion")}</AlertDescription>
        <AlertAction>
          <Button size="sm" variant="outline" onClick={recargar}>
            Reintentar
          </Button>
        </AlertAction>
      </Alert>
    );
  }

  if (estado === null) {
    return (
      <Alert variant="destructive" className="rounded-none border-x-0 border-t-0">
        <TriangleAlertIcon />
        <AlertTitle>{TITULO_BLOQUEANTE}</AlertTitle>
        <AlertDescription>{MENSAJE_SIN_SUSCRIPCION}</AlertDescription>
      </Alert>
    );
  }

  const mensajeBloqueante = MENSAJES_BLOQUEANTES[estado.estado];
  if (mensajeBloqueante) {
    return (
      <Alert variant="destructive" className="rounded-none border-x-0 border-t-0">
        <TriangleAlertIcon />
        <AlertTitle>{TITULO_BLOQUEANTE}</AlertTitle>
        <AlertDescription>{mensajeBloqueante}</AlertDescription>
      </Alert>
    );
  }

  const mensajeAviso = MENSAJES_AVISO[estado.estado];
  if (mensajeAviso) {
    return (
      <Alert className="rounded-none border-x-0 border-t-0">
        <TriangleAlertIcon />
        <AlertTitle>Aviso de pago</AlertTitle>
        <AlertDescription>{mensajeAviso}</AlertDescription>
      </Alert>
    );
  }

  return null;
}
