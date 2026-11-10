import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { aErrorApi, mensajeDeError, type ErrorApi } from "@/shared/api/errores";
import { CLASE_ENLACE } from "@/shared/ui/enlace";
import { listarTramites } from "@/features/tramites/api";
import { etiquetaTipoTramite } from "@/features/tramites/etiquetas";
import { fechaHechoEnOvz } from "@/features/tramites/hechoEnOvz";
import type { Tramite } from "@/features/tramites/types";

const TAMANIO = 100;

type Dia = { clave: string; etiqueta: string; items: Tramite[] };

/** Agrupa por día (Madrid) los trámites marcados como hechos en OVZ. Sin createdAt en el listado,
 * no inventamos fechas de alta: solo usamos `hechoEnOvzEn`, que sí viene del backend. */
export function CalendarioPage() {
  const [hechos, setHechos] = useState<Tramite[]>([]);
  const [pendientesOvz, setPendientesOvz] = useState(0);
  const [error, setError] = useState<ErrorApi | null>(null);
  const [cargando, setCargando] = useState(true);
  const [version, setVersion] = useState(0);
  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setError(null);
    Promise.all([
      listarTramites(0, TAMANIO, "HECHO_EN_OVZ"),
      listarTramites(0, 1, "APROBADO"),
    ])
      .then(([hechosPag, aprobadosPag]) => {
        if (cancelado) return;
        setHechos(hechosPag.content.filter((t) => t.hechoEnOvzEn));
        setPendientesOvz(aprobadosPag.totalElements);
        setCargando(false);
      })
      .catch((err) => {
        if (cancelado) return;
        setHechos([]);
        setError(aErrorApi(err));
        setCargando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [version]);

  const dias: Dia[] = useMemo(() => agruparPorDia(hechos), [hechos]);

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-xl font-bold tracking-tight">Calendario</h1>
        <p className="text-sm text-muted-foreground">
          Días en los que marcaste trámites como hechos en OVZ (dato real). Los plazos de
          carencia/alertas aún no tienen backend.
        </p>
      </header>

      {error && (
        <Alert variant="destructive">
          <AlertTitle>No se ha podido cargar el calendario</AlertTitle>
          <AlertDescription className="flex flex-col gap-3">
            <p>{mensajeDeError(error, "listar-tramites")}</p>
            <Button type="button" variant="outline" size="sm" className="w-fit" onClick={recargar}>
              Reintentar
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {!error && (
        <p className="rounded-sm border border-border bg-card px-4 py-3 text-sm">
          {cargando ? (
            "Cargando…"
          ) : (
            <>
              Hay{" "}
              <Link to="/tramites" className={CLASE_ENLACE}>
                {pendientesOvz} {pendientesOvz === 1 ? "trámite aprobado" : "trámites aprobados"}
              </Link>{" "}
              pendientes de marcar como hechos en OVZ.
            </>
          )}
        </p>
      )}

      {!error && !cargando && dias.length === 0 && (
        <p className="text-sm text-muted-foreground">
          Todavía no hay trámites marcados como hechos en OVZ. Cuando marques uno desde la ficha o el
          modal, aparecerá aquí por día.
        </p>
      )}

      <ul className="flex flex-col gap-6">
        {dias.map((dia) => (
          <li key={dia.clave} className="flex flex-col gap-2">
            <h2 className="text-sm font-bold text-muted-foreground">{dia.etiqueta}</h2>
            <ul className="divide-y divide-border rounded-sm border border-border bg-card">
              {dia.items.map((tramite) => (
                <li key={tramite.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 text-sm">
                  <Link to="/tramites" className={CLASE_ENLACE}>
                    {etiquetaTipoTramite(tramite.tipoTramite ?? "") || "Trámite"}
                    {tramite.explotacionNombre ? ` · ${tramite.explotacionNombre}` : ""}
                  </Link>
                  <span className="text-xs text-muted-foreground">
                    {fechaHechoEnOvz(tramite.hechoEnOvzEn) ?? ""}
                    {tramite.hechoEnOvzPorNombre ? ` · ${tramite.hechoEnOvzPorNombre}` : ""}
                  </span>
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </div>
  );
}

function agruparPorDia(tramites: Tramite[]): Dia[] {
  const mapa = new Map<string, Dia>();
  for (const t of tramites) {
    const iso = t.hechoEnOvzEn;
    if (!iso) continue;
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) continue;
    const clave = new Intl.DateTimeFormat("en-CA", {
      timeZone: "Europe/Madrid",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(d);
    const etiqueta =
      new Intl.DateTimeFormat("es-ES", {
        timeZone: "Europe/Madrid",
        weekday: "long",
        day: "numeric",
        month: "long",
        year: "numeric",
      }).format(d);
    const dia = mapa.get(clave) ?? { clave, etiqueta, items: [] };
    dia.items.push(t);
    mapa.set(clave, dia);
  }
  return [...mapa.values()].sort((a, b) => (a.clave < b.clave ? 1 : -1));
}
