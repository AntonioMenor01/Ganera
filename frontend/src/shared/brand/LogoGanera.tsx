import type { ComponentProps } from "react";
import { cn } from "@/lib/utils";

type LogoGaneraProps = Omit<ComponentProps<"span">, "children" | "role"> & {
  /**
   * Decorativo cuando va junto al texto visible "GANERA" (o a un título "Ganera"): se oculta a
   * los lectores de pantalla para que no lean el nombre dos veces. Por defecto es una imagen con
   * nombre accesible "Ganera".
   */
  decorativo?: boolean;
};

/**
 * Símbolo de Ganera (`public/ganera-logo.svg`) pintado como máscara CSS sobre `currentColor`
 * (utilidad `logo-ganera` de `index.css`). El color sale del token de texto que se le pase
 * (`text-marca`) y el tamaño de `className` (`size-5`, `size-10`...). No usa `<img>` porque el
 * `fill="currentColor"` del SVG no hereda color dentro de una imagen.
 */
export function LogoGanera({ decorativo = false, className, ...props }: LogoGaneraProps) {
  const accesibilidad = decorativo
    ? ({ "aria-hidden": true } as const)
    : ({ role: "img", "aria-label": "Ganera" } as const);

  return (
    <span
      data-slot="logo-ganera"
      className={cn("logo-ganera inline-block size-6 shrink-0", className)}
      {...accesibilidad}
      {...props}
    />
  );
}
