import { useCallback, useEffect, useRef, useState, type MouseEvent } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { ArrowLeft, ChevronDown, CircleAlertIcon, Phone } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  TEXTO_GANADERO_NO_ENCONTRADO,
  aErrorApi,
  esCancelacion,
  mensajeDeError,
  type ErrorApi,
} from "@/shared/api/errores";
import { BadgeRolContacto } from "@/features/tramites/BadgesTramite";
import { obtenerGanadero } from "./api";
import type { ContactoDeExplotacion, ExplotacionDeGanadero, GanaderoDetalle } from "./types";
import { formatearTelefono, hrefTelefono } from "./telefono";
import { CLASE_ENLACE } from "@/shared/ui/enlace";
import { AnimalesDeExplotacion } from "@/features/explotaciones/AnimalesDeExplotacion";

/** Con más explotaciones que esto, la ficha abre con un índice de enlaces a cada sección. */
const MAX_EXPLOTACIONES_SIN_INDICE = 3;

type Vista =
  | { estado: "cargando" }
  | { estado: "error"; error: ErrorApi }
  | { estado: "no-encontrado" }
  | { estado: "listo"; ganadero: GanaderoDetalle };

/** Resultado de una carga, atado al id que se pidió (I1): React Router reutiliza esta pantalla al
 * pasar de /ganaderos/1 a /ganaderos/2, y sin el id el primer render de 2 enseñaría la ficha de 1. */
type Carga = Vista & { id: number };

/** Solo un entero positivo es un id: lo demás es "no encontrado" sin preguntar al backend. */
function idDeRuta(valor: string | undefined): number | null {
  if (!valor || !/^[1-9]\d*$/.test(valor)) return null;
  const id = Number(valor);
  return Number.isSafeInteger(id) ? id : null;
}

function textoExplotaciones(total: number): string {
  return `${total} ${total === 1 ? "explotación" : "explotaciones"}`;
}

const idSeccion = (explotacionId: number) => `explotacion-${explotacionId}`;
const idTituloSeccion = (explotacionId: number) => `explotacion-${explotacionId}-titulo`;

