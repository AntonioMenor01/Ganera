import { useEffect, useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
} from "@/components/ui/card";
import { useAuth } from "@/shared/auth/AuthContext";
import { destinoTrasLogin } from "./destinoTrasLogin";
import { mensajeDeError } from "@/shared/api/errores";
import { LogoGanera } from "@/shared/brand/LogoGanera";
import { CLASE_ENLACE } from "@/shared/ui/enlace";
import { cn } from "@/lib/utils";

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
    <main className="flex min-h-screen items-center justify-center bg-background p-6">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <LogoGanera decorativo className="mb-3 size-10 text-primary" />
          {/* Mismo aspecto que CardTitle, pero como h1: es el título de la pantalla. */}
          <h1 data-slot="card-title" className="font-heading text-base leading-snug font-medium">
            Ganera
          </h1>
          <CardDescription>Inicia sesión con tu cuenta de gestoría.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
            {sesionCaducada && (
              <Alert>
                <AlertDescription>{AVISO_SESION_CADUCADA}</AlertDescription>
              </Alert>
            )}
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="username"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="password">Contraseña</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </div>
            {error && (
              <Alert variant="destructive">
                <AlertTitle>No se ha podido iniciar sesión</AlertTitle>
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
            <Button type="submit" disabled={enviando} className="mt-2 w-full">
              {enviando ? "Entrando…" : "Entrar"}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            ¿No tienes cuenta?{" "}
            <Link to="/registro" className={cn("font-medium", CLASE_ENLACE)}>
              Regístrate
            </Link>
          </p>
        </CardContent>
      </Card>
    </main>
  );
}
