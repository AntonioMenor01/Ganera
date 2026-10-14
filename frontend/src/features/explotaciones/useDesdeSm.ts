import { useConsultaAncho } from "@/shared/layout/useConsultaAncho";

/** La misma consulta que el `sm` de Tailwind v4 (40rem = 640 px). Si cambia el punto de corte del
 * tema, se cambia aquí también. */
const CONSULTA_SM = "(min-width: 40rem)";

/**
 * true desde `sm` hacia arriba. Solo para decisiones de ESTRUCTURA que el CSS no puede tomar, como
 * cuántas columnas tiene una tabla (el `colSpan` de una fila de expansión debe coincidir con las
 * columnas que existen de verdad). Para lo puramente visual, las variantes de Tailwind.
 */
export function useDesdeSm(): boolean {
  return useConsultaAncho(CONSULTA_SM);
}
