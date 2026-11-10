import { httpClient } from "@/shared/api/httpClient";

export interface PreferenciaOvz {
  oficinaDi: string | null;
  oficinaMer: string | null;
  emisionDi: string | null;
  tipoIdentificador: string | null;
  telefonoMovimiento: string | null;
  medioTransporte: string | null;
  matricula: string | null;
  transportistaSirentra: string | null;
  tipoResponsable: string | null;
}

export interface ListaOvz {
  id: string;
  etiqueta: string;
  provisional: boolean;
  valores: string[];
}

export async function obtenerPreferenciasOvz(): Promise<PreferenciaOvz> {
  const { data } = await httpClient.get<PreferenciaOvz>("/configuracion/preferencias-ovz");
  return data;
}

export async function guardarPreferenciasOvz(body: PreferenciaOvz): Promise<PreferenciaOvz> {
  const { data } = await httpClient.put<PreferenciaOvz>("/configuracion/preferencias-ovz", body);
  return data;
}

export async function obtenerListasOvz(): Promise<ListaOvz[]> {
  const { data } = await httpClient.get<{ listas: ListaOvz[] }>("/configuracion/listas-ovz");
  return data.listas;
}
