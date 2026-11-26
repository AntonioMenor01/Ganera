import { useState, type FormEvent } from "react";
import { ArrowRightIcon, CircleAlertIcon, LockIcon } from "lucide-react";
import { Navigate, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { useAuth } from "@/shared/auth/AuthContext";
import { mensajeDeError } from "@/shared/api/errores";
import { LogoGanera } from "@/shared/brand/LogoGanera";

const LONGITUD_MINIMA = 8;

/**
 * Cambio obligatorio de la contraseña temporal del alta (RequireAuth manda aquí mientras
 * `debeCambiarPassword` esté activo). No tiene la barra de la app: es un paso previo a usarla. Si el
 * flag ya está a false (cambio hecho, o visita directa sin él), se va al inicio.
 */
export function CambiarPasswordPage() {
  const { usuario, cambiarPassword } = useAuth();
  const navigate = useNavigate();
  const [actual, setActual] = useState("");
  const [nueva, setNueva] = useState("");
  const [confirmacion, setConfirmacion] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  if (usuario && !usuario.debeCambiarPassword) {
    return <Navigate to="/" replace />;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (nueva.length < LONGITUD_MINIMA) {
      setError(`La contraseña nueva debe tener al menos ${LONGITUD_MINIMA} caracteres.`);
      return;
    }
    if (nueva !== confirmacion) {
      setError("Las dos contraseñas no coinciden.");
      return;
    }
    setEnviando(true);
    try {
      await cambiarPassword(actual, nueva);
      navigate("/", { replace: true });
    } catch (err) {
      setError(mensajeDeError(err, "cambiar-password"));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="flex min-h-screen items-start justify-center bg-background px-4 py-10 sm:px-6 lg:items-center lg:py-12">
      <div className="w-full max-w-[400px]">
        <LogoGanera decorativo className="mb-4 size-9 text-marca" />
        <h1 className="font-heading text-[32px] leading-tight font-bold tracking-[-0.02em]">
          Cambia tu contraseña
        </h1>
        <p className="mt-1.5 text-[15px] text-muted-foreground">
          Te dimos una contraseña temporal al crear la cuenta. Elige una nueva para seguir.
        </p>
        <form className="mt-6 flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="actual">Contraseña actual</Label>
            <div className="relative">
              <LockIcon
                aria-hidden
                className="pointer-events-none absolute top-1/2 left-3 size-[18px] -translate-y-1/2 text-muted-foreground"
              />
              <Input
                id="actual"
                type="password"
                autoComplete="current-password"
                required
                value={actual}
                onChange={(event) => setActual(event.target.value)}
                className="h-11 bg-card pl-10 text-[15px]"
              />
            </div>
            <p className="text-[13px] text-muted-foreground">La que te enviamos por correo.</p>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="nueva">Contraseña nueva</Label>
            <div className="relative">
              <LockIcon
                aria-hidden
                className="pointer-events-none absolute top-1/2 left-3 size-[18px] -translate-y-1/2 text-muted-foreground"
              />
              <Input
                id="nueva"
                type="password"
                autoComplete="new-password"
                required
                minLength={LONGITUD_MINIMA}
                value={nueva}
                onChange={(event) => setNueva(event.target.value)}
                className="h-11 bg-card pl-10 text-[15px]"
              />
            </div>
            <p className="text-[13px] text-muted-foreground">Al menos {LONGITUD_MINIMA} caracteres.</p>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="confirmacion">Repite la contraseña nueva</Label>
            <div className="relative">
              <LockIcon
                aria-hidden
                className="pointer-events-none absolute top-1/2 left-3 size-[18px] -translate-y-1/2 text-muted-foreground"
              />
              <Input
                id="confirmacion"
                type="password"
                autoComplete="new-password"
                required
                value={confirmacion}
                onChange={(event) => setConfirmacion(event.target.value)}
                className="h-11 bg-card pl-10 text-[15px]"
              />
            </div>
          </div>
          {error && (
            <Alert variant="destructive">
              <CircleAlertIcon />
              <AlertTitle>No se ha podido cambiar la contraseña</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <Button
            type="submit"
            disabled={enviando}
            className="mt-2 h-12 w-full justify-between px-4 text-base font-medium"
          >
            {enviando ? "Guardando…" : "Guardar y continuar"}
            <ArrowRightIcon aria-hidden className="size-[18px]" />
          </Button>
        </form>
      </div>
    </main>
  );
}
