import { useEffect, useState, type FormEvent } from "react";
import { ArrowRightIcon, CircleAlertIcon, LockIcon, MailIcon } from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { useAuth } from "@/shared/auth/AuthContext";
import { destinoTrasLogin } from "./destinoTrasLogin";
import { mensajeDeError } from "@/shared/api/errores";
import { LogoGanera } from "@/shared/brand/LogoGanera";

const AVISO_SESION_CADUCADA = "Tu sesión ha caducado. Vuelve a iniciar sesión.";
export function LoginPage() {
  const { login, motivoCierre, olvidarMotivoCierre } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  // El aviso se lee una vez al montar y se consume en el contexto: no se repite al volver a /login.
  const [sesionCaducada, setSesionCaducada] = useState(() => motivoCierre === "caducada");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    if (motivoCierre !== null) olvidarMotivoCierre();
  }, [motivoCierre, olvidarMotivoCierre]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSesionCaducada(false);
    setEnviando(true);
    try {
      await login(email, password);
      navigate(destinoTrasLogin(location.state), { replace: true });
    } catch (err) {
      setError(mensajeDeError(err, "login"));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="flex min-h-screen flex-col bg-background lg:grid lg:grid-cols-2">
      {/* Panel Rojo 600: banda corta por debajo de `lg`, columna entera desde `lg`. Va antes del
          formulario (como v2), sin encabezado ni nada enfocable. */}
      <div
        data-slot="login-panel"
        className="flex h-14 shrink-0 items-center bg-primary px-4 text-primary-foreground sm:px-6 lg:h-auto lg:flex-col lg:items-stretch lg:justify-between lg:p-12 xl:px-14"
      >
        <div className="flex items-center gap-2.5">
          <LogoGanera decorativo className="size-6 text-primary-foreground lg:size-7" />
          <span className="text-lg font-bold tracking-wide">GANERA</span>
        </div>
        <p className="hidden max-w-md text-xl leading-snug text-pretty lg:block">
          Los mensajes de WhatsApp de tus ganaderos llegan como trámites listos para revisar.
        </p>
      </div>
      <div className="flex flex-1 items-start justify-center px-4 py-10 sm:px-6 lg:items-center lg:px-12 lg:py-12">
        <div className="w-full max-w-[400px]">
          <LogoGanera decorativo className="mb-4 size-9 text-marca" />
          <h1 className="font-heading text-[32px] leading-tight font-bold tracking-tight">
            Entrar en Ganera
          </h1>
          <p className="mt-1.5 text-muted-foreground">Inicia sesión con tu cuenta de gestoría.</p>
          <form className="mt-6 flex flex-col gap-4" onSubmit={handleSubmit}>
            {sesionCaducada && (
              <Alert>
                <AlertDescription>{AVISO_SESION_CADUCADA}</AlertDescription>
              </Alert>
            )}
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="email">Email</Label>
              <div className="relative">
                <MailIcon
                  aria-hidden
                  className="pointer-events-none absolute top-1/2 left-3 size-[18px] -translate-y-1/2 text-muted-foreground"
                />
                <Input
                  id="email"
                  type="email"
                  autoComplete="username"
                  required
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  className="h-11 bg-card pl-10"
                />
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="password">Contraseña</Label>
              <div className="relative">
                <LockIcon
                  aria-hidden
                  className="pointer-events-none absolute top-1/2 left-3 size-[18px] -translate-y-1/2 text-muted-foreground"
                />
                <Input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  className="h-11 bg-card pl-10"
                />
              </div>
            </div>
            {error && (
              <Alert variant="destructive">
                <CircleAlertIcon />
                <AlertTitle>No se ha podido iniciar sesión</AlertTitle>
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
            <Button
              type="submit"
              disabled={enviando}
              className="mt-2 h-12 w-full justify-between px-4 text-base font-medium"
            >
              {enviando ? "Entrando…" : "Entrar"}
              <ArrowRightIcon aria-hidden className="size-[18px]" />
            </Button>
          </form>
        </div>
      </div>
    </main>
  );
}
