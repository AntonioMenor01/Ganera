/** Nombre fijo de la ventana de la ficha: abrir otra ficha reutiliza la misma ventana en lugar de
 * acumular ventanas junto a OVZ. */
export const NOMBRE_VENTANA_FICHA = "ganera-ficha-ovz";

export function rutaFichaOvz(id: number): string {
  return `/tramites/${id}/ovz`;
}

/**
 * Abre la ficha para OVZ en otra ventana (plan de la ficha OVZ, D3). SIN `noopener`: el token vive
 * en `sessionStorage`, que es por pestaña, y el navegador solo lo copia a la ventana nueva si se
 * abre con opener (desde Chrome 89, `noopener` y `target="_blank"` la abren vacía y caería al login).
 * La página es de nuestro mismo origen, así que el opener no abre ninguna puerta nueva; aun así, la
 * ficha lo corta al montar (`window.opener = null`). Si el navegador bloquea la ventana, se abre en
 * la misma pestaña.
 */
export function abrirFichaOvz(id: number): void {
  const ruta = rutaFichaOvz(id);
  const ventana = window.open(ruta, NOMBRE_VENTANA_FICHA);
  if (ventana) {
    ventana.focus();
    return;
  }
  window.location.assign(ruta);
}
