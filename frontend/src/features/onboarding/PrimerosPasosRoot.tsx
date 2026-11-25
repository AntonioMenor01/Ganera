import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/shared/auth/AuthContext";
import { PrimerosPasosGuia } from "./PrimerosPasosGuia";
import {
  autoAperturaPrimerosPasosActiva,
  guiaYaVista,
  marcarGuiaVista,
  suscribirAperturaPrimerosPasos,
} from "./primerosPasos";

/**
 * Conecta la guía de primeros pasos con la app: la abre sola la PRIMERA vez que un usuario entra
 * (una vez por usuario, recordado en localStorage), permite reabrirla a mano desde cualquier parte
 * ({@code abrirPrimerosPasos()}), navega cuando se pulsa una CTA y la marca como vista al cerrarla.
 * Se monta una sola vez, dentro del layout autenticado.
 */
export function PrimerosPasosRoot() {
  const { usuario } = useAuth();
  const navigate = useNavigate();
  const [abierta, setAbierta] = useState(false);
  // Para no reevaluar la apertura automática más de una vez por usuario en esta sesión de React.
  const autoEvaluadaPara = useRef<number | null>(null);

  const usuarioId = usuario?.id ?? null;

  // Apertura automática la primera vez de cada usuario (y solo una vez por montaje/usuario).
  useEffect(() => {
    if (usuarioId == null) return;
    if (autoEvaluadaPara.current === usuarioId) return;
    autoEvaluadaPara.current = usuarioId;
    if (autoAperturaPrimerosPasosActiva() && !guiaYaVista(usuarioId)) setAbierta(true);
  }, [usuarioId]);

  // Reapertura manual (botón «Primeros pasos»).
  useEffect(() => suscribirAperturaPrimerosPasos(() => setAbierta(true)), []);

  function cerrar() {
    setAbierta(false);
    if (usuarioId != null) marcarGuiaVista(usuarioId);
  }

  function ir(to: string) {
    cerrar();
    navigate(to);
  }

  return <PrimerosPasosGuia open={abierta} onClose={cerrar} onIr={ir} />;
}
