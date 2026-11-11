import { useState, type FormEvent } from "react";
import { CircleAlertIcon, Phone, PlusIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  TEXTO_ERROR_RED,
  TEXTO_ERROR_SERVIDOR,
  aErrorApi,
  type ErrorApi,
} from "@/shared/api/errores";
import { CLASE_ENLACE } from "@/shared/ui/enlace";
import { formatearTelefono, hrefTelefono } from "@/features/ganaderos/telefono";
import { ROLES_CONTACTO, etiquetaRolContacto } from "@/features/tramites/etiquetas";
import {
  crearContacto,
  desvincularExplotacion,
  listarTodosLosContactos,
  vincularExplotacion,
  type Contacto,
  type RolContacto,
} from "./api";
import { telefonoCanonico } from "./telefono";

/** La persona tal como la traen las fichas (ganadero y explotación): {contactoId, nombre, telefono, rol}. */
export interface PersonaExplotacion {
  contactoId: number;
  nombre: string;
  telefono: string;
  rol: string;
}

// Textos exactos (compartidos con los tests para que no haya deriva).
export const TEXTO_TELEFONO_409 =
  "No se puede usar este teléfono. Revísalo o contacta con soporte.";
export const TEXTO_VINCULO_404 = "No se ha podido, vuelve a cargar";
export const TEXTO_PARCIAL =
  "Contacto creado, pero no se ha podido vincular a la explotación";
export const TEXTO_SIN_CONTACTOS = "Sin contactos activos en esta explotación.";
const TEXTO_FALTA_TELEFONO = "El teléfono es obligatorio.";
const TEXTO_FALTA_NOMBRE = "El nombre es obligatorio para una persona nueva.";
const TEXTO_CONFLICTO_GENERICO = "No se ha podido completar la operación.";
const TEXTO_GENERICO = "Ha ocurrido un error. Inténtalo de nuevo.";

const ROLES = Object.entries(ROLES_CONTACTO) as [RolContacto, string][];
const ROL_POR_DEFECTO: RolContacto = "TITULAR";

type ErrorAlta =
  | { clase: "mensaje"; mensaje: string; reintentar?: () => void }
  | { clase: "parcial"; contactoId: number; detalle?: string };

/** Un 401 cierra la sesión por el interceptor de httpClient: aquí nunca se enseña como error. */
function esSesionCaducada(error: ErrorApi): boolean {
  return error.tipo === "no-autorizado";
}

function esReintentable(error: ErrorApi): boolean {
  return error.tipo === "red" || error.tipo === "servidor";
}

/** Mensaje de un fallo al enlazar/cambiar rol/quitar (POST o DELETE de explotaciones del contacto). */
function mensajeVinculo(error: ErrorApi): string {
  switch (error.tipo) {
    case "no-encontrado":
      return TEXTO_VINCULO_404;
    case "conflicto":
      return error.motivo ?? TEXTO_CONFLICTO_GENERICO; // contacto inactivo → motivo del backend
    case "red":
      return TEXTO_ERROR_RED;
    case "servidor":
      return TEXTO_ERROR_SERVIDOR;
    default:
      return error.motivo ?? TEXTO_GENERICO;
  }
}

/** Mensaje de un fallo al crear el contacto (POST /contactos). */
function mensajeCreacion(error: ErrorApi): string {
  switch (error.tipo) {
    case "conflicto":
      return TEXTO_TELEFONO_409; // texto fijo: nunca el motivo del backend
    case "validacion":
      return error.motivo ?? TEXTO_GENERICO; // 400 → el motivo del backend
    case "red":
      return TEXTO_ERROR_RED;
    case "servidor":
      return TEXTO_ERROR_SERVIDOR;
    default:
      return error.motivo ?? TEXTO_GENERICO;
  }
}

/**
 * «Personas de la explotación»: ver, añadir, quitar y cambiar el rol (titular/empleado) de los
 * contactos de una explotación. Reutilizado por la ficha de explotación y por cada explotación de
 * la ficha del ganadero. Las personas llegan por props (las fichas ya las traen); tras cada cambio
 * se llama a `onCambio` para que la ficha vuelva a cargarse. Una petición a la vez.
 */
