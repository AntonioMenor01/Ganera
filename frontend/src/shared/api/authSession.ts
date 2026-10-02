/**
 * Fuente única del token para httpClient (fuera del árbol de React). AuthContext es quien lo
 * escribe: login, Salir y el manejador del 401.
 *
 * Persistencia (decisión 1 del plan A2): SOLO el token, en sessionStorage bajo `ganera.token`.
 * - Sobrevive a una recarga y se borra al cerrar la pestaña.
 * - Es por pestaña a propósito: no hay sincronización entre pestañas. Abrir otra pestaña pide
 *   login, y salir en una no cierra las demás.
 * - Nada más va al almacenamiento: ni el usuario ni el email (se piden a /auth/me al arrancar).
 * - Si el almacenamiento no está disponible (modo privado que lanza, cuota, cookies bloqueadas),
 *   todo sigue funcionando con el token en memoria, como antes de la decisión 1: solo se pierde la
 *   supervivencia a la recarga.
 * - sessionStorage NO protege frente a XSS (cualquier script de la página puede leerlo); la
 *   alternativa robusta, una cookie httpOnly, exige cambios de backend.
 */
export const CLAVE_TOKEN = "ganera.token";

let currentToken: string | null = null;

/** El getter de window.sessionStorage puede lanzar él mismo (SecurityError): todo va en try. */
function leerGuardado(): string | null {
  try {
    const valor = window.sessionStorage.getItem(CLAVE_TOKEN);
    return valor && valor.trim() !== "" ? valor : null;
  } catch {
    return null;
  }
}

function guardar(token: string | null): void {
  try {
    if (token) {
      window.sessionStorage.setItem(CLAVE_TOKEN, token);
    } else {
      window.sessionStorage.removeItem(CLAVE_TOKEN);
    }
  } catch {
    // Sin almacenamiento: la sesión vive solo en memoria.
  }
}

export function setAuthToken(token: string | null): void {
  currentToken = token;
  guardar(token);
}

export function getAuthToken(): string | null {
  return currentToken;
}

/**
 * Al arrancar la app (tras una recarga la memoria está vacía): recupera el token guardado, si lo
 * hay, y lo deja como token actual. Devuelve el token actual. Idempotente.
 */
export function restaurarTokenGuardado(): string | null {
  const guardado = leerGuardado();
  if (guardado) currentToken = guardado;
  return currentToken;
}

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

/** Registrado por AuthProvider para cerrar la sesión cuando llega un 401 real del backend. */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

/**
 * Lo llama httpClient ante un 401 (salvo el del login). `tokenUsado` es el token con el que salió
 * esa petición (null si salió sin Bearer). Un 401 de una petición hecha con un token que ya no es
 * el actual (p. ej. la comprobación del arranque con el token viejo, que responde después de un
 * login nuevo) no dice nada de la sesión actual y se ignora.
 */
export function notifyUnauthorized(tokenUsado: string | null = currentToken): void {
  if (tokenUsado !== currentToken) return;
  unauthorizedHandler?.();
}
