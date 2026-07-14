import { useEffect, useState } from "react";
import axios from "axios";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { aprobarTramite, obtenerDetalleTramite, rechazarTramite } from "./api";
import type { TramiteDetalle } from "./types";

interface TramiteReviewDialogProps {
  tramiteId: number | null;
  onClose: () => void;
  onCambiado: () => void;
}

export function TramiteReviewDialog({
  tramiteId,
  onClose,
  onCambiado,
}: TramiteReviewDialogProps) {
  const [detalle, setDetalle] = useState<TramiteDetalle | null>(null);
  const [cargando, setCargando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);

  useEffect(() => {
    if (tramiteId === null) {
      setDetalle(null);
      setErrorAccion(null);
      return;
    }
    let cancelado = false;
    setCargando(true);
    obtenerDetalleTramite(tramiteId)
      .then((resultado) => {
        if (!cancelado) setDetalle(resultado);
      })
      .finally(() => {
        if (!cancelado) setCargando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [tramiteId]);

  async function handleAprobar() {
    if (tramiteId === null) return;
    setEnviando(true);
    setErrorAccion(null);
    try {
      await aprobarTramite(tramiteId);
      onCambiado();
      onClose();
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 403) {
        setErrorAccion(
          "Tu suscripción no permite aprobar trámites ahora mismo (trial expirado o suspendida). Actualiza tu suscripción en Facturación.",
        );
      } else {
        setErrorAccion("No se ha podido aprobar el trámite. Inténtalo de nuevo.");
      }
    } finally {
      setEnviando(false);
    }
  }

  async function handleRechazar() {
    if (tramiteId === null) return;
    setEnviando(true);
    setErrorAccion(null);
    try {
      await rechazarTramite(tramiteId);
      onCambiado();
      onClose();
    } catch {
      setErrorAccion("No se ha podido rechazar el trámite. Inténtalo de nuevo.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Dialog
      open={tramiteId !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Revisión de trámite</DialogTitle>
          <DialogDescription>
            {detalle ? `Trámite #${detalle.id}` : "Cargando…"}
          </DialogDescription>
        </DialogHeader>

        {cargando && <p className="text-sm text-muted-foreground">Cargando detalle…</p>}

        {detalle && !cargando && (
          <div className="flex flex-col gap-4 text-sm">
            <div className="flex items-center gap-2">
              <span className="font-medium">Estado:</span>
              <Badge variant="outline">{detalle.estado}</Badge>
            </div>

            <div>
              <p className="font-medium">Tipo de trámite</p>
              <p className="text-muted-foreground">
                {detalle.tipoTramite ?? "Sin determinar todavía"}
              </p>
            </div>

            <div>
              <p className="font-medium">Explotación</p>
              {detalle.explotacionId ? (
                <p className="text-muted-foreground">
                  {detalle.explotacionNombre} ({detalle.explotacionCodigoRega})
                </p>
              ) : (
                <p className="text-muted-foreground">
                  Sin resolver — hay que asignar la explotación manualmente.
                </p>
              )}
            </div>

            <div>
              <p className="font-medium">Mensaje original de WhatsApp</p>
              {detalle.mensajeOriginal ? (
                <p className="rounded-md bg-muted p-2 text-muted-foreground">
                  {detalle.mensajeOriginal}
                </p>
              ) : (
                <p className="text-muted-foreground">No disponible todavía.</p>
              )}
            </div>

            {detalle.motivoError && (
              <Alert variant="destructive">
                <AlertTitle>Motivo del error</AlertTitle>
                <AlertDescription>{detalle.motivoError}</AlertDescription>
              </Alert>
            )}
          </div>
        )}

        {errorAccion && (
          <Alert variant="destructive">
            <AlertTitle>Acción no completada</AlertTitle>
            <AlertDescription>{errorAccion}</AlertDescription>
          </Alert>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={handleRechazar} disabled={enviando || cargando}>
            Rechazar
          </Button>
          <Button onClick={handleAprobar} disabled={enviando || cargando}>
            Aprobar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
