import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from "react";
import { Link } from "react-router-dom";
import {
  Building2Icon,
  CreditCardIcon,
  FileTextIcon,
  MessageCircleIcon,
  PlugIcon,
  ShieldCheckIcon,
  UserCogIcon,
  UsersRoundIcon,
  type LucideIcon,
} from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { aErrorApi, mensajeDeError, type ErrorApi } from "@/shared/api/errores";
import { useAuth } from "@/shared/auth/AuthContext";
import { CLASE_ENLACE } from "@/shared/ui/enlace";
import { obtenerEstadoSuscripcion } from "@/features/facturacion/api";
import type { SuscripcionEstado } from "@/features/facturacion/types";
import {
  actualizarContacto,
  crearContacto,
  desactivarContacto,
  listarContactos,
  reactivarContacto,
  type Contacto,
} from "@/features/contactos/api";

const TAMANIO = 50;

const ETIQUETA_SUSCRIPCION: Record<SuscripcionEstado["estado"], string> = {
  TRIAL: "Prueba",
  TRIAL_EXPIRADO_SIN_PAGO: "Prueba terminada sin pago",
  ACTIVA: "Activa",
  IMPAGO_GRACIA: "Impago (periodo de gracia)",
  SUSPENDIDA: "Suspendida",
  CANCELADA: "Cancelada",
};

type SeccionConfig =
  | "gestoria"
  | "equipo"
  | "ovz"
  | "whatsapp"
  | "plantillas"
  | "suscripcion"
  | "cuenta";

const SECCIONES: { id: SeccionConfig; label: string; Icono: LucideIcon }[] = [
  { id: "gestoria", label: "Gestoría", Icono: Building2Icon },
  { id: "equipo", label: "Equipo", Icono: UsersRoundIcon },
  { id: "ovz", label: "OVZ.net (Cañada)", Icono: ShieldCheckIcon },
  { id: "whatsapp", label: "WhatsApp", Icono: MessageCircleIcon },
  { id: "plantillas", label: "Plantillas de trámite", Icono: FileTextIcon },
  { id: "suscripcion", label: "Suscripción", Icono: CreditCardIcon },
  { id: "cuenta", label: "Cuenta", Icono: UserCogIcon },
];

