import { useCallback, useEffect, useState } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { Pagina } from "@/shared/api/types";
import { listarTramites } from "./api";
import { badgeVarianteDeEstado, type EstadoTramite, type Tramite } from "./types";
import { TramiteReviewDialog } from "./TramiteReviewDialog";

const TAMANIO_PAGINA = 20;

const ESTADOS: { value: EstadoTramite | "TODOS"; label: string }[] = [
  { value: "TODOS", label: "Todos los estados" },
  { value: "PENDIENTE_EXTRACCION", label: "Pendiente de extracción" },
  { value: "PENDIENTE_REVISION", label: "Pendiente de revisión" },
  { value: "APROBADO", label: "Aprobado" },
  { value: "EN_PROCESO", label: "En proceso" },
  { value: "EJECUTADO_OVZ", label: "Ejecutado en OVZ" },
  { value: "ERROR_OVZ", label: "Error OVZ" },
  { value: "RECHAZADO", label: "Rechazado" },
];

export function TramitesPage() {
  const [pagina, setPagina] = useState<Pagina<Tramite> | null>(null);
  const [numeroPagina, setNumeroPagina] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState<EstadoTramite | "TODOS">("TODOS");
  const [cargando, setCargando] = useState(true);
  const [version, setVersion] = useState(0);
  const [tramiteSeleccionado, setTramiteSeleccionado] = useState<number | null>(null);

  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    listarTramites(
      numeroPagina,
      TAMANIO_PAGINA,
      filtroEstado === "TODOS" ? undefined : filtroEstado,
    )
      .then((resultado) => {
        if (!cancelado) setPagina(resultado);
      })
      .finally(() => {
        if (!cancelado) setCargando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [numeroPagina, filtroEstado, version]);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">Cola de trámites</h1>
          <p className="text-muted-foreground">
            {pagina ? `${pagina.totalElements} trámites` : "Cargando…"}
          </p>
        </div>
        <Select
          value={filtroEstado}
          onValueChange={(value) => {
            setFiltroEstado(value as EstadoTramite | "TODOS");
            setNumeroPagina(0);
          }}
        >
          <SelectTrigger className="w-56">
            <SelectValue>
              {(value: EstadoTramite | "TODOS") =>
                ESTADOS.find((estado) => estado.value === value)?.label ?? value
              }
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            {ESTADOS.map((estado) => (
              <SelectItem key={estado.value} value={estado.value}>
                {estado.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="rounded-xl border bg-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>ID</TableHead>
              <TableHead>Tipo</TableHead>
              <TableHead>Estado</TableHead>
              <TableHead>Explotación</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {pagina?.content.length === 0 && !cargando && (
              <TableRow>
                <TableCell colSpan={4} className="text-center text-muted-foreground">
                  No hay trámites que mostrar.
                </TableCell>
              </TableRow>
            )}
            {pagina?.content.map((tramite) => (
              <TableRow
                key={tramite.id}
                className="cursor-pointer"
                onClick={() => setTramiteSeleccionado(tramite.id)}
              >
                <TableCell>#{tramite.id}</TableCell>
                <TableCell>{tramite.tipoTramite ?? "—"}</TableCell>
                <TableCell>
                  <Badge variant={badgeVarianteDeEstado(tramite.estado)}>{tramite.estado}</Badge>
                </TableCell>
                <TableCell>
                  {tramite.explotacionId ? `#${tramite.explotacionId}` : "Sin resolver"}
                </TableCell>
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

      <TramiteReviewDialog
        tramiteId={tramiteSeleccionado}
        onClose={() => setTramiteSeleccionado(null)}
        onCambiado={recargar}
      />
    </div>
  );
}
