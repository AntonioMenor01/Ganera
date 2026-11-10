import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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

export function ConfiguracionPage() {
  const { usuario } = useAuth();
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
    <div className="mx-auto flex max-w-3xl flex-col gap-10">
      <header className="flex flex-col gap-1">
        <h1 className="text-xl font-bold tracking-tight">Configuración</h1>
        <p className="text-sm text-muted-foreground">
          Tu cuenta, la suscripción y los contactos de WhatsApp. Las credenciales OVZ.net siguen
          aplazadas (escalón 2): Ganera no las pide aquí.
        </p>
      </header>

      <section aria-labelledby="cuenta-titulo" className="flex flex-col gap-3">
        <h2 id="cuenta-titulo" className="text-base font-bold">
          Tu cuenta
        </h2>
        <dl className="rounded-sm border border-border bg-card px-4 py-3 text-sm">
          <div className="flex justify-between gap-4 py-1">
            <dt className="text-muted-foreground">Nombre</dt>
            <dd className="font-medium">{usuario?.nombre ?? "—"}</dd>
          </div>
          <div className="flex justify-between gap-4 py-1">
            <dt className="text-muted-foreground">Email</dt>
            <dd className="font-medium">{usuario?.email ?? "—"}</dd>
          </div>
        </dl>
      </section>

      <section aria-labelledby="suscripcion-titulo" className="flex flex-col gap-3">
        <h2 id="suscripcion-titulo" className="text-base font-bold">
          Suscripción
        </h2>
        <div className="rounded-sm border border-border bg-card px-4 py-3 text-sm">
          {errorSuscripcion && <p>No se ha podido leer el estado de la suscripción.</p>}
          {!errorSuscripcion && suscripcion === undefined && <p>Cargando…</p>}
          {!errorSuscripcion && suscripcion === null && (
            <p>Esta gestoría aún no tiene suscripción registrada. Ponte en contacto con Ganera.</p>
          )}
          {suscripcion && (
            <dl className="flex flex-col gap-1">
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Estado</dt>
                <dd className="font-medium">{ETIQUETA_SUSCRIPCION[suscripcion.estado]}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Puede aprobar trámites</dt>
                <dd className="font-medium">{suscripcion.puedeAprobarTramites ? "Sí" : "No"}</dd>
              </div>
            </dl>
          )}
        </div>
        <p className="text-sm text-muted-foreground">
          El cobro se gestiona desde la web de Ganera, no desde esta app. Inventario:{" "}
          <Link to="/explotaciones" className={CLASE_ENLACE}>
            importar Excel en Explotaciones
          </Link>
          .
        </p>
      </section>

      <section aria-labelledby="contactos-titulo" className="flex flex-col gap-4">
        <h2 id="contactos-titulo" className="text-base font-bold">
          Contactos de WhatsApp
        </h2>
        <p className="text-sm text-muted-foreground">
          Quien escribe al número de Ganera. También se pueden cargar en la hoja «Contactos» del
          Excel.
        </p>

        <form onSubmit={onAlta} className="flex flex-col gap-3 rounded-sm border border-border bg-card p-4">
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
              <AlertDescription>{mensajeDeError(errorAlta, "guardar-contacto")}</AlertDescription>
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
              <Button type="button" variant="outline" size="sm" className="w-fit" onClick={recargarContactos}>
                Reintentar
              </Button>
            </AlertDescription>
          </Alert>
        )}

        <ul className="divide-y divide-border rounded-sm border border-border bg-card">
          {cargandoContactos && <li className="px-4 py-6 text-sm text-muted-foreground">Cargando…</li>}
          {!cargandoContactos && contactos.length === 0 && (
            <li className="px-4 py-6 text-sm text-muted-foreground">Aún no hay contactos.</li>
          )}
          {contactos.map((contacto) => (
            <FilaContacto
              key={contacto.id}
              contacto={contacto}
              onDesactivar={() => onDesactivar(contacto.id)}
              onReactivar={() => onReactivar(contacto.id)}
              onGuardar={(nombre, telefono) => onGuardarEdicion(contacto, nombre, telefono)}
            />
          ))}
        </ul>
      </section>
    </div>
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
