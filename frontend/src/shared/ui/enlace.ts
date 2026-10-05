/** Enlace de texto de la aplicación: Rojo 700 (`--enlace`, el único color de enlace, DESIGN.md),
 * subrayado al pasar con el propio rojo atenuado y a distancia fija, y el anillo de foco sólido del
 * sistema. El rojo 600 sólido (`--primary`) es solo para acciones, nunca para texto de enlace. */
export const CLASE_ENLACE =
  "rounded-sm text-enlace decoration-enlace/40 underline-offset-4 outline-none hover:underline focus-visible:underline focus-visible:ring-3 focus-visible:ring-ring";

/** Enlace de una columna de tabla: Tinta en reposo y Rojo 700 (`--enlace`) con subrayado solo al
 * pasar o con foco de teclado. El rojo en cada fila llenaría la tabla de rojo y se leería como
 * error; el color aparece al apuntar. En el resto de sitios se usa `CLASE_ENLACE`. */
export const CLASE_ENLACE_TABLA =
  "rounded-sm text-foreground decoration-enlace/40 underline-offset-4 outline-none transition-colors hover:text-enlace hover:underline focus-visible:text-enlace focus-visible:underline focus-visible:ring-3 focus-visible:ring-ring";
