import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, CircleAlertIcon, PlusIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  aErrorApi,
  esCancelacion,
  mensajeDeError,
  type ErrorApi,
} from "@/shared/api/errores";
import { CLASE_ENLACE } from "@/shared/ui/enlace";
import { AltaTramiteDialog } from "@/features/tramites/AltaTramiteDialog";
import { PersonasDeExplotacion } from "@/features/contactos/PersonasDeExplotacion";
import { obtenerExplotacion } from "./api";
import type { ExplotacionDetalle } from "./types";
import { AnimalesDeExplotacion } from "./AnimalesDeExplotacion";

type Vista =
  | { estado: "cargando" }
  | { estado: "error"; error: ErrorApi }
  | { estado: "no-encontrado" }
  | { estado: "listo"; explotacion: ExplotacionDetalle };

function idDeRuta(valor: string | undefined): number | null {
  if (!valor || !/^[1-9]\d*$/.test(valor)) return null;
  const id = Number(valor);
  return Number.isSafeInteger(id) ? id : null;
}

export function ExplotacionDetallePage() {
  const { id: idParam } = useParams();
  const id = idDeRuta(idParam);
  const navigate = useNavigate();
  const [vista, setVista] = useState<Vista>({ estado: "cargando" });
  const [intento, setIntento] = useState(0);
  const [altaAbierta, setAltaAbierta] = useState(false);

  const reintentar = useCallback(() => setIntento((n) => n + 1), []);

  // Recarga silenciosa tras cambiar los contactos: no vuelve al esqueleto de carga, solo refresca
  // los datos. Si falla, se mantiene lo mostrado (la mutación ya se aplicó en el backend).
  const recargarContactos = useCallback(() => {
    if (id === null) return;
    obtenerExplotacion(id)
      .then((explotacion) => setVista({ estado: "listo", explotacion }))
      .catch(() => {});
  }, [id]);

  useEffect(() => {
    if (id === null) {
      setVista({ estado: "no-encontrado" });
      return;
    }
    const controlador = new AbortController();
    setVista({ estado: "cargando" });
    obtenerExplotacion(id, controlador.signal)
      .then((explotacion) => setVista({ estado: "listo", explotacion }))
      .catch((err: unknown) => {
        if (esCancelacion(err)) return;
        const error = aErrorApi(err);
        setVista(
          error.tipo === "no-encontrado"
            ? { estado: "no-encontrado" }
            : { estado: "error", error },
        );
      });
    return () => controlador.abort();
  }, [id, intento]);

  return (
    <div className="mx-auto flex w-full max-w-[1360px] min-w-0 flex-col gap-5">
      {vista.estado !== "no-encontrado" && (
        <Link
          to="/explotaciones"
          className={cn(
            "inline-flex w-fit items-center gap-1 text-[13px] font-medium text-enlace",
            "hover:underline focus-visible:rounded-sm focus-visible:ring-3 focus-visible:ring-ring",
          )}
        >
          <ArrowLeft aria-hidden="true" className="size-3.5" />
          Explotaciones
        </Link>
      )}

      {vista.estado === "cargando" && (
        <div role="status" className="flex flex-col gap-4">
          <span className="sr-only">Cargando explotación…</span>
          <div aria-hidden="true" className="h-8 w-64 max-w-full rounded-sm bg-muted motion-safe:animate-pulse" />
          <div aria-hidden="true" className="h-40 border border-border bg-card" />
        </div>
      )}

      {vista.estado === "no-encontrado" && (
        <div className="flex flex-col gap-1">
          <h1 className="text-[28px] leading-tight font-bold tracking-[-0.02em]">
            Explotación no encontrada
          </h1>
          <p className="text-sm text-muted-foreground">
            No existe o no pertenece a tu gestoría.
          </p>
          <Link to="/explotaciones" className={cn("mt-2 w-fit", CLASE_ENLACE)}>
            Volver a Explotaciones
          </Link>
        </div>
      )}

      {vista.estado === "error" && (
        <Alert variant="destructive">
          <CircleAlertIcon />
          <AlertTitle>No se ha podido cargar la explotación</AlertTitle>
          <AlertDescription>
            <p>{mensajeDeError(vista.error, "detalle-explotacion")}</p>
            <Button variant="outline" size="sm" onClick={reintentar} className="mt-2">
              Reintentar
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {vista.estado === "listo" && (
        <>
          <header className="flex flex-wrap items-end justify-between gap-3 border-b-2 border-divisor pb-5">
            <div className="min-w-0">
              <p className="mb-1.5 text-[13px] font-medium text-muted-foreground tabular-nums">
                {vista.explotacion.codigoRega}
              </p>
              <h1 className="text-[28px] leading-tight font-bold tracking-[-0.02em] break-words">
                {vista.explotacion.nombre}
              </h1>
              <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
                <span>
                  Ganadero:{" "}
                  <Link
                    to={`/ganaderos/${vista.explotacion.ganaderoId}`}
                    className={CLASE_ENLACE}
                  >
                    {vista.explotacion.nombreGanadero}
                  </Link>
                  {vista.explotacion.ganaderoNif
                    ? ` · NIF ${vista.explotacion.ganaderoNif}`
                    : ""}
                </span>
                <span className="tabular-nums">
                  {vista.explotacion.numeroAnimales}{" "}
                  {vista.explotacion.numeroAnimales === 1 ? "animal" : "animales"}
                </span>
                <span className="tabular-nums">
                  {vista.explotacion.tramitesPendientesRevision}{" "}
                  {vista.explotacion.tramitesPendientesRevision === 1
                    ? "trámite pendiente"
                    : "trámites pendientes"}
                </span>
              </div>
            </div>
            <Button type="button" onClick={() => setAltaAbierta(true)}>
              <PlusIcon aria-hidden="true" className="size-4" />
              Nuevo trámite
            </Button>
          </header>

          <section className="border border-border bg-card" aria-labelledby="contactos-explotacion">
            <h2 id="contactos-explotacion" className="border-b-2 border-divisor px-5 py-3 text-sm font-bold">
              Contactos
            </h2>
            <div className="px-5 py-3">
              <PersonasDeExplotacion
                explotacionId={vista.explotacion.id}
                contactos={vista.explotacion.contactos}
                onCambio={recargarContactos}
                idEtiquetaLista="contactos-explotacion"
              />
            </div>
          </section>

          <section className="border border-border bg-card" aria-labelledby="animales-explotacion">
            <h2 id="animales-explotacion" className="border-b-2 border-divisor px-5 py-3 text-sm font-bold">
              Inventario
            </h2>
            <div className="px-5 py-3">
              <AnimalesDeExplotacion explotacionId={vista.explotacion.id} />
            </div>
          </section>

          <AltaTramiteDialog
            open={altaAbierta}
            onOpenChange={setAltaAbierta}
            explotacionIdInicial={vista.explotacion.id}
            onCreado={(creado) => navigate(`/tramites`, { state: { abrirTramiteId: creado.id } })}
          />
        </>
      )}
    </div>
  );
}
