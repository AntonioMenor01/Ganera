import axios, { type InternalAxiosRequestConfig } from "axios";
import { getAuthToken, notifyUnauthorized } from "./authSession";
import { aErrorApi } from "./errores";

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080",
});

httpClient.interceptors.request.use((config) => {
  const token = getAuthToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * Todo fallo sale de aquí como ErrorApi (ver errores.ts): ninguna pantalla interpreta axios.
 * Un 401 cierra la sesión, salvo el del propio login (H11): ahí no hay sesión que cerrar y la
 * pantalla de login da su error uniforme. Un 403 nunca cierra la sesión.
 */
httpClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const errorApi = aErrorApi(error);
    const config = axios.isAxiosError(error) ? error.config : undefined;
    if (errorApi.tipo === "no-autorizado" && !esPeticionDeLogin(config)) {
      notifyUnauthorized(tokenDeLaPeticion(config));
    }
    return Promise.reject(errorApi);
  },
);

/** El token con el que salió la petición (lo puso el interceptor de petición), o null. */
function tokenDeLaPeticion(config: InternalAxiosRequestConfig | undefined): string | null {
  const cabecera = config?.headers?.Authorization;
  if (typeof cabecera !== "string" || !cabecera.startsWith("Bearer ")) return null;
  return cabecera.slice("Bearer ".length);
}

function esPeticionDeLogin(config: InternalAxiosRequestConfig | undefined): boolean {
  if (!config || config.method?.toLowerCase() !== "post") return false;
  try {
    const ruta = new URL(httpClient.getUri(config), "http://localhost").pathname;
    return ruta.replace(/\/+$/, "").endsWith("/auth/login");
  } catch {
    return false;
  }
}
