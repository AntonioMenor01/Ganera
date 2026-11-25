import {
  CircleCheckBigIcon,
  ClipboardListIcon,
  FileSpreadsheetIcon,
  HandIcon,
  MessageCircleIcon,
  SendIcon,
  SettingsIcon,
  UserPlusIcon,
  type LucideIcon,
} from "lucide-react";

/** Un paso de la guía de primeros pasos. `cta` (opcional) lleva a una pantalla real de la app. */
export interface PasoGuia {
  id: string;
  Icono: LucideIcon;
  titulo: string;
  cuerpo: string;
  /** Acción que lleva a una pantalla real; al pulsarla se cierra la guía. */
  cta?: { label: string; to: string };
}

/**
 * Los 7-8 pasos cortos que explican a un cliente nuevo qué hacer para empezar. Son explicativos y
 * NAVEGAN a las pantallas reales (donde ya están los formularios): no duplican formularios ni
 * capturan datos que el backend todavía no sabe guardar (p. ej. credenciales de login de OVZ, que
 * es una decisión aplazada). Orden alineado con el flujo de trabajo real de la app.
 */
export const PASOS_GUIA: PasoGuia[] = [
  {
    id: "bienvenida",
    Icono: HandIcon,
    titulo: "Te damos la bienvenida a Ganera",
    cuerpo:
      "Ganera convierte los WhatsApp de tus ganaderos en trámites listos para revisar. Estos pasos te dejan todo preparado en un par de minutos. Puedes saltártelos y volver cuando quieras.",
  },
  {
    id: "ovz",
    Icono: SettingsIcon,
    titulo: "Configura tus datos de OVZ",
    cuerpo:
      "Rellena los datos de tu oficina (oficina DI/MER, teléfono de movimientos, medio de transporte…) para que los trámites salgan con la información correcta para OVZ.net.",
    cta: { label: "Ir a Configuración", to: "/configuracion" },
  },
  {
    id: "ganadero",
    Icono: UserPlusIcon,
    titulo: "Añade tu primer ganadero",
    cuerpo:
      "Con su nombre, su NIF y, sobre todo, su teléfono de WhatsApp. Ese número es el que permite a Ganera reconocer sus mensajes cuando escriba.",
    cta: { label: "Ir a Ganaderos", to: "/ganaderos" },
  },
  {
    id: "inventario",
    Icono: FileSpreadsheetIcon,
    titulo: "Importa tu inventario",
    cuerpo:
      "Sube tus explotaciones y animales desde un Excel. Así, cuando un ganadero mencione un crotal, Ganera lo reconoce al instante.",
    cta: { label: "Ir a Explotaciones", to: "/explotaciones" },
  },
  {
    id: "bandeja",
    Icono: MessageCircleIcon,
    titulo: "Revisa la bandeja",
    cuerpo:
      "Cuando un ganadero escriba al número de Ganera, su mensaje aparece en la bandeja con el trámite ya preparado por la IA, listo para que lo revises.",
    cta: { label: "Abrir la bandeja", to: "/bandeja" },
  },
  {
    id: "aprobar",
    Icono: ClipboardListIcon,
    titulo: "Revisa, aprueba y pásalo a OVZ",
    cuerpo:
      "Abre un trámite, corrige lo que haga falta y pulsa «Aprobar». Después, con la ficha de OVZ copias los datos en OVZ.net y lo marcas como hecho. Ganera no envía nada por su cuenta: el control es tuyo.",
    cta: { label: "Ver los trámites", to: "/tramites" },
  },
  {
    id: "whatsapp",
    Icono: SendIcon,
    titulo: "Responde por WhatsApp",
    cuerpo:
      "Desde cada trámite puedes contestar al ganadero por WhatsApp con un mensaje que tú revisas antes de enviar. Nada sale sin que tú lo apruebes.",
  },
  {
    id: "listo",
    Icono: CircleCheckBigIcon,
    titulo: "¡Todo listo!",
    cuerpo:
      "Ya puedes empezar. En cuanto un ganadero escriba, su mensaje llegará a la bandeja convertido en un trámite. Tienes esta guía siempre a mano desde el botón «Primeros pasos» del inicio.",
  },
];

/** Marca de «ya la ha visto», por usuario (sessionStorage se pierde al cerrar; usamos localStorage). */
function clave(usuarioId: number): string {
  return `ganera.primerosPasos.visto.${usuarioId}`;
}

/** true si este usuario ya cerró/terminó la guía alguna vez (no se vuelve a abrir sola). */
export function guiaYaVista(usuarioId: number): boolean {
  try {
    return localStorage.getItem(clave(usuarioId)) === "true";
  } catch {
    // Si localStorage no está disponible, se comporta como «ya vista»: nunca molesta en bucle.
    return true;
  }
}

/** Deja constancia de que este usuario ya vio la guía, para que no se abra sola otra vez. */
export function marcarGuiaVista(usuarioId: number): void {
  try {
    localStorage.setItem(clave(usuarioId), "true");
  } catch {
    // Sin almacenamiento no se puede recordar; no pasa nada (como mucho, se abriría otra vez).
  }
}

// --- Autoapertura (primera vez) ---
// En producción está activa. Los tests que montan el layout/app NO quieren el overlay encima, así
// que el setup global de tests la desactiva; el test propio de la autoapertura la reactiva.
let autoApertura = true;

export function configurarAutoAperturaPrimerosPasos(activa: boolean): void {
  autoApertura = activa;
}

export function autoAperturaPrimerosPasosActiva(): boolean {
  return autoApertura;
}

// --- Reapertura manual desde cualquier parte (p. ej. un botón «Primeros pasos» en el inicio) ---
// Pub/sub a nivel de módulo (sin eventos de window): simple, testeable y sin dependencia del DOM.
type Oyente = () => void;
const oyentes = new Set<Oyente>();

/** Pide abrir la guía. Lo escucha la guía montada en el layout. */
export function abrirPrimerosPasos(): void {
  for (const oyente of oyentes) oyente();
}

/** Suscribe a las peticiones de apertura manual. Devuelve la función para desuscribirse. */
export function suscribirAperturaPrimerosPasos(oyente: Oyente): () => void {
  oyentes.add(oyente);
  return () => {
    oyentes.delete(oyente);
  };
}
