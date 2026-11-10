import { httpClient } from "@/shared/api/httpClient";

export interface Alerta {
  id: string;
  tipo: string;
  titulo: string;
  detalle: string;
  venceEl: string | null;
  tramiteId: number | null;
  severidad: string;
}

export async function listarAlertas(): Promise<Alerta[]> {
  const { data } = await httpClient.get<Alerta[]>("/alertas");
  return data;
}
