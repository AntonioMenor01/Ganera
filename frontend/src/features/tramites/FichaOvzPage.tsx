import { useCallback, useEffect, useId, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { CheckIcon, ChevronDown, CircleAlertIcon, EyeIcon, InfoIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  TEXTO_TRAMITE_NO_ENCONTRADO,
  aErrorApi,
  esCancelacion,
  mensajeDeError,
  type ErrorApi,
} from "@/shared/api/errores";
import { LogoGanera } from "@/shared/brand/LogoGanera";
import { BotonCopiar } from "@/shared/ui/BotonCopiar";
import { CLASE_ENLACE } from "@/shared/ui/enlace";
import { BadgeEstadoTramite } from "./BadgesTramite";
import { obtenerDetalleTramite } from "./api";
import {
  PISTA_EXPLOTACION,
  construirFichaOvz,
  modoFicha,
  type BloqueFicha,
  type CampoFicha,
  type FichaOvz,
} from "./fichaOvz";
import type { TramiteDetalle } from "./types";

/**
 * Ficha para OVZ (plan de la ficha OVZ, T5; shape aprobado por Antonio el 2026-10-07). Ventana
 * auxiliar que el gestor pone al lado de OVZ: los datos del trámite en el orden del formulario de
 * OVZ, con Copiar en cada campo. Ganera no envía nada a OVZ (3c no existe): solo prepara lo que el
 * gestor pega. Sin la barra de navegación de la app (va fuera de `AppLayout`, dentro de
 * `RequireAuth`). El orden, las etiquetas y las pistas salen de `fichaOvz.ts`; el estado decide si se
 * puede copiar (`modoFicha`, D4): solo `APROBADO`.
 */

type Vista =
  | { estado: "cargando" }
  | { estado: "error"; error: ErrorApi }
  | { estado: "no-encontrado" }
  | { estado: "listo"; detalle: TramiteDetalle };

/** Resultado de una carga atado al id que se pidió (mismo patrón que la ficha del Ganadero). */
type Carga = Vista & { id: number };

/** Solo un entero positivo es un id: lo demás es "no encontrado" sin preguntar al backend. */
function idDeRuta(valor: string | undefined): number | null {
  if (!valor || !/^[1-9]\d*$/.test(valor)) return null;
  const id = Number(valor);
  return Number.isSafeInteger(id) ? id : null;
}

export function FichaOvzPage() {
  const { id: idParam } = useParams();
  const id = idDeRuta(idParam);
  const [carga, setCarga] = useState<Carga | null>(null);
  const [intento, setIntento] = useState(0);
  const reintentar = useCallback(() => setIntento((n) => n + 1), []);

  // D3: la ventana se abrió con opener para heredar la sesión; aquí se corta el vínculo.
  useEffect(() => {
    try {
      window.opener = null;
    } catch {
      // Algún navegador no deja asignarlo: no pasa nada, la página es de nuestro mismo origen.
    }
  }, []);

  // Con varias ventanas abiertas (OVZ, la cola, la ficha), el título dice cuál es.
  useEffect(() => {
    if (id === null) return;
    const anterior = document.title;
    document.title = `Ficha OVZ · #${id}`;
    return () => {
      document.title = anterior;
    };
  }, [id]);

  useEffect(() => {
    if (id === null) return;
    const controlador = new AbortController();
    setCarga({ id, estado: "cargando" });
    obtenerDetalleTramite(id, controlador.signal)
      .then((detalle) => setCarga({ id, estado: "listo", detalle }))
      .catch((err: unknown) => {
        if (esCancelacion(err)) return;
        const error = aErrorApi(err);
        // 404: de otra gestoría o inexistente, indistinguibles a propósito.
        setCarga(error.tipo === "no-encontrado" ? { id, estado: "no-encontrado" } : { id, estado: "error", error });
      });
    return () => controlador.abort();
  }, [id, intento]);

  const vista: Vista =
    id === null ? { estado: "no-encontrado" } : carga && carga.id === id ? carga : { estado: "cargando" };

  return (
    <div className="flex min-h-screen flex-col bg-background text-foreground">
      <header className="sticky top-0 z-10 border-b border-sidebar-border bg-sidebar px-4 py-2.5 text-sidebar-foreground">
        <div className="mx-auto flex w-full max-w-xl items-center gap-2">
          <LogoGanera decorativo className="size-5 text-marca" />
          <span className="text-sm font-semibold text-foreground">Ficha para OVZ</span>
          {id !== null && (
            <span className="ml-auto flex items-center gap-2">
              <span className="text-sm text-muted-foreground tabular-nums">{`#${id}`}</span>
              {vista.estado === "listo" && <BadgeEstadoTramite estado={vista.detalle.estado} />}
            </span>
          )}
        </div>
      </header>

      <main className="mx-auto flex w-full max-w-xl flex-col gap-5 px-4 py-5">
        {vista.estado === "cargando" && <FichaCargando />}

        {vista.estado === "no-encontrado" && (
          <SinFicha titulo="Trámite no encontrado" texto={TEXTO_TRAMITE_NO_ENCONTRADO} />
        )}

        {vista.estado === "error" && (
          <Alert variant="destructive">
            <CircleAlertIcon />
            <AlertTitle>No se ha podido cargar la ficha</AlertTitle>
            <AlertDescription>
              <p>{mensajeDeError(vista.error, "detalle-tramite")}</p>
              <Button variant="outline" size="sm" onClick={reintentar} className="mt-2">
                Reintentar
              </Button>
            </AlertDescription>
          </Alert>
        )}

        {vista.estado === "listo" && <Contenido detalle={vista.detalle} />}
      </main>
    </div>
  );
}

function FichaCargando() {
  return (
    <div role="status" className="flex flex-col gap-5">
      <span className="sr-only">Cargando la ficha…</span>
      <div aria-hidden="true" className="flex flex-col gap-2">
        <div className="h-6 w-48 max-w-full rounded-sm bg-muted motion-safe:animate-pulse" />
        <div className="h-4 w-64 max-w-full rounded-sm bg-muted motion-safe:animate-pulse" />
      </div>
      <div aria-hidden="true" className="h-48 rounded-xl bg-card ring-1 ring-foreground/10" />
    </div>
  );
}

/** Estados sin ficha: título, una frase y la vuelta a la cola (patrón "Not found" de DESIGN.md). */
function SinFicha({ titulo, texto }: { titulo: string; texto: string }) {
  return (
    <div className="flex flex-col gap-1">
      <h1 className="text-xl font-extrabold">{titulo}</h1>
      <p className="text-sm text-muted-foreground">{texto}</p>
      <Link to="/tramites" className={cn("mt-2 w-fit", CLASE_ENLACE)}>
        Volver a trámites
      </Link>
    </div>
  );
}

function Contenido({ detalle }: { detalle: TramiteDetalle }) {
  const modo = modoFicha(detalle.estado);
  if (modo === "rechazado") {
    return <SinFicha titulo="Trámite rechazado" texto="Un trámite rechazado no se pasa a OVZ." />;
  }
  if (modo === "extraccion") {
    return (
      <SinFicha
        titulo="Extracción en curso"
        texto="Ganera aún está leyendo el mensaje. Cuando acabe, revisa y aprueba el trámite para tener su ficha."
      />
    );
  }
  if (modo === "no-disponible") {
    return <SinFicha titulo="Ficha no disponible" texto="Ganera no prepara la ficha de un trámite en este estado." />;
  }

  const resultado = construirFichaOvz(detalle);
  switch (resultado.tipo) {
    case "sin-tipo":
      return (
        <SinFicha
          titulo="Falta el tipo de trámite"
          texto="Asígnalo en la revisión del trámite para ver su ficha."
        />
      );
    case "tipo-desconocido":
      return (
        <SinFicha
          titulo="Ficha no disponible"
          texto={`Ganera aún no tiene ficha para este tipo de trámite (${resultado.valor}).`}
        />
      );
    case "sin-preparar":
      return (
        <SinFicha
          titulo={resultado.formulario}
          texto="Ganera aún no prepara esta ficha (las líneas de categoría, raza y cantidad). Rellénala directamente en OVZ."
        />
      );
    case "ficha":
      return (
        <Ficha
          ficha={resultado.ficha}
          copiable={modo === "copiable"}
          mensaje={detalle.mensajeOriginal}
        />
      );
  }
}

function Ficha({ ficha, copiable, mensaje }: { ficha: FichaOvz; copiable: boolean; mensaje: string | null }) {
  // Filas ya copiadas en esta ventana (solo en memoria: ni se guarda ni cambia el trámite).
  const [copiados, setCopiados] = useState<ReadonlySet<string>>(() => new Set());
  const marcarCopiado = useCallback((id: string) => {
    setCopiados((actual) => (actual.has(id) ? actual : new Set(actual).add(id)));
  }, []);

  const tieneCuenta = ficha.explotacion.valor !== null;
  const entrar: CampoFicha[] = [
    campoPagina("cuenta-nombre", "Titular", ficha.cuenta.nombre, tieneCuenta ? "Sin nombre en Ganera" : PISTA_EXPLOTACION),
    campoPagina("cuenta-nif", "NIF del titular", ficha.cuenta.nif, tieneCuenta ? "Sin NIF en Ganera" : PISTA_EXPLOTACION),
    ficha.explotacion,
  ];

  return (
    <>
      {!copiable && (
        <p className="flex items-start gap-2 rounded-lg bg-muted px-3 py-2 text-sm text-foreground">
          <EyeIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
          <span>Vista previa: aprueba el trámite antes de pasarlo a OVZ.</span>
        </p>
      )}

      <div className="flex flex-col gap-1">
        <h1 className="text-xl font-extrabold">{ficha.formulario}</h1>
        <p className="text-sm text-muted-foreground">
          En OVZ: <span className="text-foreground">{ficha.rutaOvz}</span>
        </p>
        <p className="text-sm text-muted-foreground">Para copiar en OVZ. Ganera no envía nada.</p>
        {ficha.avisos.length > 0 && (
          <ul aria-label="Avisos" className="mt-2 flex flex-col gap-1 text-sm text-muted-foreground">
            {ficha.avisos.map((aviso) => (
              <li key={aviso} className="flex items-start gap-2">
                <InfoIcon aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
                <span>{aviso}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {mensaje && <Mensaje texto={mensaje} />}

      <div className="flex flex-col gap-4">
        <Seccion
          bloque={{ id: "entrar", titulo: "Entrar en OVZ", campos: entrar }}
          copiable={copiable}
          copiables={new Set(["cuenta-nif", ficha.explotacion.id])}
          copiados={copiados}
          onCopiado={marcarCopiado}
        />
        {ficha.bloques.map((bloque) => (
          <Seccion
            key={bloque.id}
            bloque={bloque}
            copiable={copiable}
            copiados={copiados}
            onCopiado={marcarCopiado}
          />
        ))}
      </div>
    </>
  );
}

/** Campos de la página que no son del formulario de OVZ (la cuenta con la que entrar). */
function campoPagina(id: string, etiqueta: string, valor: string | null, pista: string): CampoFicha {
  const limpio = valor?.trim() ? valor.trim() : null;
  return { id, etiqueta, obligatorio: false, valor: limpio, pista: limpio === null ? pista : null, escrito: null };
}

function Mensaje({ texto }: { texto: string }) {
  const [abierto, setAbierto] = useState(true);
  const idPanel = useId();
  return (
    <div className="flex flex-col gap-1">
      <Button
        variant="ghost"
        size="sm"
        className="-mx-2.5 w-fit"
        aria-expanded={abierto}
        aria-controls={idPanel}
        onClick={() => setAbierto((a) => !a)}
      >
        Mensaje de WhatsApp
        <ChevronDown
          aria-hidden="true"
          className={cn(
            "size-4 transition-transform duration-200 motion-reduce:transition-none",
            abierto && "rotate-180",
          )}
        />
      </Button>
      <div id={idPanel} hidden={!abierto}>
        {abierto && (
          <blockquote className="rounded-lg bg-muted px-3 py-2 text-sm whitespace-pre-wrap break-words">
            {texto}
          </blockquote>
        )}
      </div>
    </div>
  );
}

function Seccion({
  bloque,
  copiable,
  copiables,
  copiados,
  onCopiado,
}: {
  bloque: BloqueFicha;
  copiable: boolean;
  /** Si se da, solo estos campos llevan Copiar (en "Entrar en OVZ", el nombre del titular no). */
  copiables?: ReadonlySet<string>;
  copiados: ReadonlySet<string>;
  onCopiado: (id: string) => void;
}) {
  const idTitulo = useId();
  return (
    <section aria-labelledby={idTitulo} className="rounded-xl bg-card ring-1 ring-foreground/10">
      <header className="border-b px-4 py-3">
        <h2 id={idTitulo} className="text-base font-semibold">
          {bloque.titulo}
        </h2>
      </header>
      <dl className="divide-y px-4">
        {bloque.campos.map((campo) => (
          <Fila
            key={campo.id}
            campo={campo}
            conBoton={copiable && (copiables ? copiables.has(campo.id) : true)}
            copiado={copiados.has(campo.id)}
            onCopiado={() => onCopiado(campo.id)}
          />
        ))}
      </dl>
    </section>
  );
}

/** Texto de lo que falta: la pista, y en un crotal sin completar, lo que se escribió. */
function textoFalta(campo: CampoFicha): string {
  return campo.escrito ? `${campo.pista} · escrito: ${campo.escrito}` : `Falta · ${campo.pista}`;
}

function Fila({
  campo,
  conBoton,
  copiado,
  onCopiado,
}: {
  campo: CampoFicha;
  conBoton: boolean;
  copiado: boolean;
  onCopiado: () => void;
}) {
  const { valor, escrito } = campo;
  return (
    <div className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 gap-y-0.5 py-2.5">
      <dt className="col-start-1 flex items-center gap-1.5 text-sm text-muted-foreground">
        {copiado && <CheckIcon aria-hidden="true" className="size-3.5 shrink-0 text-foreground" />}
        <span>{campo.etiqueta}</span>
        {campo.obligatorio && (
          <>
            {/* Tinta, no rojo: en Ganera el rojo sólido es para acciones (DESIGN.md, los dos rojos). */}
            <span aria-hidden="true" className="font-semibold text-foreground">
              *
            </span>
            <span className="sr-only">obligatorio</span>
          </>
        )}
        {copiado && <span className="sr-only">(copiado)</span>}
      </dt>
      <dd className="col-start-1 min-w-0">
        {valor !== null ? (
          <>
            <span className="block font-medium break-all tabular-nums">{valor}</span>
            {escrito && escrito !== valor && (
              <span className="block text-xs text-muted-foreground">escrito: {escrito}</span>
            )}
          </>
        ) : (
          <span className="text-sm text-muted-foreground">{textoFalta(campo)}</span>
        )}
      </dd>
      {conBoton && valor !== null && (
        <dd className="col-start-2 row-span-2 row-start-1 self-center">
          <BotonCopiar texto={valor} etiqueta={campo.etiqueta} onCopiado={onCopiado} />
        </dd>
      )}
    </div>
  );
}
