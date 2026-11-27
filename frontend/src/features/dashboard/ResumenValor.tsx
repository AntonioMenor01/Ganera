import { useEffect, useState } from "react";
import { CircleCheckBigIcon } from "lucide-react";
import { contarGanaderos, contarTramitesPorEstado, contarTramitesTotales } from "@/features/informes/api";

interface Datos {
  hechos: number | null;
  gestionados: number | null;
  ganaderos: number | null;
}

/**
 * Tarjeta de valor (idea #5, gestoría): recuerda de un vistazo lo que Ganera ya ha hecho por la
 * gestoría —trámites dejados hechos en OVZ y total gestionado— para reforzar el valor y reducir la
 * cancelación. SOLO datos reales (conteos de endpoints existentes): nunca estima "horas ahorradas"
 * ni inventa nada (regla de honestidad). Un conteo que falle se muestra como «—», sin romper nada.
 */
export function ResumenValor() {
  const [datos, setDatos] = useState<Datos>({ hechos: null, gestionados: null, ganaderos: null });
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    Promise.allSettled([
      contarTramitesPorEstado("HECHO_EN_OVZ"),
      contarTramitesTotales(),
      contarGanaderos(),
    ]).then(([hechosRes, totalRes, ganRes]) => {
      if (cancelado) return;
      setDatos({
        hechos: hechosRes.status === "fulfilled" ? hechosRes.value : null,
        gestionados: totalRes.status === "fulfilled" ? totalRes.value : null,
        ganaderos: ganRes.status === "fulfilled" ? ganRes.value : null,
      });
      setCargando(false);
    });
    return () => {
      cancelado = true;
    };
  }, []);

  return (
    <section
      aria-labelledby="resumen-valor-titulo"
      className="flex flex-col gap-4 border border-border bg-card p-5 sm:flex-row sm:items-center sm:justify-between"
    >
      <div className="flex items-start gap-3">
        <span className="grid size-10 shrink-0 place-items-center bg-success text-success-foreground">
          <CircleCheckBigIcon aria-hidden="true" className="size-5" />
        </span>
        <div>
          <h2 id="resumen-valor-titulo" className="text-sm font-bold">
            Tu actividad en Ganera
          </h2>
          <p className="mt-0.5 text-[13px] text-muted-foreground">
            {cargando
              ? "Cargando…"
              : "Lo que Ganera ya ha dejado hecho y gestionado para tu gestoría."}
          </p>
        </div>
      </div>
      <dl className="grid grid-cols-3 gap-6 sm:gap-8">
        <Metrica etiqueta="Hechos en OVZ" valor={datos.hechos} cargando={cargando} destacado />
        <Metrica etiqueta="Trámites gestionados" valor={datos.gestionados} cargando={cargando} />
        <Metrica etiqueta="Ganaderos" valor={datos.ganaderos} cargando={cargando} />
      </dl>
    </section>
  );
}

function Metrica({
  etiqueta,
  valor,
  cargando,
  destacado = false,
}: {
  etiqueta: string;
  valor: number | null;
  cargando: boolean;
  destacado?: boolean;
}) {
  return (
    <div className="flex flex-col gap-1">
      <dd
        className={`text-2xl leading-none font-bold tabular-nums ${destacado ? "text-primary" : ""}`}
        aria-live="polite"
      >
        {cargando ? (
          <span aria-hidden="true" className="inline-block h-6 w-10 animate-pulse bg-muted align-middle" />
        ) : valor === null ? (
          <span title="No disponible ahora mismo">—</span>
        ) : (
          valor
        )}
      </dd>
      <dt className="text-[12px] text-muted-foreground">{etiqueta}</dt>
    </div>
  );
}
