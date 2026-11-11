import { useEffect, useRef, useState, type FormEvent } from "react";
import { CircleAlertIcon } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { mensajeDeError } from "@/shared/api/errores";
import { CampoExplotacion } from "./CampoExplotacion";
import { crearTramite } from "./api";
import { TIPOS_TRAMITE, etiquetaTipoTramite, type TipoTramite } from "./etiquetas";
import type { TramiteDetalle } from "./types";

const MOTIVO_TIPO = "Elige el tipo de trámite.";
const MOTIVO_EXPLOTACION = "Elige la explotación.";

/**
 * Alta MANUAL de un trámite (POST /tramites). La gestoría rellena tipo, explotación y, si los
 * tiene, crotales y fecha del hecho. Queda en pendiente de revisión; no se envía nada a OVZ.
 */
export function AltaTramiteDialog({
  open,
  onOpenChange,
  onCreado,
  explotacionIdInicial = null,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreado: (tramite: TramiteDetalle) => void;
  /** Prefill desde ficha de explotación/ganadero. */
  explotacionIdInicial?: number | null;
}) {
  const [tipoTramite, setTipoTramite] = useState<TipoTramite | "">("");
  const [explotacionId, setExplotacionId] = useState<number | null>(null);
  const [crotalesTexto, setCrotalesTexto] = useState("");
  const [fechaHecho, setFechaHecho] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const tipoRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (open) {
      setTipoTramite("");
      setExplotacionId(explotacionIdInicial);
      setCrotalesTexto("");
      setFechaHecho("");
      setError(null);
      setEnviando(false);
    }
  }, [open, explotacionIdInicial]);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (!tipoTramite) {
      setError(MOTIVO_TIPO);
      return;
    }
    if (explotacionId == null) {
      setError(MOTIVO_EXPLOTACION);
      return;
    }
    const crotales = crotalesTexto
      .split(/[\n,;]+/)
      .map((c) => c.trim())
      .filter(Boolean);
    setError(null);
    setEnviando(true);
    try {
      const creado = await crearTramite({
        tipoTramite,
        explotacionId,
        crotales: crotales.length > 0 ? crotales : undefined,
        fechaHecho: fechaHecho || undefined,
      });
      onCreado(creado);
      onOpenChange(false);
    } catch (err) {
      setError(mensajeDeError(err));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        initialFocus={tipoRef}
        className="gap-0 overflow-hidden p-0 sm:max-w-lg"
      >
        <form onSubmit={enviar} className="grid gap-0" noValidate>
          <DialogHeader className="gap-1.5 border-b-2 border-divisor px-5 py-4 text-left">
            <DialogTitle className="text-xl font-bold tracking-[-0.02em]">
              Nuevo trámite
            </DialogTitle>
            <DialogDescription>
              Para cuando te lo piden por teléfono, en persona o por correo. Queda pendiente de
              revisión; Ganera no lo envía a OVZ.
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 px-5 py-5">
            <div className="grid gap-2">
              <Label htmlFor="alta-tramite-tipo">Tipo de trámite</Label>
              <Select
                value={tipoTramite || null}
                onValueChange={(valor) => {
                  if (valor) setTipoTramite(valor as TipoTramite);
                }}
                disabled={enviando}
              >
                <SelectTrigger
                  ref={tipoRef}
                  id="alta-tramite-tipo"
                  className="w-full data-[size=default]:h-10"
                >
                  <SelectValue>
                    {(valor: string | null) =>
                      valor ? etiquetaTipoTramite(valor) : "Elige un tipo"
                    }
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
            </div>

            <div className="grid gap-2">
              <Label htmlFor="alta-tramite-explotacion">Explotación</Label>
              <CampoExplotacion
                idCampo="alta-tramite-explotacion"
                explotacionId={explotacionId}
                asignada={null}
                onCambiar={(id) => setExplotacionId(id)}
                deshabilitado={enviando}
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="alta-tramite-crotales">Crotales (opcional)</Label>
              <Input
                id="alta-tramite-crotales"
                value={crotalesTexto}
                onChange={(e) => setCrotalesTexto(e.target.value)}
                placeholder="Últimos dígitos o crotal completo, separados por coma"
                disabled={enviando}
                autoComplete="off"
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="alta-tramite-fecha">Fecha del hecho (opcional)</Label>
              <Input
                id="alta-tramite-fecha"
                type="date"
                value={fechaHecho}
                onChange={(e) => setFechaHecho(e.target.value)}
                disabled={enviando}
              />
            </div>

            {error && (
              <Alert variant="destructive">
                <CircleAlertIcon />
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
          </div>

          <DialogFooter className="border-t-2 border-divisor px-5 py-4">
            <Button
              type="button"
              variant="outline"
              disabled={enviando}
              onClick={() => onOpenChange(false)}
            >
              Cancelar
            </Button>
            <Button type="submit" disabled={enviando}>
              {enviando ? "Creando…" : "Crear trámite"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
