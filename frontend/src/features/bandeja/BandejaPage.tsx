import { useCallback, useEffect, useMemo, useState } from "react";
import {
  CircleAlertIcon,
  MessageCircleIcon,
  PhoneIcon,
  SearchIcon,
  UserIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { aErrorApi, mensajeDeError, type ErrorApi } from "@/shared/api/errores";
import { iniciales } from "@/shared/layout/iniciales";
import { listarTramites, obtenerDetalleTramite } from "@/features/tramites/api";
import { BadgeEstadoTramite } from "@/features/tramites/BadgesTramite";
import {
  etiquetaTipoTramite,
  presentacionEstadoTramite,
  presentacionRespuestaWhatsapp,
} from "@/features/tramites/etiquetas";
import { fechaHoraMadrid, horaMadrid } from "@/features/tramites/fechaMadrid";
import { TramiteReviewDialog } from "@/features/tramites/TramiteReviewDialog";
import type { EstadoTramite, Tramite, TramiteDetalle } from "@/features/tramites/types";

const TAMANIO = 50;

type FiltroBandeja = "TODOS" | "PENDIENTES" | "HECHOS";

const FILTROS: { id: FiltroBandeja; label: string }[] = [
  { id: "TODOS", label: "Todos" },
  { id: "PENDIENTES", label: "Pendientes" },
  { id: "HECHOS", label: "Hechos en OVZ" },
];

const PENDIENTES = new Set<EstadoTramite>(["PENDIENTE_EXTRACCION", "PENDIENTE_REVISION"]);

/**
 * Bandeja al look del mock v2: lista + panel de conversación.
 * Sin API de hilos: cada fila es un trámite `origen=WHATSAPP`. El panel muestra el mensaje
 * original y las respuestas reales del detalle; responder se hace en el modal de revisión.
 */
export function BandejaPage() {
  const [items, setItems] = useState<Tramite[]>([]);
  const [error, setError] = useState<ErrorApi | null>(null);
  const [cargando, setCargando] = useState(true);
  const [version, setVersion] = useState(0);
  const [filtro, setFiltro] = useState<FiltroBandeja>("TODOS");
  const [q, setQ] = useState("");
  const [seleccionadoId, setSeleccionadoId] = useState<number | null>(null);
  const [detalle, setDetalle] = useState<TramiteDetalle | null>(null);
  const [errorDetalle, setErrorDetalle] = useState<ErrorApi | null>(null);
  const [cargandoDetalle, setCargandoDetalle] = useState(false);
  const [revisionId, setRevisionId] = useState<number | null>(null);

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

  const filtrados = useMemo(() => {
    const texto = q.trim().toLocaleLowerCase("es");
    return items.filter((t) => {
      if (filtro === "PENDIENTES" && !PENDIENTES.has(t.estado)) return false;
      if (filtro === "HECHOS" && t.estado !== "HECHO_EN_OVZ") return false;
      if (!texto) return true;
      const haystack = [
        t.explotacionNombre,
        t.explotacionCodigoRega,
        etiquetaTipoTramite(t.tipoTramite ?? ""),
        String(t.id),
      ]
        .filter(Boolean)
        .join(" ")
        .toLocaleLowerCase("es");
      return haystack.includes(texto);
    });
  }, [items, filtro, q]);

  useEffect(() => {
    if (filtrados.length === 0) {
      setSeleccionadoId(null);
      return;
    }
    if (seleccionadoId == null || !filtrados.some((t) => t.id === seleccionadoId)) {
      setSeleccionadoId(filtrados[0].id);
    }
  }, [filtrados, seleccionadoId]);

  useEffect(() => {
    if (seleccionadoId == null) {
      setDetalle(null);
      setErrorDetalle(null);
      return;
    }
    const ac = new AbortController();
    setCargandoDetalle(true);
    setErrorDetalle(null);
    obtenerDetalleTramite(seleccionadoId, ac.signal)
      .then((d) => {
        setDetalle(d);
        setCargandoDetalle(false);
      })
      .catch((err) => {
        if (ac.signal.aborted) return;
        setDetalle(null);
        setErrorDetalle(aErrorApi(err));
        setCargandoDetalle(false);
      });
    return () => ac.abort();
  }, [seleccionadoId, version]);

  const seleccionadoLista = filtrados.find((t) => t.id === seleccionadoId) ?? null;

  return (
    <div className="-mx-6 -mt-6 -mb-6 flex min-h-[calc(100dvh-3.5rem)] flex-col lg:-mx-8 lg:-mt-8 lg:-mb-8 lg:min-h-[calc(100dvh-0px)]">
      <div className="grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-[minmax(280px,380px)_minmax(0,1fr)]">
        {/* Lista */}
        <section
          aria-label="Conversaciones de WhatsApp"
          className="flex min-h-0 flex-col border-b-2 border-divisor bg-card lg:border-r-2 lg:border-b-0"
        >
          <div className="flex flex-col gap-3 border-b-2 border-divisor p-4">
            <h1 className="text-[22px] font-bold tracking-[-0.01em]">Bandeja</h1>
            <label className="relative block">
              <span className="sr-only">Buscar explotación o tipo</span>
              <SearchIcon
                aria-hidden="true"
                className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground"
              />
              <Input
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder="Buscar explotación o tipo"
                className="h-9 bg-card pl-9"
              />
            </label>
            <div
              role="tablist"
              aria-label="Filtro de la bandeja"
              className="flex border border-input"
            >
              {FILTROS.map((f, i) => {
                const activo = filtro === f.id;
                return (
                  <button
                    key={f.id}
                    type="button"
                    role="tab"
                    aria-selected={activo}
                    onClick={() => setFiltro(f.id)}
                    className={cn(
                      "flex h-8 flex-1 items-center justify-start px-2 text-[13px] font-medium outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring",
                      i > 0 && "border-l border-input",
                      activo
                        ? "bg-primary text-primary-foreground"
                        : "bg-card text-foreground hover:bg-gris-200",
                    )}
                  >
                    {f.label}
                  </button>
                );
              })}
            </div>
          </div>

          {error && (
            <div className="p-4">
              <Alert variant="destructive">
                <CircleAlertIcon />
                <AlertTitle>No se ha podido cargar la bandeja</AlertTitle>
                <AlertDescription className="flex flex-col gap-3">
                  <p>{mensajeDeError(error, "listar-tramites")}</p>
                  <Button type="button" variant="outline" size="sm" className="w-fit" onClick={recargar}>
                    Reintentar
                  </Button>
                </AlertDescription>
              </Alert>
            </div>
          )}

          <div className="min-h-0 flex-1 overflow-y-auto">
            {cargando &&
              Array.from({ length: 5 }, (_, i) => (
                <div key={i} className="h-[72px] animate-pulse border-b border-border bg-muted/40" />
              ))}
            {!cargando && !error && filtrados.length === 0 && (
              <p className="px-4 py-6 text-sm text-muted-foreground">
                {items.length === 0
                  ? "No hay trámites de WhatsApp entre los más recientes."
                  : "Ningún resultado con este filtro o búsqueda."}
              </p>
            )}
            {!cargando &&
              filtrados.map((tramite) => {
                const activo = tramite.id === seleccionadoId;
                const titulo =
                  tramite.explotacionNombre?.trim() ||
                  tramite.explotacionCodigoRega ||
                  `Trámite #${tramite.id}`;
                const preview =
                  etiquetaTipoTramite(tramite.tipoTramite ?? "") || "Sin tipo todavía";
                const st = presentacionEstadoTramite(tramite);
                return (
                  <button
                    key={tramite.id}
                    type="button"
                    onClick={() => setSeleccionadoId(tramite.id)}
                    className={cn(
                      "grid w-full grid-cols-[40px_minmax(0,1fr)_auto] gap-3 border-b border-border px-4 py-3.5 text-left outline-none transition-colors focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring",
                      activo ? "bg-gris-100" : "bg-card hover:bg-gris-100",
                    )}
                  >
                    <span
                      aria-hidden="true"
                      className="grid size-10 place-items-center bg-gris-200 text-[13px] font-bold"
                    >
                      {iniciales(titulo, "")}
                    </span>
                    <span className="flex min-w-0 flex-col gap-0.5">
                      <span
                        className={cn(
                          "truncate text-sm",
                          PENDIENTES.has(tramite.estado) ? "font-bold" : "font-medium",
                        )}
                      >
                        {titulo}
                      </span>
                      <span className="truncate text-[13px] text-muted-foreground">{preview}</span>
                      <span
                        className={cn(
                          "mt-1 inline-flex h-5 w-fit items-center px-1.5 text-[11px] font-medium",
                          st.variante === "success" && "bg-success text-success-foreground",
                          st.variante === "warning" && "bg-warning text-warning-foreground",
                          st.variante === "danger" && "bg-danger text-danger-foreground",
                          st.variante === "outline" && "bg-neutral text-neutral-foreground",
                        )}
                      >
                        {st.etiqueta}
                      </span>
                    </span>
                    <span className="text-xs text-muted-foreground tabular-nums">#{tramite.id}</span>
                  </button>
                );
              })}
          </div>
        </section>

        {/* Panel conversación */}
        <section
          aria-label="Conversación"
          className="flex min-h-[50vh] min-w-0 flex-col bg-background lg:min-h-0"
        >
          {!seleccionadoLista && !cargando && (
            <div className="flex flex-1 flex-col items-center justify-center gap-2 px-6 text-center text-sm text-muted-foreground">
              <MessageCircleIcon aria-hidden="true" className="size-8 text-gris-400" />
              <p>Selecciona un mensaje de la izquierda para verlo.</p>
            </div>
          )}

          {seleccionadoLista && (
            <>
              <header className="flex h-[72px] shrink-0 items-center gap-3 border-b-2 border-divisor bg-card px-5">
                <span
                  aria-hidden="true"
                  className="grid size-10 shrink-0 place-items-center bg-foreground text-[13px] font-bold text-background"
                >
                  {iniciales(
                    detalle?.whatsapp?.destinatarioNombre ||
                      seleccionadoLista.explotacionNombre ||
                      "WA",
                    "",
                  )}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-base font-bold">
                    {detalle?.whatsapp?.destinatarioNombre ||
                      seleccionadoLista.explotacionNombre ||
                      `Trámite #${seleccionadoLista.id}`}
                  </p>
                  <p className="flex items-center gap-1.5 truncate text-[13px] text-muted-foreground">
                    {detalle?.whatsapp?.telefonoEnmascarado ? (
                      <>
                        <PhoneIcon aria-hidden="true" className="size-3.5 shrink-0" />
                        {detalle.whatsapp.telefonoEnmascarado}
                      </>
                    ) : (
                      seleccionadoLista.explotacionCodigoRega ?? "WhatsApp"
                    )}
                  </p>
                </div>
                <BadgeEstadoTramite
                  estado={seleccionadoLista.estado}
                  motivoRechazo={seleccionadoLista.motivoRechazo}
                />
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="hidden h-8 gap-1.5 sm:inline-flex"
                  onClick={() => setRevisionId(seleccionadoLista.id)}
                >
                  <UserIcon aria-hidden="true" className="size-3.5" />
                  Revisar
                </Button>
              </header>

              <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto p-6">
                {cargandoDetalle && (
                  <div className="space-y-3" aria-hidden="true">
                    <div className="ml-auto h-16 w-2/3 animate-pulse bg-muted" />
                    <div className="h-20 w-2/3 animate-pulse bg-card" />
                  </div>
                )}
                {errorDetalle && (
                  <Alert variant="destructive">
                    <AlertTitle>No se ha podido cargar el mensaje</AlertTitle>
                    <AlertDescription>{mensajeDeError(errorDetalle, "detalle-tramite")}</AlertDescription>
                  </Alert>
                )}
                {!cargandoDetalle && !errorDetalle && detalle && (
                  <>
                    <p className="self-center bg-secondary px-2.5 py-0.5 text-xs font-medium text-gris-800">
                      Trámite #{detalle.id}
                      {detalle.tipoTramite
                        ? ` · ${etiquetaTipoTramite(detalle.tipoTramite)}`
                        : ""}
                    </p>
                    {detalle.mensajeOriginal ? (
                      <BurbujaEntrante texto={detalle.mensajeOriginal} />
                    ) : (
                      <p className="self-start max-w-[72%] border border-border bg-card px-3 py-2.5 text-sm text-muted-foreground">
                        Este trámite no tiene texto guardado (p. ej. caducó la retención o llegó sin
                        cuerpo).
                      </p>
                    )}
                    {detalle.whatsapp?.respuestas.map((r) => (
                      <BurbujaSaliente
                        key={r.id}
                        texto={r.texto}
                        estado={r.estado}
                        cuando={r.enviadoEn}
                        quien={r.enviadoPorNombre}
                      />
                    ))}
                  </>
                )}
              </div>

              <footer className="flex shrink-0 flex-wrap items-center justify-between gap-3 border-t-2 border-divisor bg-card px-5 py-3">
                <p className="text-[13px] text-muted-foreground">
                  Para responder por WhatsApp o corregir el trámite, ábrelo en revisión.
                </p>
                <Button type="button" className="h-9" onClick={() => setRevisionId(seleccionadoLista.id)}>
                  Revisar y responder
                </Button>
              </footer>
            </>
          )}
        </section>
      </div>

      <TramiteReviewDialog
        tramiteId={revisionId}
        onClose={() => setRevisionId(null)}
        onCambiado={recargar}
      />
    </div>
  );
}

function BurbujaEntrante({ texto }: { texto: string }) {
  return (
    <div className="max-w-[72%] self-start border border-border bg-card px-3 py-2.5">
      <p className="whitespace-pre-wrap text-sm leading-snug">{texto}</p>
    </div>
  );
}

function BurbujaSaliente({
  texto,
  estado,
  cuando,
  quien,
}: {
  texto: string;
  estado: string;
  cuando: string;
  quien: string | null;
}) {
  const { etiqueta } = presentacionRespuestaWhatsapp(estado);
  return (
    <div className="max-w-[72%] self-end bg-rojo-100 px-3 py-2.5">
      <p className="whitespace-pre-wrap text-sm leading-snug">{texto}</p>
      <p className="mt-1.5 text-[11px] text-muted-foreground">
        {[quien, horaMadrid(cuando) ?? fechaHoraMadrid(cuando), etiqueta].filter(Boolean).join(" · ")}
      </p>
    </div>
  );
}