export function GanaderoDetallePage() {
  const { id: idParam } = useParams();
  const id = idDeRuta(idParam);
  const [carga, setCarga] = useState<Carga | null>(null);
  const [intento, setIntento] = useState(0);

  const reintentar = useCallback(() => setIntento((n) => n + 1), []);

  useEffect(() => {
    if (id === null) return;
    const controlador = new AbortController();
    setCarga({ id, estado: "cargando" });
    obtenerGanadero(id, controlador.signal)
      .then((ganadero) => setCarga({ id, estado: "listo", ganadero }))
      .catch((err: unknown) => {
        // Una petición cancelada (cambio de id, desmontaje, StrictMode) no es un fallo que enseñar.
        if (esCancelacion(err)) return;
        const error = aErrorApi(err);
        // 404: de otra gestoría o inexistente, indistinguibles a propósito. Nunca un error genérico.
        setCarga(
          error.tipo === "no-encontrado"
            ? { id, estado: "no-encontrado" }
            : { id, estado: "error", error },
        );
      });
    return () => controlador.abort();
  }, [id, intento]);

  // Lo cargado solo vale para el id que se pidió; mientras no coincida, se está cargando.
  const vista: Vista =
    id === null
      ? { estado: "no-encontrado" }
      : carga && carga.id === id
        ? carga
        : { estado: "cargando" };

  return (
    <div className="flex min-w-0 flex-col gap-6">
      {/* En "no encontrado" la única acción es "Volver a Ganaderos", bajo el mensaje. */}
      {vista.estado !== "no-encontrado" && (
        <Link
          to="/ganaderos"
          className={cn("inline-flex w-fit items-center gap-1 text-sm", CLASE_ENLACE)}
        >
          <ArrowLeft aria-hidden="true" className="size-3.5" />
          Ganaderos
        </Link>
      )}

      {vista.estado === "cargando" && <FichaCargando />}

      {vista.estado === "no-encontrado" && (
        <div className="flex flex-col gap-1">
          <h1 className="text-xl font-extrabold">Ganadero no encontrado</h1>
          <p className="text-sm text-muted-foreground">{TEXTO_GANADERO_NO_ENCONTRADO}</p>
          <Link to="/ganaderos" className={cn("mt-2 w-fit", CLASE_ENLACE)}>
            Volver a Ganaderos
          </Link>
        </div>
      )}

      {vista.estado === "error" && (
        <Alert variant="destructive">
          <CircleAlertIcon />
          <AlertTitle>No se ha podido cargar el ganadero</AlertTitle>
          <AlertDescription>
            <p>{mensajeDeError(vista.error, "detalle-ganadero")}</p>
            <Button variant="outline" size="sm" onClick={reintentar} className="mt-2">
              Reintentar
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {vista.estado === "listo" && <Ficha ganadero={vista.ganadero} />}
    </div>
  );
}

function FichaCargando() {
  return (
    <div role="status" className="flex flex-col gap-6">
      <span className="sr-only">Cargando ganadero…</span>
      <div aria-hidden="true" className="flex flex-col gap-2">
        <div className="h-6 w-56 max-w-full rounded-sm bg-muted motion-safe:animate-pulse" />
        <div className="h-4 w-28 rounded-sm bg-muted motion-safe:animate-pulse" />
      </div>
      <div aria-hidden="true" className="h-40 rounded-xl bg-card ring-1 ring-foreground/10" />
    </div>
  );
}

function Ficha({ ganadero }: { ganadero: GanaderoDetalle }) {
  const { explotaciones } = ganadero;
  const { hash } = useLocation();
  const hashAtendido = useRef(false);

  // m3: con #explotacion-N en la URL (un enlace del índice copiado o abierto en otra pestaña), el
  // navegador intentó saltar antes de que llegaran los datos. Se hace aquí, una vez.
  useEffect(() => {
    if (hashAtendido.current) return;
    hashAtendido.current = true;
    const explotacionId = /^#explotacion-(\d+)$/.exec(hash)?.[1];
    if (explotacionId) irASeccion(Number(explotacionId));
  }, [hash]);

  return (
    <>
      <div className="min-w-0">
        <div className="flex flex-wrap items-baseline gap-x-3 gap-y-0.5">
          <h1 className="min-w-0 text-xl font-extrabold break-words">{ganadero.nombre}</h1>
          {ganadero.nif ? (
            <span className="text-muted-foreground tabular-nums">
              <span className="sr-only">NIF </span>
              {ganadero.nif}
            </span>
          ) : (
            <span className="text-muted-foreground">Sin NIF</span>
          )}
        </div>
        <p className="text-sm text-muted-foreground tabular-nums">{textoExplotaciones(explotaciones.length)}</p>
      </div>

      {explotaciones.length > MAX_EXPLOTACIONES_SIN_INDICE && (
        <IndiceExplotaciones explotaciones={explotaciones} />
      )}

      {explotaciones.length === 0 ? (
        <div className="text-muted-foreground">
          <p>Este ganadero no tiene explotaciones. Se añaden al importar el Excel.</p>
          <Link to="/explotaciones" className={cn("mt-1 inline-block", CLASE_ENLACE)}>
            Ir a Explotaciones
          </Link>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {explotaciones.map((explotacion) => (
            <SeccionExplotacion key={explotacion.id} explotacion={explotacion} />
          ))}
        </div>
      )}
    </>
  );
}

/** Lleva la vista y el foco al título de una sección: quien navega con teclado sigue leyendo
 * desde ahí. scroll-mt deja aire arriba. false si la sección no existe. */
function irASeccion(explotacionId: number): boolean {
  const titulo = document.getElementById(idTituloSeccion(explotacionId));
  if (!titulo) return false;
  titulo.closest("section")?.scrollIntoView?.({ block: "start" });
  titulo.focus({ preventScroll: true });
  return true;
}

function IndiceExplotaciones({ explotaciones }: { explotaciones: ExplotacionDeGanadero[] }) {
  function alPulsar(evento: MouseEvent<HTMLAnchorElement>, explotacionId: number) {
    // m2: con modificadores o botón no principal (nueva pestaña/ventana) decide el navegador.
    if (evento.button !== 0) return;
    if (evento.ctrlKey || evento.metaKey || evento.shiftKey || evento.altKey) return;
    if (irASeccion(explotacionId)) evento.preventDefault();
  }

  return (
    <nav aria-label="Explotaciones de este ganadero">
      <ul className="flex flex-wrap gap-x-4 gap-y-1.5 text-sm">
        {explotaciones.map((explotacion) => (
          <li key={explotacion.id} className="min-w-0">
            <a
              href={`#${idSeccion(explotacion.id)}`}
              onClick={(evento) => alPulsar(evento, explotacion.id)}
              className={CLASE_ENLACE}
            >
              <span className="tabular-nums">{explotacion.codigoRega}</span> · {explotacion.nombre}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}

function SeccionExplotacion({ explotacion }: { explotacion: ExplotacionDeGanadero }) {
  const [animalesAbiertos, setAnimalesAbiertos] = useState(false);
  const idTitulo = idTituloSeccion(explotacion.id);
  const idContactos = `explotacion-${explotacion.id}-contactos`;
  const idPanelAnimales = `explotacion-${explotacion.id}-animales`;

  return (
    <section
      id={idSeccion(explotacion.id)}
      aria-labelledby={idTitulo}
      className="scroll-mt-6 rounded-xl bg-card ring-1 ring-foreground/10"
    >
      <header className="border-b px-4 py-3">
        <h2
          id={idTitulo}
          tabIndex={-1}
          className="rounded-sm text-base font-semibold break-words outline-none focus-visible:ring-3 focus-visible:ring-ring"
        >
          <span className="tabular-nums">{explotacion.codigoRega}</span>
          <span className="font-normal"> · {explotacion.nombre}</span>
        </h2>
      </header>

      <div className="px-4 py-3">
        <h3 id={idContactos} className="text-sm font-semibold text-muted-foreground">
          Contactos
        </h3>
        {explotacion.contactos.length === 0 ? (
          <p className="mt-1 text-sm text-muted-foreground">
            Sin contactos activos en esta explotación.
          </p>
        ) : (
          <ul aria-labelledby={idContactos} className="divide-y">
            {explotacion.contactos.map((contacto) => (
              <FilaContacto key={contacto.contactoId} contacto={contacto} />
            ))}
          </ul>
        )}
      </div>

      <div className="rounded-b-xl border-t bg-muted/50 px-4 py-2">
        <Button
          variant="ghost"
          size="sm"
          className="-mx-2.5"
          aria-expanded={animalesAbiertos}
          aria-controls={idPanelAnimales}
          onClick={() => setAnimalesAbiertos((abierto) => !abierto)}
        >
          Ver animales
          <ChevronDown
            aria-hidden="true"
            className={cn(
              "size-4 transition-transform duration-200 motion-reduce:transition-none",
              animalesAbiertos && "rotate-180",
            )}
          />
        </Button>
        <div id={idPanelAnimales} hidden={!animalesAbiertos} className="pt-1 pb-2">
          {/* Solo se monta desplegado: los animales se piden al abrir, y plegar cancela y desmonta. */}
          {animalesAbiertos && <AnimalesDeExplotacion explotacionId={explotacion.id} />}
        </div>
      </div>
    </section>
  );
}

function FilaContacto({ contacto }: { contacto: ContactoDeExplotacion }) {
  return (
    // Rejilla: los teléfonos quedan alineados en columna (no bailan con el ancho del badge) y
    // pegados al nombre en escritorio. En móvil, nombre | teléfono y el badge debajo.
    <li className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-1 py-2 text-sm sm:grid-cols-[minmax(0,18rem)_11rem_auto] sm:justify-start">
      <span className="min-w-0 break-words">{contacto.nombre}</span>
      <a
        href={hrefTelefono(contacto.telefono)}
        className={cn("inline-flex items-center gap-1.5 justify-self-start tabular-nums", CLASE_ENLACE)}
      >
        <Phone aria-hidden="true" className="size-3.5" />
        {formatearTelefono(contacto.telefono)}
      </a>
      <span className="col-span-2 justify-self-start sm:col-span-1">
        <BadgeRolContacto rol={contacto.rol} />
      </span>
    </li>
  );
}
