/**
 * Cálculo del `scrollLeft` que deja un enlace de la tira de navegación (móvil) completamente a la
 * vista, con `margen` px libres a cada lado (el margen interior de la tira). Todas las posiciones
 * van en coordenadas del contenido de la tira (independientes del desplazamiento actual).
 *
 * - Si el enlace ya se ve entero (margen incluido), devuelve el mismo `scrollLeft`: la tira no se
 *   mueve sin necesidad.
 * - Si se sale por la izquierda (o es más ancho que la zona visible), lo alinea por su inicio.
 * - Si se sale por la derecha, desplaza lo justo para que su final quede a `margen` del borde.
 */
export function scrollLeftParaMostrar({
  scrollLeft,
  anchoVisible,
  inicio,
  fin,
  margen,
}: {
  scrollLeft: number;
  anchoVisible: number;
  inicio: number;
  fin: number;
  margen: number;
}): number {
  const izquierdaVisible = scrollLeft + margen;
  const derechaVisible = scrollLeft + anchoVisible - margen;
  if (inicio >= izquierdaVisible && fin <= derechaVisible) return scrollLeft;
  const masAnchoQueLaZona = fin - inicio > anchoVisible - 2 * margen;
  if (inicio < izquierdaVisible || masAnchoQueLaZona) return Math.max(0, inicio - margen);
  return fin - anchoVisible + margen;
}
