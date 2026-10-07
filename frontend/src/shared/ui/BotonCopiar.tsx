import { CheckIcon, CopyIcon } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { flushSync } from "react-dom";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

/** Cuánto dura «Copiado» en el botón tras copiar. */
const DURACION_COPIADO_MS = 2000;

const AVISO_COPIA_MANUAL = "Selecciona el texto y cópialo (Ctrl+C o ⌘C)";

interface BotonCopiarProps {
  /** Lo que se copia, exactamente, sin transformarlo. */
  texto: string;
  /** Nombre del campo: el nombre accesible del botón es «Copiar {etiqueta}». */
  etiqueta: string;
  /** Clases para el botón (no para el contenedor). */
  className?: string;
  /** Se llama cada vez que una copia sale bien (no si falla). La ficha de OVZ marca la fila. */
  onCopiado?: () => void;
}

/**
 * Copia `texto` al portapapeles con la Clipboard API. Si la API no existe o rechaza (sin `https`,
 * permiso denegado), muestra el texto en un campo de solo lectura, seleccionado y con el foco, para
 * copiarlo a mano. El resultado se anuncia en una región `aria-live` siempre montada. El texto
 * visible es siempre «Copiar» (WCAG 2.5.3: el nombre accesible «Copiar {etiqueta}» lo contiene):
 * tras copiar solo cambia el icono a un check durante 2 s, con `data-copiado`. Nunca escribe el
 * texto en la consola.
 */
export function BotonCopiar({ texto, etiqueta, className, onCopiado }: BotonCopiarProps) {
  const [copiado, setCopiado] = useState(false);
  const [anuncio, setAnuncio] = useState("");
  // Cada fallo incrementa el contador: el efecto vuelve a seleccionar aunque el campo ya estuviera.
  const [fallos, setFallos] = useState(0);
  const [copiaManual, setCopiaManual] = useState(false);

  const pendiente = useRef(false);
  const montado = useRef(true);
  const temporizador = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const temporizadorAnuncio = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const campo = useRef<HTMLInputElement>(null);
  const idAviso = useId();

  useEffect(() => {
    montado.current = true;
    return () => {
      montado.current = false;
      clearTimeout(temporizador.current);
      clearTimeout(temporizadorAnuncio.current);
    };
  }, []);

  useEffect(() => {
    if (fallos > 0 && campo.current) {
      campo.current.focus();
      campo.current.select();
    }
  }, [fallos]);

  /** Vacía la región y pone el texto en el siguiente tick: así un anuncio idéntico al anterior
   * (copiar dos veces seguidas, fallar dos veces) vuelve a cambiar el DOM y se lee de nuevo. */
  function anunciar(mensaje: string) {
    clearTimeout(temporizadorAnuncio.current);
    flushSync(() => setAnuncio(""));
    temporizadorAnuncio.current = setTimeout(() => setAnuncio(mensaje), 0);
  }

  function copiadoConExito() {
    clearTimeout(temporizador.current);
    setCopiaManual(false);
    setCopiado(true);
    anunciar(`Copiado: ${etiqueta}`);
    onCopiado?.();
    temporizador.current = setTimeout(() => {
      setCopiado(false);
      // Vaciar la región permite que la próxima copia se vuelva a anunciar.
      setAnuncio("");
    }, DURACION_COPIADO_MS);
  }

  function copiaFallida() {
    clearTimeout(temporizador.current);
    setCopiado(false);
    setCopiaManual(true);
    anunciar(`No se ha podido copiar ${etiqueta}. ${AVISO_COPIA_MANUAL}`);
    setFallos((n) => n + 1);
  }

  async function copiar() {
    if (pendiente.current) return;
    pendiente.current = true;
    let exito = false;
    try {
      const portapapeles = typeof navigator === "undefined" ? undefined : navigator.clipboard;
      if (typeof portapapeles?.writeText === "function") {
        await portapapeles.writeText(texto);
        exito = true;
      }
    } catch {
      // Sin registrar nada: el error podría incluir el texto. El fallo se ve en la interfaz.
      exito = false;
    } finally {
      pendiente.current = false;
    }
    if (!montado.current) return;
    if (exito) copiadoConExito();
    else copiaFallida();
  }

  return (
    <span className="inline-flex max-w-full flex-col items-start gap-1">
      <Button
        type="button"
        variant="outline"
        size="sm"
        aria-label={`Copiar ${etiqueta}`}
        data-copiado={copiado ? "" : undefined}
        className={cn(className)}
        onClick={() => void copiar()}
      >
        {copiado ? <CheckIcon aria-hidden /> : <CopyIcon aria-hidden />}
        Copiar
      </Button>
      {copiaManual && (
        <span className="flex max-w-full flex-col gap-1">
          <Input
            ref={campo}
            type="text"
            readOnly
            value={texto}
            aria-label={`${etiqueta} para copiar`}
            aria-describedby={idAviso}
            onFocus={(evento) => evento.currentTarget.select()}
            className="font-mono"
          />
          <span id={idAviso} className="text-xs text-muted-foreground">
            {AVISO_COPIA_MANUAL}
          </span>
        </span>
      )}
      <span role="status" aria-live="polite" className="sr-only">
        {anuncio}
      </span>
    </span>
  );
}
