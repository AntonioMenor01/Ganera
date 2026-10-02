import { useSyncExternalStore } from "react";

/** La misma consulta que el `sm` de Tailwind v4 (40rem = 640 px). Si cambia el punto de corte del
 * tema, se cambia aquí también. */
const CONSULTA_SM = "(min-width: 40rem)";

function hayMatchMedia(): boolean {
  return typeof window !== "undefined" && typeof window.matchMedia === "function";
}

function suscribir(avisar: () => void): () => void {
  if (!hayMatchMedia()) return () => {};
  const consulta = window.matchMedia(CONSULTA_SM);
  consulta.addEventListener("change", avisar);
  return () => consulta.removeEventListener("change", avisar);
}

function leer(): boolean {
  // Sin matchMedia (navegador muy antiguo, tests en jsdom) se asume escritorio: la tabla completa.
  return hayMatchMedia() ? window.matchMedia(CONSULTA_SM).matches : true;
}

/**
 * true desde `sm` hacia arriba. Solo para decisiones de ESTRUCTURA que el CSS no puede tomar, como
 * cuántas columnas tiene una tabla (el `colSpan` de una fila de expansión debe coincidir con las
 * columnas que existen de verdad). Para lo puramente visual, las variantes de Tailwind.
 */
export function useDesdeSm(): boolean {
  return useSyncExternalStore(suscribir, leer, () => true);
}
