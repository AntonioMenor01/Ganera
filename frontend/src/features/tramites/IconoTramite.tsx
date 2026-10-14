import {
  ArchiveIcon,
  ArrowDownToLineIcon,
  CheckIcon,
  CircleCheckIcon,
  EyeIcon,
  ListOrderedIcon,
  LogInIcon,
  LogOutIcon,
  SparklesIcon,
  TagIcon,
  TruckIcon,
  XIcon,
  type LucideIcon,
} from "lucide-react";
import type { IconoEstado, IconoTipo } from "./etiquetas";

/** Nombre (de `etiquetas.ts`) → componente de lucide. */
const ICONOS = {
  "log-in": LogInIcon,
  "log-out": LogOutIcon,
  truck: TruckIcon,
  "arrow-down-to-line": ArrowDownToLineIcon,
  "list-ordered": ListOrderedIcon,
  tag: TagIcon,
  sparkles: SparklesIcon,
  eye: EyeIcon,
  check: CheckIcon,
  "circle-check": CircleCheckIcon,
  x: XIcon,
  archive: ArchiveIcon,
} as const satisfies Record<IconoTipo | IconoEstado, LucideIcon>;

/** Siempre decorativo: el texto de al lado (etiqueta del tipo o del estado) es el que informa. */
export function IconoTramite({ nombre, className }: { nombre: IconoTipo | IconoEstado; className?: string }) {
  const Icono = ICONOS[nombre];
  return <Icono aria-hidden="true" data-icono={nombre} className={className} />;
}
