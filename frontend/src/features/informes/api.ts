import { listarTramites } from "@/features/tramites/api";
import type { EstadoTramite } from "@/features/tramites/types";
import { contarGanaderos } from "@/features/dashboard/api";
import { httpClient } from "@/shared/api/httpClient";
import type { Pagina } from "@/shared/api/types";

/** Estados que la cola ya filtra (sin los de automatización OVZ que hoy no se dan). */
export const ESTADOS_INFORME: readonly EstadoTramite[] = [
  "PENDIENTE_EXTRACCION",
  "PENDIENTE_REVISION",
  "APROBADO",
  "HECHO_EN_OVZ",
  "RECHAZADO",
];

/** Conteo barato: `totalElements` de GET /tramites?estado=…&size=1 (mismo patrón que el dashboard). */
export async function contarTramitesPorEstado(estado: EstadoTramite): Promise<number> {
  const pagina = await listarTramites(0, 1, estado);
  return pagina.totalElements;
}

export async function contarTramitesTotales(): Promise<number> {
  const pagina = await listarTramites(0, 1);
  return pagina.totalElements;
}

export async function contarExplotaciones(): Promise<number> {
  const { data } = await httpClient.get<Pagina<unknown>>("/explotaciones", {
    params: { page: 0, size: 1 },
  });
  return data.totalElements;
}

export { contarGanaderos };
