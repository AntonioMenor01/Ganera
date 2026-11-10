import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { aErrorApi, mensajeDeError, type ErrorApi } from "@/shared/api/errores";
import { CLASE_ENLACE } from "@/shared/ui/enlace";
import { listarTramites } from "@/features/tramites/api";
import { BadgeEstadoTramite } from "@/features/tramites/BadgesTramite";
import { etiquetaTipoTramite } from "@/features/tramites/etiquetas";
import type { Tramite } from "@/features/tramites/types";

const TAMANIO = 50;

/**
 * Vista de «Bandeja» sin endpoint de mensajes: lista los trámites que llegaron por WhatsApp
 * (origen WHATSAPP) a partir de GET /tramites. El chat sigue en el modal de cada trámite.
 */
export function BandejaPage() {
  const [items, setItems] = useState<Tramite[]>([]);
  const [totalWhatsapp, setTotalWhatsapp] = useState(0);
  const [error, setError] = useState<ErrorApi | null>(null);
  const [cargando, setCargando] = useState(true);
  const [version, setVersion] = useState(0);
  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setError(null);
    listarTramites(0, TAMANIO)
      .then((pagina) => {
        if (cancelado) return;
        const whatsapp = pagina.content.filter((t) => t.origen === "WHATSAPP");
        setItems(whatsapp);
        setTotalWhatsapp(whatsapp.length);
        setCargando(false);
      })
      .catch((err) => {
        if (cancelado) return;
        setItems([]);
        setError(aErrorApi(err));
        setCargando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [version]);

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-xl font-bold tracking-tight">Bandeja</h1>
        <p className="text-sm text-muted-foreground">
          Trámites que entraron por WhatsApp (los {TAMANIO} más recientes). Para responder al
          contacto, ábrelo en la cola de trámites.
        </p>
      </header>

      {error && (
        <Alert variant="destructive">
          <AlertTitle>No se ha podido cargar la bandeja</AlertTitle>
          <AlertDescription className="flex flex-col gap-3">
            <p>{mensajeDeError(error, "listar-tramites")}</p>
            <Button type="button" variant="outline" size="sm" className="w-fit" onClick={recargar}>
              Reintentar
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {!error && !cargando && totalWhatsapp === 0 && (
        <p className="rounded-sm border border-border bg-card px-4 py-3 text-sm text-muted-foreground">
          No hay trámites de WhatsApp entre los más recientes. Cuando un contacto escriba, aparecerán
          aquí y en{" "}
          <Link to="/tramites" className={CLASE_ENLACE}>
            Trámites
          </Link>
          .
        </p>
      )}

      {(cargando || items.length > 0) && (
        <ul className="divide-y divide-border rounded-sm border border-border bg-card">
          {cargando
            ? Array.from({ length: 4 }, (_, i) => (
                <li key={i} className="h-14 animate-pulse bg-muted/40" />
              ))
            : items.map((tramite) => (
                <li key={tramite.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
                  <div className="flex min-w-0 flex-col gap-0.5">
                    <Link to="/tramites" className={CLASE_ENLACE}>
                      {etiquetaTipoTramite(tramite.tipoTramite ?? "") || "Sin tipo"}
                      {tramite.explotacionNombre ? ` · ${tramite.explotacionNombre}` : ""}
                    </Link>
                    <span className="text-xs text-muted-foreground">
                      {tramite.explotacionCodigoRega ?? "Sin explotación"} · #{tramite.id}
                    </span>
                  </div>
                  <BadgeEstadoTramite
                    estado={tramite.estado}
                    motivoRechazo={tramite.motivoRechazo}
                  />
                </li>
              ))}
        </ul>
      )}
    </div>
  );
}
