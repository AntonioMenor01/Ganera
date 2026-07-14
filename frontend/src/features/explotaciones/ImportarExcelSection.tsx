import { useRef, useState, type ChangeEvent } from "react";
import axios from "axios";
import { UploadIcon } from "lucide-react";
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
import { importarExcel } from "./api";
import type { ImportHojaResumen, ImportResumen } from "./types";

/** Vía principal para dar de alta Explotaciones/Animales mientras la sincronización con
 * OVZ.net (Prompt 3a) siga bloqueada -- se le da protagonismo a propósito, no es secundaria. */
export function ImportarExcelSection({ onImportado }: { onImportado: () => void }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [subiendo, setSubiendo] = useState(false);
  const [resumen, setResumen] = useState<ImportResumen | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const archivo = event.target.files?.[0];
    event.target.value = "";
    if (!archivo) {
      return;
    }

    setSubiendo(true);
    setError(null);
    setResumen(null);
    try {
      const resultado = await importarExcel(archivo);
      setResumen(resultado);
      onImportado();
    } catch (err) {
      if (axios.isAxiosError(err) && typeof err.response?.data === "string") {
        setError(err.response.data);
      } else {
        setError("No se ha podido importar el fichero. Inténtalo de nuevo.");
      }
    } finally {
      setSubiendo(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Importar inventario desde Excel</CardTitle>
        <CardDescription>
          Hoja &quot;Explotaciones&quot; (codigo_rega, nombre, nif_ganadero, nombre_ganadero) y
          hoja &quot;Animales&quot; (crotal, especie, codigo_rega_explotacion). Reimportar el mismo
          fichero actualiza en vez de duplicar.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div>
          <input
            ref={inputRef}
            type="file"
            accept=".xlsx"
            className="hidden"
            onChange={handleFileChange}
          />
          <Button onClick={() => inputRef.current?.click()} disabled={subiendo}>
            <UploadIcon />
            {subiendo ? "Importando…" : "Importar Excel"}
          </Button>
        </div>

        {error && (
          <Alert variant="destructive">
            <AlertTitle>No se ha podido importar</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {resumen && (
          <div className="flex flex-col gap-3 rounded-lg border p-4">
            <div className="flex flex-wrap gap-6 text-sm">
              <ResumenHoja titulo="Explotaciones" resumen={resumen.explotaciones} />
              <ResumenHoja titulo="Animales" resumen={resumen.animales} />
            </div>
            {resumen.errores.length > 0 && (
              <div>
                <p className="mb-2 text-sm font-medium">
                  {resumen.errores.length} fila(s) con error:
                </p>
                <ul className="flex flex-col gap-1.5 text-sm text-muted-foreground">
                  {resumen.errores.map((filaError, index) => (
                    <li key={index} className="flex items-start gap-2">
                      <Badge variant="outline" className="shrink-0">
                        {filaError.hoja} · fila {filaError.fila}
                      </Badge>
                      <span>{filaError.motivo}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function ResumenHoja({ titulo, resumen }: { titulo: string; resumen: ImportHojaResumen }) {
  return (
    <div>
      <p className="font-medium">{titulo}</p>
      <p className="text-muted-foreground">
        {resumen.filasProcesadas} filas · {resumen.creadas} creadas · {resumen.actualizadas}{" "}
        actualizadas
      </p>
    </div>
  );
}
