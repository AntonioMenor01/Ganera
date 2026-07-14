/**
 * Token en memoria, no localStorage -- sesion de SPA, una recarga de pagina exige login de
 * nuevo por ahora (sin refresh token todavia). httpClient (fuera del arbol de React) lee el
 * token de aqui; AuthContext es quien lo escribe.
 */
let currentToken: string | null = null;

export function setAuthToken(token: string | null): void {
  currentToken = token;
}

export function getAuthToken(): string | null {
  return currentToken;
}

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

/** Registrado por AuthProvider para limpiar la sesion cuando llega un 401 real del backend. */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

export function notifyUnauthorized(): void {
  unauthorizedHandler?.();
}
