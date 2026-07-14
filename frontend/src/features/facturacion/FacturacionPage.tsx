import { useState } from "react";
import { useOutletContext } from "react-router-dom";
import axios from "axios";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import type { AppLayoutContext } from "@/shared/layout/AppLayout";
import { crearSesionCheckout } from "./api";

const ETIQUETAS_ESTADO: Record<string, string> = {
  TRIAL: "Periodo de prueba",
  TRIAL_EXPIRADO_SIN_PAGO: "Prueba expirada, sin pago",
  ACTIVA: "Activa",
  IMPAGO_GRACIA: "Pago fallido (periodo de gracia)",
  SUSPENDIDA: "Suspendida",
  CANCELADA: "Cancelada",
};

export function FacturacionPage() {
  const { suscripcion } = useOutletContext<AppLayoutContext>();
  const { estado, cargando, error, recargar } = suscripcion;
  const [iniciandoCheckout, setIniciandoCheckout] = useState(false);
  const [errorCheckout, setErrorCheckout] = useState<string | null>(null);

  async function handleCheckout() {
    setIniciandoCheckout(true);
    setErrorCheckout(null);
    try {
      const url = await crearSesionCheckout();
      window.location.href = url;
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 503) {
        setErrorCheckout(
          "La facturación todavía no está configurada. Vuelve a intentarlo más tarde.",
        );
      } else {
        setErrorCheckout("No se ha podido iniciar el pago. Inténtalo de nuevo.");
      }
    } finally {
      setIniciandoCheckout(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Facturación</h1>
        <p className="text-muted-foreground">Estado de la suscripción de tu gestoría.</p>
      </div>

      {cargando && <p className="text-sm text-muted-foreground">Cargando…</p>}

      {error && (
        <Alert variant="destructive">
          <AlertTitle>No se ha podido cargar el estado de la suscripción</AlertTitle>
          <AlertDescription>
            <Button variant="outline" size="sm" onClick={recargar} className="mt-2">
              Reintentar
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {!cargando && !error && (
        <Card>
          <CardHeader>
            <CardTitle>Estado actual</CardTitle>
            <CardDescription>
              {estado
                ? "Tu gestoría tiene una suscripción registrada."
                : "Tu gestoría todavía no tiene ninguna suscripción."}
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            {estado ? (
              <>
                <div className="flex items-center gap-2">
                  <Badge variant={estado.puedeAprobarTramites ? "secondary" : "destructive"}>
                    {ETIQUETAS_ESTADO[estado.estado] ?? estado.estado}
                  </Badge>
                </div>
                {estado.explotacionesContratadas !== null && (
                  <p className="text-sm text-muted-foreground">
                    {estado.explotacionesContratadas} explotaciones contratadas.
                  </p>
                )}
                {!estado.puedeAprobarTramites && (
                  <Alert variant="destructive">
                    <AlertTitle>No puedes aprobar trámites ahora mismo</AlertTitle>
                    <AlertDescription>
                      Actualiza el pago de tu suscripción para poder volver a aprobar trámites.
                    </AlertDescription>
                  </Alert>
                )}
                <Button onClick={handleCheckout} disabled={iniciandoCheckout} className="w-fit">
                  {iniciandoCheckout ? "Abriendo pago…" : "Actualizar suscripción"}
                </Button>
              </>
            ) : (
              <>
                <p className="text-sm text-muted-foreground">
                  Empieza tu prueba gratuita de 15 días para poder aprobar trámites. Solo se puede
                  una vez por gestoría.
                </p>
                <Button onClick={handleCheckout} disabled={iniciandoCheckout} className="w-fit">
                  {iniciandoCheckout ? "Abriendo pago…" : "Empezar prueba de 15 días"}
                </Button>
              </>
            )}

            {errorCheckout && (
              <Alert variant="destructive">
                <AlertTitle>No se ha podido continuar</AlertTitle>
                <AlertDescription>{errorCheckout}</AlertDescription>
              </Alert>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
