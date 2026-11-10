import { useEffect, useRef, useState, type FormEvent } from "react";
import { CircleAlertIcon } from "lucide-react";
import {
  Dialog,
  DialogClose,
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
import { mensajeDeError } from "@/shared/api/errores";
import { crearGanadero } from "./api";
import type { GanaderoResumen } from "./types";

const MOTIVO_NOMBRE = "El nombre del ganadero es obligatorio.";
const MOTIVO_NIF = "El NIF/CIF es obligatorio.";

/**
 * Alta manual de un ganadero (nombre + NIF). El backend es la fuente de verdad de la validación del
 * NIF (dígito de control) y de la unicidad: aquí solo se comprueba que los campos no estén vacíos y
 * se muestra el motivo exacto que devuelve el servidor (400 NIF inválido / 409 NIF duplicado).
 */
export function AltaGanaderoDialog({
  open,
  onOpenChange,
  onCreado,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreado: (ganadero: GanaderoResumen) => void;
}) {
  const [nombre, setNombre] = useState("");
  const [nif, setNif] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const nombreRef = useRef<HTMLInputElement>(null);

  // Cada apertura parte de un formulario limpio (nada de un intento anterior).
  useEffect(() => {
    if (open) {
      setNombre("");
      setNif("");
      setError(null);
      setEnviando(false);
    }
  }, [open]);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (!nombre.trim()) {
      setError(MOTIVO_NOMBRE);
      return;
    }
    if (!nif.trim()) {
      setError(MOTIVO_NIF);
      return;
    }
    setError(null);
    setEnviando(true);
    try {
      const creado = await crearGanadero(nombre.trim(), nif.trim());
      onCreado(creado);
      onOpenChange(false);
    } catch (err) {
      // El backend da el motivo exacto (NIF inválido, NIF duplicado…); fuera de eso, texto genérico.
      setError(mensajeDeError(err));
    } finally {
      setEnviando(false);
    }
  }

  const errorEnNif = error != null && /nif/i.test(error);
  const errorEnNombre = error != null && /nombre/i.test(error);

  return (
    <Dialog open={open} onOpenChange={(abierto) => onOpenChange(abierto)}>
      <DialogContent
        // El foco inicial va al primer campo, no al botón de cerrar.
        initialFocus={nombreRef}
        className="gap-0 overflow-hidden p-0 sm:max-w-lg"
      >
        <form onSubmit={enviar} className="grid gap-0" noValidate>
          <DialogHeader className="gap-1.5 border-b-2 border-divisor px-5 py-4 text-left">
            <DialogTitle className="text-xl font-bold tracking-[-0.02em]">
              Alta de ganadero
            </DialogTitle>
            <DialogDescription>
              Solo nombre y NIF. Las explotaciones y los contactos llegan por el Excel o por
              WhatsApp.
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 px-5 py-5">
            <div className="grid gap-2">
              <div className="flex items-center gap-1">
                <Label htmlFor="alta-ganadero-nombre">Nombre</Label>
                <span aria-hidden="true" className="text-[13px] font-medium text-enlace">
                  *
                </span>
              </div>
              <Input
                id="alta-ganadero-nombre"
                ref={nombreRef}
                value={nombre}
                onChange={(e) => setNombre(e.target.value)}
                autoComplete="off"
                placeholder="Nombre y apellidos o razón social"
                aria-invalid={errorEnNombre || undefined}
                required
              />
            </div>

            <div className="grid gap-2">
              <div className="flex items-center gap-1">
                <Label htmlFor="alta-ganadero-nif">NIF / CIF</Label>
                <span aria-hidden="true" className="text-[13px] font-medium text-enlace">
                  *
                </span>
              </div>
              <Input
                id="alta-ganadero-nif"
                value={nif}
                onChange={(e) => setNif(e.target.value)}
                autoComplete="off"
                inputMode="text"
                placeholder="12345678Z"
                aria-invalid={errorEnNif || undefined}
                required
              />
            </div>

            {error && (
              <Alert variant="destructive">
                <CircleAlertIcon />
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
          </div>

          <DialogFooter className="border-t-2 border-divisor px-5 py-3 sm:justify-end">
            <DialogClose render={<Button type="button" variant="outline" />}>Cancelar</DialogClose>
            <Button type="submit" disabled={enviando}>
              {enviando ? "Añadiendo…" : "Añadir ganadero"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