export function ConfiguracionPage() {
  const { usuario } = useAuth();
  const [seccion, setSeccion] = useState<SeccionConfig>("gestoria");
  const [suscripcion, setSuscripcion] = useState<SuscripcionEstado | null | undefined>(undefined);
  const [errorSuscripcion, setErrorSuscripcion] = useState(false);

  const [contactos, setContactos] = useState<Contacto[]>([]);
  const [errorContactos, setErrorContactos] = useState<ErrorApi | null>(null);
  const [cargandoContactos, setCargandoContactos] = useState(true);
  const [version, setVersion] = useState(0);
  const recargarContactos = useCallback(() => setVersion((v) => v + 1), []);

  const [nombreNuevo, setNombreNuevo] = useState("");
  const [telefonoNuevo, setTelefonoNuevo] = useState("");
  const [guardando, setGuardando] = useState(false);
  const [errorAlta, setErrorAlta] = useState<ErrorApi | null>(null);

  useEffect(() => {
    let cancelado = false;
    obtenerEstadoSuscripcion()
      .then((s) => {
        if (!cancelado) setSuscripcion(s);
      })
      .catch(() => {
        if (!cancelado) {
          setSuscripcion(null);
          setErrorSuscripcion(true);
        }
      });
    return () => {
      cancelado = true;
    };
  }, []);

  useEffect(() => {
    let cancelado = false;
    setCargandoContactos(true);
    setErrorContactos(null);
    listarContactos(0, TAMANIO, true)
      .then((pagina) => {
        if (cancelado) return;
        setContactos(pagina.content);
        setCargandoContactos(false);
      })
      .catch((err) => {
        if (cancelado) return;
        setContactos([]);
        setErrorContactos(aErrorApi(err));
        setCargandoContactos(false);
      });
    return () => {
      cancelado = true;
    };
  }, [version]);

  async function onAlta(evento: FormEvent) {
    evento.preventDefault();
    setGuardando(true);
    setErrorAlta(null);
    try {
      await crearContacto(telefonoNuevo, nombreNuevo);
      setNombreNuevo("");
      setTelefonoNuevo("");
      recargarContactos();
    } catch (err) {
      setErrorAlta(aErrorApi(err));
    } finally {
      setGuardando(false);
    }
  }

  async function onDesactivar(id: number) {
    try {
      await desactivarContacto(id);
      recargarContactos();
    } catch (err) {
      setErrorContactos(aErrorApi(err));
    }
  }

  async function onReactivar(id: number) {
    try {
      await reactivarContacto(id);
      recargarContactos();
    } catch (err) {
      setErrorContactos(aErrorApi(err));
    }
  }

  async function onGuardarEdicion(contacto: Contacto, nombre: string, telefono: string) {
    try {
      await actualizarContacto(contacto.id, telefono, nombre);
      recargarContactos();
    } catch (err) {
      setErrorContactos(aErrorApi(err));
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-[1360px] min-w-0 flex-col gap-6">
      <div className="border-b-2 border-divisor pb-5">
        <h1 className="text-[28px] leading-tight font-bold tracking-[-0.02em]">Configuración</h1>
      </div>

      <div className="grid gap-8 lg:grid-cols-[240px_minmax(0,1fr)] lg:items-start">
        <nav aria-label="Secciones de configuración" className="flex flex-col gap-0.5">
          {SECCIONES.map(({ id, label, Icono }) => {
            const activo = seccion === id;
            return (
              <button
                key={id}
                type="button"
                onClick={() => setSeccion(id)}
                className={cn(
                  "flex h-10 items-center gap-2.5 border px-3 text-left text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring",
                  activo
                    ? "border-border bg-card font-extrabold"
                    : "border-transparent font-semibold hover:bg-card",
                )}
              >
                <Icono aria-hidden="true" className="size-4 shrink-0" />
                {label}
              </button>
            );
          })}
        </nav>

        <div className="min-w-0">
          {seccion === "gestoria" && (
            <Panel titulo="Datos de la gestoría">
              <p className="mb-4 text-sm text-muted-foreground">
                La ficha de la gestoría aún no se edita desde la app. El inventario se carga en{" "}
                <Link to="/explotaciones" className={CLASE_ENLACE}>
                  Explotaciones
                </Link>
                .
              </p>
              <div className="grid gap-4 sm:grid-cols-2">
                <CampoSoloLectura etiqueta="Nombre o razón social" valor="—" />
                <CampoSoloLectura etiqueta="CIF" valor="—" />
                <div className="sm:col-span-2">
                  <CampoSoloLectura etiqueta="Dirección" valor="—" />
                </div>
              </div>
            </Panel>
          )}

          {seccion === "equipo" && (
            <Panel titulo="Equipo">
              <p className="mb-4 text-sm text-muted-foreground">
                Invitaciones y roles de empleados aún no tienen endpoint. Hoy solo hay tu usuario.
              </p>
              <ul className="divide-y divide-border border border-border">
                <li className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm">
                  <div className="flex items-center gap-3">
                    <span className="grid size-10 place-items-center bg-muted text-[13px] font-bold">
                      {iniciales(usuario?.nombre)}
                    </span>
                    <div>
                      <p className="font-medium">{usuario?.nombre ?? "—"}</p>
                      <p className="text-muted-foreground">{usuario?.email ?? "—"}</p>
                    </div>
                  </div>
                  <span className="inline-flex h-6 items-center bg-success px-2 text-xs font-medium text-success-foreground">
                    Activo
                  </span>
                </li>
              </ul>
              <Button type="button" variant="outline" className="mt-4 h-10 bg-card" disabled>
                Invitar compañero
              </Button>
            </Panel>
          )}

          {seccion === "ovz" && (
            <Panel titulo="OVZ.net (Cañada)">
              <div className="flex flex-wrap items-center gap-4 border-b-2 border-divisor pb-5">
                <span className="grid size-12 place-items-center bg-muted text-muted-foreground">
                  <PlugIcon aria-hidden="true" className="size-6" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-lg font-bold">Sin conectar</p>
                  <p className="text-[13px] text-muted-foreground">
                    Puedes revisar y aprobar trámites. Enviar a OVZ.net es escalón 2 (modo asistido):
                    las credenciales se guardarán cifradas más adelante.
                  </p>
                </div>
              </div>
              <div className="mt-5 grid gap-4 sm:grid-cols-2">
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="ovz-usuario">Usuario de OVZ.net</Label>
                  <Input id="ovz-usuario" disabled placeholder="Pendiente de escalón 2" />
                </div>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="ovz-pass">Contraseña de OVZ.net</Label>
                  <Input id="ovz-pass" type="password" disabled placeholder="••••••••" />
                </div>
              </div>
              <Button type="button" className="mt-4 h-10" disabled>
                Probar conexión
              </Button>
            </Panel>
          )}

          {seccion === "whatsapp" && (
            <div className="flex flex-col gap-5">
              <div className="flex flex-wrap items-center gap-4 border border-border bg-card p-5">
                <span className="grid size-12 place-items-center bg-muted">
                  <MessageCircleIcon aria-hidden="true" className="size-6" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-xs text-muted-foreground">Número de Ganera</p>
                  <p className="text-[22px] font-bold leading-tight">Número compartido</p>
                  <p className="text-[13px] text-muted-foreground">
                    Tus ganaderos escriben al WhatsApp de Ganera. Se reconoce a cada uno por su
                    teléfono (contactos).
                  </p>
                </div>
                <span className="inline-flex h-6 items-center gap-1.5 bg-success px-2 text-xs font-medium text-success-foreground">
                  Activo
                </span>
              </div>

              <Panel titulo="Contactos de WhatsApp">
                <p className="mb-4 text-sm text-muted-foreground">
                  Quien escribe al número de Ganera. También se pueden cargar en la hoja «Contactos»
                  del Excel.
                </p>
                <form onSubmit={onAlta} className="mb-4 flex flex-col gap-3 border border-border p-4">
                  <p className="text-sm font-medium">Añadir contacto</p>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="contacto-nombre">Nombre</Label>
                      <Input
                        id="contacto-nombre"
                        value={nombreNuevo}
                        onChange={(e) => setNombreNuevo(e.target.value)}
                        required
                        autoComplete="name"
                      />
                    </div>
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="contacto-telefono">Teléfono</Label>
                      <Input
                        id="contacto-telefono"
                        value={telefonoNuevo}
                        onChange={(e) => setTelefonoNuevo(e.target.value)}
                        required
                        inputMode="tel"
                        autoComplete="tel"
                        placeholder="+34…"
                      />
                    </div>
                  </div>
                  {errorAlta && (
                    <Alert variant="destructive">
                      <AlertTitle>No se ha podido crear</AlertTitle>
                      <AlertDescription>
                        {mensajeDeError(errorAlta, "guardar-contacto")}
                      </AlertDescription>
                    </Alert>
                  )}
                  <Button type="submit" disabled={guardando} className="w-fit">
                    {guardando ? "Guardando…" : "Añadir"}
                  </Button>
                </form>

                {errorContactos && (
                  <Alert variant="destructive">
                    <AlertTitle>No se han podido cargar los contactos</AlertTitle>
                    <AlertDescription className="flex flex-col gap-3">
                      <p>{mensajeDeError(errorContactos, "listar-contactos")}</p>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="w-fit"
                        onClick={recargarContactos}
                      >
                        Reintentar
                      </Button>
                    </AlertDescription>
                  </Alert>
                )}

                <ul className="divide-y divide-border border border-border">
                  {cargandoContactos && (
                    <li className="px-4 py-6 text-sm text-muted-foreground">Cargando…</li>
                  )}
                  {!cargandoContactos && contactos.length === 0 && (
                    <li className="px-4 py-6 text-sm text-muted-foreground">Aún no hay contactos.</li>
                  )}
                  {contactos.map((contacto) => (
                    <FilaContacto
                      key={contacto.id}
                      contacto={contacto}
                      onDesactivar={() => onDesactivar(contacto.id)}
                      onReactivar={() => onReactivar(contacto.id)}
                      onGuardar={(nombre, telefono) =>
                        onGuardarEdicion(contacto, nombre, telefono)
                      }
                    />
                  ))}
                </ul>
              </Panel>

              <Panel titulo="Plantillas de respuesta">
                <p className="text-sm text-muted-foreground">
                  Las plantillas editables de WhatsApp aún no están. Mientras, el texto se propone
                  al responder desde el trámite.
                </p>
                <Button type="button" variant="outline" className="mt-3 h-8 bg-card" disabled>
                  Nueva plantilla
                </Button>
              </Panel>
            </div>
          )}

          {seccion === "plantillas" && (
            <Panel titulo="Plantillas de trámite">
              <p className="text-sm text-muted-foreground">
                Los campos por tipo de trámite se siguen en la ficha OVZ. Editar plantillas aquí
                llegará más adelante.
              </p>
            </Panel>
          )}

          {seccion === "suscripcion" && (
            <Panel titulo="Suscripción">
              <div className="text-sm">
                {errorSuscripcion && <p>No se ha podido leer el estado de la suscripción.</p>}
                {!errorSuscripcion && suscripcion === undefined && <p>Cargando…</p>}
                {!errorSuscripcion && suscripcion === null && (
                  <p>
                    Esta gestoría aún no tiene suscripción registrada. Ponte en contacto con Ganera.
                  </p>
                )}
                {suscripcion && (
                  <dl className="grid gap-3 sm:grid-cols-2">
                    <div className="flex flex-col gap-1">
                      <dt className="text-[13px] font-medium text-muted-foreground">Estado</dt>
                      <dd className="font-medium">{ETIQUETA_SUSCRIPCION[suscripcion.estado]}</dd>
                    </div>
                    <div className="flex flex-col gap-1">
                      <dt className="text-[13px] font-medium text-muted-foreground">
                        Puede aprobar trámites
                      </dt>
                      <dd className="font-medium">
                        {suscripcion.puedeAprobarTramites ? "Sí" : "No"}
                      </dd>
                    </div>
                  </dl>
                )}
              </div>
              <p className="mt-4 text-sm text-muted-foreground">
                El cobro se gestiona desde la web de Ganera, no desde esta app.
              </p>
            </Panel>
          )}

          {seccion === "cuenta" && (
            <Panel titulo="Tu cuenta">
              <dl className="grid gap-3 text-sm sm:grid-cols-2">
                <div className="flex flex-col gap-1">
                  <dt className="text-[13px] font-medium text-muted-foreground">Nombre</dt>
                  <dd className="font-medium">{usuario?.nombre ?? "—"}</dd>
                </div>
                <div className="flex flex-col gap-1">
                  <dt className="text-[13px] font-medium text-muted-foreground">Email</dt>
                  <dd className="font-medium">{usuario?.email ?? "—"}</dd>
                </div>
              </dl>
            </Panel>
          )}
        </div>
      </div>
    </div>
  );
}

function iniciales(nombre: string | undefined): string {
  if (!nombre?.trim()) return "?";
  const partes = nombre.trim().split(/\s+/).slice(0, 2);
  return partes.map((p) => p[0]?.toUpperCase() ?? "").join("");
}

function CampoSoloLectura({ etiqueta, valor }: { etiqueta: string; valor: string }) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-[13px] font-medium">{etiqueta}</span>
      <div className="flex min-h-10 items-center border border-input bg-muted/40 px-2.5 text-sm text-muted-foreground">
        {valor}
      </div>
    </div>
  );
}

