import { useCallback, useEffect, useState } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import type { Pagina } from "@/shared/api/types";
import { listarExplotaciones } from "./api";
import type { Explotacion } from "./types";
import { ImportarExcelSection } from "./ImportarExcelSection";

const TAMANIO_PAGINA = 20;

export function ExplotacionesPage() {
  const [pagina, setPagina] = useState<Pagina<Explotacion> | null>(null);
  const [numeroPagina, setNumeroPagina] = useState(0);
  const [cargando, setCargando] = useState(true);
  const [version, setVersion] = useState(0);

  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    listarExplotaciones(numeroPagina, TAMANIO_PAGINA)
      .then((resultado) => {
        if (!cancelado) setPagina(resultado);
      })
      .finally(() => {
        if (!cancelado) setCargando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [numeroPagina, version]);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Explotaciones</h1>
      </div>

      <Card className="w-fit min-w-48">
        <CardContent className="flex flex-col gap-1">
          <span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
            Explotaciones totales
          </span>
          <span className="text-3xl font-semibold">
            {pagina ? pagina.totalElements : "—"}
          </span>
        </CardContent>
      </Card>

      <ImportarExcelSection
        onImportado={() => {
          setNumeroPagina(0);
          recargar();
        }}
      />

      <div className="rounded-xl border bg-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Código REGA</TableHead>
              <TableHead>Nombre</TableHead>
              <TableHead>Ganadero</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {pagina?.content.length === 0 && !cargando && (
              <TableRow>
                <TableCell colSpan={3} className="text-center text-muted-foreground">
                  No hay explotaciones todavía. Importa un Excel para empezar.
                </TableCell>
              </TableRow>
            )}
            {pagina?.content.map((explotacion) => (
              <TableRow key={explotacion.id}>
                <TableCell>{explotacion.codigoRega}</TableCell>
                <TableCell>{explotacion.nombre}</TableCell>
                <TableCell>{explotacion.nombreGanadero}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {pagina && pagina.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <Button
            variant="outline"
            size="sm"
            disabled={numeroPagina === 0}
            onClick={() => setNumeroPagina((p) => p - 1)}
          >
            Anterior
          </Button>
          <span className="text-sm text-muted-foreground">
            Página {pagina.number + 1} de {pagina.totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={numeroPagina + 1 >= pagina.totalPages}
            onClick={() => setNumeroPagina((p) => p + 1)}
          >
            Siguiente
          </Button>
        </div>
      )}
    </div>
  );
}
