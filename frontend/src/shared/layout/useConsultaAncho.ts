import { useCallback, useSyncExternalStore } from "react";

function hayMatchMedia(): boolean {
  return typeof window !== "undefined" && typeof window.matchMedia === "function";
}

/**
 * true si se cumple `consulta` (p. ej. `"(min-width: 64rem)"`, el `lg` de Tailwind v4). Solo para
 * decisiones de ESTRUCTURA que el CSS no puede tomar (qué marcado se pinta, cuántas columnas o
 * crotales hay): así el DOM tiene una sola versión, sin nada duplicado para el lector de pantalla.
 * Para lo puramente visual, las variantes de Tailwind.
 *
 * Sin matchMedia (navegador muy antiguo, tests en jsdom) se asume escritorio: devuelve true.
 */
export function useConsultaAncho(consulta: string): boolean {
  const suscribir = useCallback(
    (avisar: () => void) => {
      if (!hayMatchMedia()) return () => {};
      const lista = window.matchMedia(consulta);
      lista.addEventListener("change", avisar);
      return () => lista.removeEventListener("change", avisar);
    },
    [consulta],
  );
  const leer = useCallback(
    () => (hayMatchMedia() ? window.matchMedia(consulta).matches : true),
    [consulta],
  );
  return useSyncExternalStore(suscribir, leer, () => true);
}
