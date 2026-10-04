import { httpClient } from "@/shared/api/httpClient";
import { esErrorApi } from "@/shared/api/errores";
import type { SuscripcionEstado } from "./types";

/** null si la Gestoria nunca ha tenido Suscripcion (404 del backend, fail-closed a proposito). */
export async function obtenerEstadoSuscripcion(): Promise<SuscripcionEstado | null> {
  try {
    const { data } = await httpClient.get<SuscripcionEstado>("/facturacion/suscripcion");
    return data;
  } catch (error) {
    if (esErrorApi(error) && error.tipo === "no-encontrado") {
      return null;
    }
    throw error;
  }
}
