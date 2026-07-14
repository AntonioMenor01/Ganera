import axios from "axios";
import { httpClient } from "@/shared/api/httpClient";
import type { SuscripcionEstado } from "./types";

/** null si la Gestoria nunca ha tenido Suscripcion (404 del backend, fail-closed a proposito). */
export async function obtenerEstadoSuscripcion(): Promise<SuscripcionEstado | null> {
  try {
    const { data } = await httpClient.get<SuscripcionEstado>("/facturacion/suscripcion");
    return data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      return null;
    }
    throw error;
  }
}

/** Misma sesion de checkout tanto para empezar el trial de 15 dias (si nunca tuvo Suscripcion)
 * como para poner al dia el pago (si esta en un estado bloqueante) -- el backend ya resuelve
 * cual de los dos casos es via obtenerOCrearSuscripcion. */
export async function crearSesionCheckout(): Promise<string> {
  const { data } = await httpClient.post<{ url: string }>("/facturacion/checkout");
  return data.url;
}