function Panel({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="border border-border bg-card">
      <div className="flex h-14 items-center border-b-2 border-divisor px-5">
        <h2 className="text-lg font-bold">{titulo}</h2>
      </div>
      <div className="px-5 py-5">{children}</div>
    </section>
  );
}

function FilaContacto({
  contacto,
  onDesactivar,
  onReactivar,
  onGuardar,
}: {
  contacto: Contacto;
  onDesactivar: () => void;
  onReactivar: () => void;
  onGuardar: (nombre: string, telefono: string) => Promise<void>;
}) {
  const [editando, setEditando] = useState(false);
  const [nombre, setNombre] = useState(contacto.nombre);
  const [telefono, setTelefono] = useState(contacto.telefono);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    setNombre(contacto.nombre);
    setTelefono(contacto.telefono);
  }, [contacto.nombre, contacto.telefono]);

  async function guardar() {
    setGuardando(true);
    try {
      await onGuardar(nombre, telefono);
      setEditando(false);
    } finally {
      setGuardando(false);
    }
  }

  return (
    <li className={`flex flex-col gap-2 px-4 py-3 text-sm ${contacto.activo ? "" : "opacity-60"}`}>
      {editando ? (
        <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <Label htmlFor={`ed-nombre-${contacto.id}`}>Nombre</Label>
            <Input
              id={`ed-nombre-${contacto.id}`}
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
            />
          </div>
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <Label htmlFor={`ed-tel-${contacto.id}`}>Teléfono</Label>
            <Input
              id={`ed-tel-${contacto.id}`}
              value={telefono}
              onChange={(e) => setTelefono(e.target.value)}
            />
          </div>
          <div className="flex gap-2">
            <Button type="button" size="sm" disabled={guardando} onClick={() => void guardar()}>
              Guardar
            </Button>
            <Button type="button" size="sm" variant="outline" onClick={() => setEditando(false)}>
              Cancelar
            </Button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="font-medium">
              {contacto.nombre}
              {!contacto.activo && (
                <span className="ml-2 text-xs font-normal text-muted-foreground">(inactivo)</span>
              )}
            </p>
            <p className="text-muted-foreground">{contacto.telefono}</p>
            {contacto.explotaciones.length > 0 && (
              <p className="mt-1 text-xs text-muted-foreground">
                {contacto.explotaciones
                  .map((e) => `${e.codigoRega} (${e.rol === "TITULAR" ? "titular" : "empleado"})`)
                  .join(" · ")}
              </p>
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            {contacto.activo && (
              <>
                <Button type="button" size="sm" variant="outline" onClick={() => setEditando(true)}>
                  Editar
                </Button>
                <Button type="button" size="sm" variant="outline" onClick={onDesactivar}>
                  Desactivar
                </Button>
              </>
            )}
            {!contacto.activo && (
              <Button type="button" size="sm" variant="outline" onClick={onReactivar}>
                Reactivar
              </Button>
            )}
          </div>
        </div>
      )}
    </li>
  );
}
