import type { EstadoRedireccionLogin } from "@/shared/auth/RequireAuth";

const DESTINO_POR_DEFECTO = "/tramites";

/** Rutas a las que no tiene sentido volver tras entrar (públicas o la raíz). */
const RUTAS_EXCLUIDAS = new Set(["/", "/login", "/registro"]);

/** Caracteres de control (U+0000-U+001F y U+007F): el parser de URL borra tabuladores y saltos. */
function tieneCaracteresDeControl(texto: string): boolean {
  for (let i = 0; i < texto.length; i += 1) {
    const codigo = texto.charCodeAt(i);
    if (codigo < 0x20 || codigo === 0x7f) return true;
  }
  return false;
}

/**
 * A dónde ir tras un login correcto: el `from` que dejó RequireAuth si es una ruta interna del
 * mismo origen, o /tramites. Hoy `from` solo lo produce RequireAuth, pero se valida igual (defensa
 * en profundidad contra open redirect):
 * - empieza por "/" y no por "//";
 * - sin barra invertida: los navegadores la tratan como "/", así que "/\evil.com" es otro origen;
 * - sin caracteres de control: "/<TAB>/evil.com" se convierte en "//evil.com" al parsear;
 * - resuelto contra el origen actual, sigue en el mismo origen, y la ruta normalizada tampoco
 *   empieza por "//" ("/.//evil.com" o "/%2e//evil.com" se colapsan a "//evil.com");
 * - no es /, /login ni /registro (con o sin barra final, query o hash).
 * Devuelve la ruta normalizada por el parser de URL (path + query + hash).
 */
export function destinoTrasLogin(state: unknown, origen: string = window.location.origin): string {
  const from = (state as EstadoRedireccionLogin | null)?.from;
  if (typeof from !== "string" || !from.startsWith("/") || from.startsWith("//")) {
    return DESTINO_POR_DEFECTO;
  }
  if (from.includes("\\") || tieneCaracteresDeControl(from)) {
    return DESTINO_POR_DEFECTO;
  }
  let url: URL;
  try {
    url = new URL(from, origen);
    if (url.origin !== new URL(origen).origin) return DESTINO_POR_DEFECTO;
  } catch {
    return DESTINO_POR_DEFECTO;
  }
  // Los segmentos "." y ".." se colapsan al parsear: "/.//evil.com" acaba en "//evil.com", que el
  // navegador leeria como otro origen. Se vuelve a comprobar sobre la ruta ya normalizada.
  if (url.pathname.startsWith("//")) return DESTINO_POR_DEFECTO;
  const ruta = url.pathname.length > 1 ? url.pathname.replace(/\/+$/, "") : url.pathname;
  if (RUTAS_EXCLUIDAS.has(ruta)) return DESTINO_POR_DEFECTO;
  return `${url.pathname}${url.search}${url.hash}`;
}