export function PersonasDeExplotacion({
  explotacionId,
  contactos,
  onCambio,
  idEtiquetaLista,
}: {
  explotacionId: number;
  contactos: PersonaExplotacion[];
  onCambio: () => void;
  idEtiquetaLista?: string;
}) {
  const [enviando, setEnviando] = useState(false);
  const [errorFila, setErrorFila] = useState<{ mensaje: string; reintentar?: () => void } | null>(
    null,
  );

  const [anadiendoAbierto, setAnadiendoAbierto] = useState(false);
  const [telefono, setTelefono] = useState("");
  const [nombre, setNombre] = useState("");
  const [rolNuevo, setRolNuevo] = useState<RolContacto>(ROL_POR_DEFECTO);
  const [errorAlta, setErrorAlta] = useState<ErrorAlta | null>(null);

  function cerrarAlta() {
    setAnadiendoAbierto(false);
    setTelefono("");
    setNombre("");
    setRolNuevo(ROL_POR_DEFECTO);
    setErrorAlta(null);
  }

  // --- Operaciones sobre una fila existente (cambiar rol / quitar) ---------------------------

  async function cambiarRol(persona: PersonaExplotacion, rol: RolContacto) {
    if (enviando || rol === persona.rol) return;
    setEnviando(true);
    setErrorFila(null);
    try {
      await vincularExplotacion(persona.contactoId, explotacionId, rol);
      onCambio();
    } catch (err) {
      const error = aErrorApi(err);
      if (esSesionCaducada(error)) return;
      setErrorFila({
        mensaje: mensajeVinculo(error),
        reintentar: esReintentable(error) ? () => void cambiarRol(persona, rol) : undefined,
      });
    } finally {
      setEnviando(false);
    }
  }

  async function quitar(persona: PersonaExplotacion) {
    if (enviando) return;
    setEnviando(true);
    setErrorFila(null);
    try {
      await desvincularExplotacion(persona.contactoId, explotacionId);
      onCambio();
    } catch (err) {
      const error = aErrorApi(err);
      if (esSesionCaducada(error)) return;
      setErrorFila({
        mensaje: mensajeVinculo(error),
        reintentar: esReintentable(error) ? () => void quitar(persona) : undefined,
      });
    } finally {
      setEnviando(false);
    }
  }

  // --- Añadir persona -------------------------------------------------------------------------

  function enviarAlta(evento: FormEvent) {
    evento.preventDefault();
    if (enviando) return;
    const tel = telefono.trim();
    if (!tel) {
      setErrorAlta({ clase: "mensaje", mensaje: TEXTO_FALTA_TELEFONO });
      return;
    }
    void ejecutarAlta(tel, nombre.trim());
  }

  async function ejecutarAlta(tel: string, nom: string) {
    setEnviando(true);
    setErrorAlta(null);
    try {
      // 1) ¿Ya es contacto de la gestoría? Se busca en el listado completo (filtrado en el cliente).
      let existentes: Contacto[];
      try {
        existentes = await listarTodosLosContactos(false);
      } catch (err) {
        const error = aErrorApi(err);
        if (esSesionCaducada(error)) return;
        setErrorAlta({
          clase: "mensaje",
          mensaje: mensajeCreacion(error),
          reintentar: esReintentable(error) ? () => void ejecutarAlta(tel, nom) : undefined,
        });
        return;
      }
      const existente = existentes.find(
        (c) => telefonoCanonico(c.telefono) === telefonoCanonico(tel),
      );

      if (existente) {
        // 2a) Existe → solo el enlace.
        try {
          await vincularExplotacion(existente.id, explotacionId, rolNuevo);
        } catch (err) {
          const error = aErrorApi(err);
          if (esSesionCaducada(error)) return;
          setErrorAlta({
            clase: "mensaje",
            mensaje: mensajeVinculo(error),
            reintentar: esReintentable(error) ? () => void ejecutarAlta(tel, nom) : undefined,
          });
          return;
        }
        cerrarAlta();
        onCambio();
        return;
      }

      // 2b) Nuevo → crear y luego enlazar. El nombre es obligatorio para una persona nueva.
      if (!nom) {
        setErrorAlta({ clase: "mensaje", mensaje: TEXTO_FALTA_NOMBRE });
        return;
      }
      let contactoId: number;
      try {
        const creado = await crearContacto(tel, nom);
        contactoId = creado.id;
      } catch (err) {
        const error = aErrorApi(err);
        if (esSesionCaducada(error)) return;
        setErrorAlta({
          clase: "mensaje",
          mensaje: mensajeCreacion(error),
          reintentar: esReintentable(error) ? () => void ejecutarAlta(tel, nom) : undefined,
        });
        return;
      }
      // Creado: a partir de aquí un fallo del enlace es el caso «a medias» (nunca recrear → daría 409).
      try {
        await vincularExplotacion(contactoId, explotacionId, rolNuevo);
      } catch (err) {
        const error = aErrorApi(err);
        if (esSesionCaducada(error)) return;
        setErrorAlta({ clase: "parcial", contactoId });
        return;
      }
      cerrarAlta();
      onCambio();
    } finally {
      setEnviando(false);
    }
  }

  // «Reintentar vínculo»: repite SOLO el enlace del contacto ya creado.
  async function reintentarVinculo(contactoId: number) {
    if (enviando) return;
    setEnviando(true);
    try {
      await vincularExplotacion(contactoId, explotacionId, rolNuevo);
      cerrarAlta();
      onCambio();
    } catch (err) {
      const error = aErrorApi(err);
      if (esSesionCaducada(error)) return;
      // Se mantiene el estado «a medias» (el contacto sigue creado) y se detalla por qué no enlazó.
      setErrorAlta({ clase: "parcial", contactoId, detalle: mensajeVinculo(error) });
    } finally {
      setEnviando(false);
    }
  }

  // --- Render ---------------------------------------------------------------------------------

  return (
    <div className="flex flex-col gap-3">
      {errorFila && (
        <Alert variant="destructive">
          <CircleAlertIcon />
          <AlertDescription className="flex flex-col gap-2">
            <p>{errorFila.mensaje}</p>
            {errorFila.reintentar && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="w-fit"
                disabled={enviando}
                onClick={errorFila.reintentar}
              >
                Reintentar
              </Button>
            )}
          </AlertDescription>
        </Alert>
      )}

      {contactos.length === 0 ? (
        <p className="text-sm text-muted-foreground">{TEXTO_SIN_CONTACTOS}</p>
      ) : (
        <ul aria-labelledby={idEtiquetaLista} className="divide-y">
          {contactos.map((persona) => (
            <FilaPersona
              key={persona.contactoId}
              explotacionId={explotacionId}
              persona={persona}
              deshabilitado={enviando}
              onCambiarRol={(rol) => void cambiarRol(persona, rol)}
              onQuitar={() => void quitar(persona)}
            />
          ))}
        </ul>
      )}

      {anadiendoAbierto ? (
        <form
          onSubmit={enviarAlta}
          noValidate
          aria-label="Añadir persona a la explotación"
          className="flex flex-col gap-3 border border-border bg-card p-4"
        >
          <p className="text-sm font-semibold">Añadir persona</p>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`persona-tel-${explotacionId}`}>Teléfono</Label>
              <Input
                id={`persona-tel-${explotacionId}`}
                value={telefono}
                onChange={(e) => setTelefono(e.target.value)}
                inputMode="tel"
                autoComplete="off"
                placeholder="+34…"
                disabled={enviando}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`persona-nombre-${explotacionId}`}>Nombre</Label>
              <Input
                id={`persona-nombre-${explotacionId}`}
                value={nombre}
                onChange={(e) => setNombre(e.target.value)}
                autoComplete="off"
                placeholder="Nombre de la persona"
                disabled={enviando}
              />
            </div>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`persona-rol-${explotacionId}`}>Rol</Label>
            <select
              id={`persona-rol-${explotacionId}`}
              value={rolNuevo}
              onChange={(e) => setRolNuevo(e.target.value as RolContacto)}
              disabled={enviando}
              className="h-9 w-full max-w-[14rem] border border-input bg-card px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
            >
              {ROLES.map(([valor, etiqueta]) => (
                <option key={valor} value={valor}>
                  {etiqueta}
                </option>
              ))}
            </select>
          </div>
          <p className="text-xs text-muted-foreground">
            Si el teléfono ya es un contacto, con el teléfono basta; el nombre solo hace falta para
            una persona nueva.
          </p>

          {errorAlta?.clase === "mensaje" && (
            <Alert variant="destructive">
              <CircleAlertIcon />
              <AlertDescription className="flex flex-col gap-2">
                <p>{errorAlta.mensaje}</p>
                {errorAlta.reintentar && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="w-fit"
                    disabled={enviando}
                    onClick={errorAlta.reintentar}
                  >
                    Reintentar
                  </Button>
                )}
              </AlertDescription>
            </Alert>
          )}

          {errorAlta?.clase === "parcial" && (
            <Alert variant="destructive">
              <CircleAlertIcon />
              <AlertDescription className="flex flex-col gap-2">
                <p>{TEXTO_PARCIAL}</p>
                {errorAlta.detalle && (
                  <p className="text-xs text-muted-foreground">{errorAlta.detalle}</p>
                )}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="w-fit"
                  disabled={enviando}
                  onClick={() => void reintentarVinculo(errorAlta.contactoId)}
                >
                  Reintentar vínculo
                </Button>
              </AlertDescription>
            </Alert>
          )}

          <div className="flex gap-2">
            <Button type="submit" size="sm" disabled={enviando}>
              {enviando ? "Añadiendo…" : "Añadir"}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={enviando}
              onClick={cerrarAlta}
            >
              Cancelar
            </Button>
          </div>
        </form>
      ) : (
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="w-fit"
          disabled={enviando}
          onClick={() => {
            setErrorFila(null);
            setAnadiendoAbierto(true);
          }}
        >
          <PlusIcon aria-hidden="true" className="size-3.5" />
          Añadir persona
        </Button>
      )}
    </div>
  );
}

