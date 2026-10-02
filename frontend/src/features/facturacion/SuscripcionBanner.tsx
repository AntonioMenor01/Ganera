import { TriangleAlertIcon } from "lucide-react";
import { Link } from "react-router-dom";
import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { mensajeDeError } from "@/shared/api/errores";
import type { SuscripcionEstadoHook } from "./useSuscripcionEstado";

const MENSAJES_BLOQUEANTES: Record<string, string> = {
  TRIAL_EXPIRADO_SIN_PAGO:
    "El periodo de prueba ha terminado y no hay ningún pago activo. No se pueden aprobar trámites hasta regularizar la suscripción.",
  SUSPENDIDA:
    "La suscripción está suspendida. No se pueden aprobar trámites hasta regularizarla.",
};

const MENSAJES_AVISO: Record<string, string> = {
  IMPAGO_GRACIA:
    "El último pago ha fallado. Sigues teniendo acceso completo durante el periodo de gracia, pero conviene regularizarlo cuanto antes.",
};

/** Persistente en el layout: null en estado significa "nunca tuvo Suscripcion" (mismo tratamiento
 * que un estado bloqueante -- ver Facturacion). Un fallo al cargar el estado se avisa con
 * "Reintentar". Ni bloqueante ni con aviso -> no se muestra nada. */
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
        <AlertTitle>Todavía no tienes una suscripción</AlertTitle>
        <AlertDescription>
          Empieza tu prueba gratuita de 15 días para poder aprobar trámites.
        </AlertDescription>
        <AlertAction>
          <Button size="sm" nativeButton={false} render={<Link to="/facturacion" />}>
            Ir a facturación
          </Button>
        </AlertAction>
      </Alert>
    );
  }

  const mensajeBloqueante = MENSAJES_BLOQUEANTES[estado.estado];
  if (mensajeBloqueante) {
    return (
      <Alert variant="destructive" className="rounded-none border-x-0 border-t-0">
        <TriangleAlertIcon />
        <AlertTitle>No puedes aprobar trámites ahora mismo</AlertTitle>
        <AlertDescription>{mensajeBloqueante}</AlertDescription>
        <AlertAction>
          <Button size="sm" nativeButton={false} render={<Link to="/facturacion" />}>
            Ir a facturación
          </Button>
        </AlertAction>
      </Alert>
    );
  }

  const mensajeAviso = MENSAJES_AVISO[estado.estado];
  if (mensajeAviso) {
    return (
      <Alert className="rounded-none border-x-0 border-t-0">
        <TriangleAlertIcon />
        <AlertTitle>Aviso de facturación</AlertTitle>
        <AlertDescription>{mensajeAviso}</AlertDescription>
        <AlertAction>
          <Button size="sm" variant="outline" nativeButton={false} render={<Link to="/facturacion" />}>
            Ir a facturación
          </Button>
        </AlertAction>
      </Alert>
    );
  }

  return null;
}
