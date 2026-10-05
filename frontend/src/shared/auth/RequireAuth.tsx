import { useEffect } from "react";
import { CircleAlertIcon } from "lucide-react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { mensajeDeError } from "@/shared/api/errores";
import { useAuth } from "./AuthContext";

/** Lo que RequireAuth deja en el state del router al mandar a /login: a dónde volver. */
export interface EstadoRedireccionLogin {
  from?: string;
}

export function RequireAuth() {
  const {
    estado,
    errorComprobacion,
    motivoCierre,
    reintentarComprobacion,
    logout,
    registrarRutaProtegida,
  } = useAuth();
  const location = useLocation();

  // Mientras hay una ruta protegida en pantalla, un 401 cuenta como "sesión caducada" (M3).
  useEffect(() => registrarRutaProtegida(), [registrarRutaProtegida]);

  if (estado === "comprobando") {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background p-6">
        <p role="status" aria-live="polite" className="text-sm text-muted-foreground">
          Comprobando tu sesión…
        </p>
      </div>
    );
  }

  if (estado === "error-comprobacion") {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background p-6">
        <div className="flex w-full max-w-sm flex-col gap-4">
          <Alert variant="destructive">
            <CircleAlertIcon />
            <AlertTitle>No se ha podido comprobar tu sesión</AlertTitle>
            <AlertDescription>
              {mensajeDeError(errorComprobacion, "comprobar-sesion")}
            </AlertDescription>
          </Alert>
          <div className="flex gap-2">
            <Button onClick={reintentarComprobacion}>Reintentar</Button>
            <Button variant="outline" onClick={logout}>
              Salir
            </Button>
          </div>
        </div>
      </div>
    );
  }

  if (estado !== "activa") {
    // Tras "Salir" no se guarda la ruta: el siguiente login (quizá de otra persona) empieza de cero.
    const state: EstadoRedireccionLogin | undefined =
      motivoCierre === "manual"
        ? undefined
        : { from: `${location.pathname}${location.search}${location.hash}` };
    return <Navigate to="/login" replace state={state} />;
  }

  return <Outlet />;
}