function FilaPersona({
  explotacionId,
  persona,
  deshabilitado,
  onCambiarRol,
  onQuitar,
}: {
  explotacionId: number;
  persona: PersonaExplotacion;
  deshabilitado: boolean;
  onCambiarRol: (rol: RolContacto) => void;
  onQuitar: () => void;
}) {
  const rolConocido = persona.rol in ROLES_CONTACTO;
  const idRol = `rol-${explotacionId}-${persona.contactoId}`;
  return (
    <li className="flex flex-col gap-2 py-3 text-sm sm:flex-row sm:items-center sm:gap-4">
      <span className="min-w-0 flex-1 break-words font-medium">{persona.nombre}</span>
      <a
        href={hrefTelefono(persona.telefono)}
        className={cn(
          "inline-flex w-fit items-center gap-1.5 tabular-nums sm:w-44",
          CLASE_ENLACE,
        )}
      >
        <Phone aria-hidden="true" className="size-3.5" />
        {formatearTelefono(persona.telefono)}
      </a>
      <div className="flex items-center gap-2">
        <label className="sr-only" htmlFor={idRol}>
          Rol de {persona.nombre}
        </label>
        <select
          id={idRol}
          value={persona.rol}
          disabled={deshabilitado}
          onChange={(e) => onCambiarRol(e.target.value as RolContacto)}
          className="h-8 border border-input bg-card px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
        >
          {/* Un rol que el frontend no conozca se muestra tal cual para no perderlo. */}
          {!rolConocido && <option value={persona.rol}>{etiquetaRolContacto(persona.rol)}</option>}
          {ROLES.map(([valor, etiqueta]) => (
            <option key={valor} value={valor}>
              {etiqueta}
            </option>
          ))}
        </select>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={deshabilitado}
          aria-label={`Quitar a ${persona.nombre} de la explotación`}
          onClick={onQuitar}
        >
          Quitar
        </Button>
      </div>
    </li>
  );
}
