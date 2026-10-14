import { useRef, useState, type ChangeEvent } from "react";
import { CircleAlertIcon, UploadIcon } from "lucide-react";
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
import { mensajeDeError } from "@/shared/api/errores";
import { importarExcel } from "./api";
import type { ImportHojaResumen, ImportResumen } from "./types";

function plural(n: number, singular: string, plural: string): string {
  return `${n} ${n === 1 ? singular : plural}`;
}

function textoFilasConError(n: number): string {
  return plural(n, "fila con error", "filas con error");
}

/** Género gramatical de cada hoja: «Explotaciones» es femenino; «Animales» y «Contactos», masculinos. */
type Genero = "f" | "m";

function textoCreadas(n: number, genero: Genero): string {
  return genero === "f" ? plural(n, "creada", "creadas") : plural(n, "creado", "creados");
}

function textoActualizadas(n: number, genero: Genero): string {
  return genero === "f"
    ? plural(n, "actualizada", "actualizadas")
    : plural(n, "actualizado", "actualizados");
}

function fraseHoja(titulo: string, hoja: ImportHojaResumen, genero: Genero): string {
  return `${titulo}: ${textoCreadas(hoja.creadas, genero)}, ${textoActualizadas(hoja.actualizadas, genero)}.`;
}

/** Lo que se anuncia al terminar (WCAG 4.1.3): el resultado entero, no solo que terminó. */
function fraseResumenImportacion(resumen: ImportResumen): string {
  const partes = [
    "Importación terminada.",
    fraseHoja("Explotaciones", resumen.explotaciones, "f"),
    fraseHoja("Animales", resumen.animales, "m"),
    fraseHoja("Contactos", resumen.contactos, "m"),
  ];
  if (resumen.errores.length > 0) partes.push(`${textoFilasConError(resumen.errores.length)}.`);
  return partes.join(" ");
}

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
      setError(mensajeDeError(err, "importar-excel"));
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
          hoja &quot;Animales&quot; (crotal, especie, codigo_rega_explotacion). Opcional: hoja
          &quot;Contactos&quot; (telefono, nombre, codigo_explotacion, rol). Reimportar el mismo
          fichero actualiza en vez de duplicar.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {/* Siempre montado y solo cambia su texto, para que el lector de pantalla lo anuncie. El
            error no pasa por aquí: ya lo anuncia su Alert (role=alert). */}
        <p role="status" className="sr-only">
          {subiendo ? "Importando el Excel…" : resumen ? fraseResumenImportacion(resumen) : ""}
        </p>
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
            <CircleAlertIcon />
            <AlertTitle>No se ha podido importar</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {resumen && (
          <div data-testid="resumen-importacion" className="flex flex-col gap-3 border border-border p-4">
            <div className="flex flex-wrap gap-6 text-sm">
              <ResumenHoja titulo="Explotaciones" resumen={resumen.explotaciones} genero="f" />
              <ResumenHoja titulo="Animales" resumen={resumen.animales} genero="m" />
              {/* Siempre visible, también con 0/0/0: el frontend no distingue "sin hoja" de "hoja
                  vacía", y ocultarla sería justo el dato silencioso que H10 quiere evitar. */}
              <ResumenHoja titulo="Contactos" resumen={resumen.contactos} genero="m" />
            </div>
            {resumen.errores.length > 0 && (
              <div>
                <p className="mb-2 text-sm font-medium">
                  {textoFilasConError(resumen.errores.length)}:
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

function ResumenHoja({
  titulo,
  resumen,
  genero,
}: {
  titulo: string;
  resumen: ImportHojaResumen;
  genero: Genero;
}) {
  return (
    <div>
      <p className="font-bold">{titulo}</p>
      <p className="text-muted-foreground">
        {[
          plural(resumen.filasProcesadas, "fila", "filas"),
          textoCreadas(resumen.creadas, genero),
          textoActualizadas(resumen.actualizadas, genero),
        ].join(" · ")}
      </p>
    </div>
  );
}
