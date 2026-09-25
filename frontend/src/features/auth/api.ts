import { httpClient } from "@/shared/api/httpClient";

export type RangoClientes =
  | "UNO_A_DIEZ"
  | "ONCE_A_TREINTA"
  | "TREINTA_UNO_A_SETENTA_Y_CINCO"
  | "SETENTA_Y_SEIS_O_MAS";

export interface RegistroGestoriaRequest {
  nombreGestoria: string;
  nombreUsuario: string;
  email: string;
  password: string;
  rangoClientes: RangoClientes;
}

/** Publico, sin JWT -- POST /gestorias/registro. Devuelve la URL de la Stripe Checkout Session a
 * la que el frontend debe redirigir (mismo patron que crearSesionCheckout en facturacion/api.ts). */
export async function registrarGestoria(request: RegistroGestoriaRequest): Promise<string> {
  const { data } = await httpClient.post<{ url: string }>("/gestorias/registro", request);
  return data.url;
}
