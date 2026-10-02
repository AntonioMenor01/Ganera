import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { mensajeDeError } from "@/shared/api/errores";
import { LogoGanera } from "@/shared/brand/LogoGanera";
import { CLASE_ENLACE } from "@/shared/ui/enlace";
import { cn } from "@/lib/utils";
import { registrarGestoria, type RangoClientes } from "./api";

const RANGOS: { value: RangoClientes; label: string }[] = [
  { value: "UNO_A_DIEZ", label: "1 a 10 clientes" },
  { value: "ONCE_A_TREINTA", label: "11 a 30 clientes" },
  { value: "TREINTA_UNO_A_SETENTA_Y_CINCO", label: "31 a 75 clientes" },
  { value: "SETENTA_Y_SEIS_O_MAS", label: "76 o más clientes" },
];

export function RegistroPage() {
  const [nombreGestoria, setNombreGestoria] = useState("");
  const [nombreUsuario, setNombreUsuario] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [rangoClientes, setRangoClientes] = useState<RangoClientes>("UNO_A_DIEZ");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      const url = await registrarGestoria({
        nombreGestoria,
        nombreUsuario,
        email,
        password,
        rangoClientes,
      });
      window.location.href = url;
    } catch (err) {
      setError(mensajeDeError(err, "registro"));
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
          <CardDescription>Registra tu gestoría y empieza tu prueba de 15 días.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="nombreGestoria">Nombre de la gestoría</Label>
              <Input
                id="nombreGestoria"
                required
                value={nombreGestoria}
                onChange={(event) => setNombreGestoria(event.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="nombreUsuario">Tu nombre</Label>
              <Input
                id="nombreUsuario"
                required
                value={nombreUsuario}
                onChange={(event) => setNombreUsuario(event.target.value)}
              />
            </div>
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
                autoComplete="new-password"
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="rangoClientes">Número de clientes</Label>
              <Select
                value={rangoClientes}
                onValueChange={(value) => setRangoClientes(value as RangoClientes)}
              >
                <SelectTrigger id="rangoClientes">
                  <SelectValue>
                    {(value: RangoClientes) => RANGOS.find((rango) => rango.value === value)?.label ?? value}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  {RANGOS.map((rango) => (
                    <SelectItem key={rango.value} value={rango.value}>
                      {rango.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {error && (
              <Alert variant="destructive">
                <AlertTitle>No se ha podido completar el registro</AlertTitle>
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
            <Button type="submit" disabled={enviando} className="mt-2 w-full">
              {enviando ? "Redirigiendo…" : "Continuar a pago"}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            ¿Ya tienes cuenta?{" "}
            <Link to="/login" className={cn("font-medium", CLASE_ENLACE)}>
              Inicia sesión
            </Link>
          </p>
        </CardContent>
      </Card>
    </main>
  );
}
