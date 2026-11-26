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
import type { PreferenciaOvz } from "@/features/configuracion/preferenciasOvzApi";

/** Pantallas donde un paso puede detectarse hecho y/o mostrar un coachmark. */
export type PantallaPaso = "/configuracion" | "/ganaderos" | "/explotaciones";

/** Qué dato de solo-lectura marca un paso como hecho (endpoints ya existentes, multi-tenant-safe). */
export type DeteccionPaso = "ovz" | "ganadero" | "inventario";

/** Un paso de la guía. Un paso con `deteccion` se marca hecho solo con los datos reales (no se
 * "confirma" a mano); el resto (explicativos) se marcan al confirmarlos. `coach` es el aviso breve
 * que sale en su pantalla mientras el onboarding esté incompleto. */
export interface PasoGuia {
  id: string;
  Icono: LucideIcon;
  titulo: string;
  cuerpo: string;
  /** Acción que lleva a la pantalla real para hacer el paso. */
  cta?: { label: string; to: string };
  /** Si está, el paso se detecta hecho con ese dato (no se confirma a mano). */
  deteccion?: DeteccionPaso;
  /** El paso "ovz" es REQUISITO para terminar el onboarding. */
  requisito?: boolean;
  /** Coachmark contextual en una pantalla concreta (mismo modelo, no textos sueltos). */
  coach?: { pantalla: PantallaPaso; titulo: string; cuerpo: string };
}

/**
 * Los 8 pasos del onboarding guiado. Explicativos + navegan a la pantalla real (no duplican
 * formularios ni capturan datos que el backend no guarda todavía, p. ej. credenciales de login de
 * OVZ, aplazadas). Tres pasos se DETECTAN hechos con datos reales (ovz, ganadero, inventario); el
 * resto se confirman al verlos. "ovz" es requisito para terminar.
 */
export const PASOS_GUIA: PasoGuia[] = [
  {
    id: "bienvenida",
    Icono: HandIcon,
    titulo: "Te damos la bienvenida a Ganera",
    cuerpo:
      "Ganera convierte los WhatsApp de tus ganaderos en trámites listos para revisar. Esta guía te deja todo preparado y recuerda por dónde ibas: puedes salir a hacer cada paso y volver a continuar.",
  },
  {
    id: "ovz",
    Icono: SettingsIcon,
    titulo: "Configura tus datos de OVZ",
    cuerpo:
      "Rellena los datos de tu oficina en las Preferencias para OVZ (oficina, tipo de responsable, teléfono de movimientos…) para que los trámites salgan con la información correcta para OVZ.net. Es imprescindible para terminar la configuración.",
    cta: { label: "Ir a Configuración", to: "/configuracion" },
    deteccion: "ovz",
    requisito: true,
    coach: {
      pantalla: "/configuracion",
      titulo: "Primeros pasos · Datos de OVZ",
      cuerpo:
        "Rellena y guarda las Preferencias para OVZ (al menos tu oficina y el tipo de responsable). Con eso, Ganera podrá preparar los trámites con tus datos.",
    },
  },
  {
    id: "ganadero",
    Icono: UserPlusIcon,
    titulo: "Añade tu primer ganadero",
    cuerpo:
      "Con su nombre, su NIF y, sobre todo, su teléfono de WhatsApp. Ese número es el que permite a Ganera reconocer sus mensajes cuando escriba.",
    cta: { label: "Ir a Ganaderos", to: "/ganaderos" },
    deteccion: "ganadero",
    coach: {
      pantalla: "/ganaderos",
      titulo: "Primeros pasos · Tu primer ganadero",
      cuerpo:
        "Añade un ganadero con su teléfono de WhatsApp. Ese número es lo que permite a Ganera reconocer sus mensajes.",
    },
  },
  {
    id: "inventario",
    Icono: FileSpreadsheetIcon,
    titulo: "Importa tu inventario",
    cuerpo:
      "Sube tus explotaciones y animales desde un Excel. Así, cuando un ganadero mencione un crotal, Ganera lo reconoce al instante.",
    cta: { label: "Ir a Explotaciones", to: "/explotaciones" },
    deteccion: "inventario",
    coach: {
      pantalla: "/explotaciones",
      titulo: "Primeros pasos · Importa tu inventario",
      cuerpo:
        "Importa tus explotaciones y animales desde un Excel para que Ganera reconozca los crotales que mencionen tus ganaderos.",
    },
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

export const TOTAL_PASOS = PASOS_GUIA.length;

// ---------------------------------------------------------------------------------------------
// OVZ configurado: helper derivado reutilizable (requisito del paso "ovz"). Los campos mínimos son
// los que la ficha de OVZ (features/tramites/fichaOvz.ts) usa para pre-rellenar un trámite; de
// todos ellos se exigen los de la gestoría más transversales (no los situacionales de transporte).
// DECISIÓN DE PRODUCTO A CONFIRMAR con Antonio/Álvaro: el conjunto exacto. Cambiar la lista aquí.

/** Campos mínimos de las Preferencias para OVZ para dar el paso "ovz" por hecho. */
export const CAMPOS_OVZ_MINIMOS: readonly (keyof PreferenciaOvz)[] = ["oficinaDi", "tipoResponsable"];

function relleno(valor: string | null | undefined): boolean {
  return typeof valor === "string" && valor.trim() !== "";
}

/** true si las Preferencias para OVZ tienen rellenos los campos mínimos (null = no configurado). */
export function ovzConfigurado(prefs: PreferenciaOvz | null | undefined): boolean {
  if (!prefs) return false;
  return CAMPOS_OVZ_MINIMOS.every((campo) => relleno(prefs[campo]));
}

// ---------------------------------------------------------------------------------------------
// Progreso por usuario (localStorage). Antes era un booleano "visto"; ahora guarda qué pasos se han
// confirmado a mano, qué coachmarks se han descartado y si el onboarding se dio por terminado. Los
// pasos con `deteccion` NO se guardan aquí: se derivan de los datos reales en cada carga.

export interface ProgresoGuia {
  /** Ids de pasos SIN deteccion confirmados a mano (los explicativos). */
  confirmados: string[];
  /** Ids de pasos cuyo coachmark se ha descartado. */
  coachDescartados: string[];
  /** El usuario dio el onboarding por terminado (llegó a "listo"). */
  terminada: boolean;
}

const PROGRESO_VACIO: ProgresoGuia = { confirmados: [], coachDescartados: [], terminada: false };

function claveProgreso(usuarioId: number): string {
  return `ganera.primerosPasos.progreso.${usuarioId}`;
}

/** Clave del booleano "visto" antiguo (onboarding v1): si estaba a true, no se vuelve a autoabrir. */
function claveVistoAntiguo(usuarioId: number): string {
  return `ganera.primerosPasos.visto.${usuarioId}`;
}

export function leerProgreso(usuarioId: number): ProgresoGuia {
  try {
    const crudo = localStorage.getItem(claveProgreso(usuarioId));
    if (!crudo) return { ...PROGRESO_VACIO };
    const datos = JSON.parse(crudo) as Partial<ProgresoGuia>;
    return {
      confirmados: Array.isArray(datos.confirmados) ? datos.confirmados.filter((x) => typeof x === "string") : [],
      coachDescartados: Array.isArray(datos.coachDescartados)
        ? datos.coachDescartados.filter((x) => typeof x === "string")
        : [],
      terminada: datos.terminada === true,
    };
  } catch {
    return { ...PROGRESO_VACIO };
  }
}

export function guardarProgreso(usuarioId: number, progreso: ProgresoGuia): void {
  try {
    localStorage.setItem(claveProgreso(usuarioId), JSON.stringify(progreso));
  } catch {
    // Sin almacenamiento no se recuerda; la guía se comporta como si todo fuera de esta sesión.
  }
}

/** true si ya hay progreso guardado para ese usuario (ha interactuado alguna vez). */
export function hayProgresoGuardado(usuarioId: number): boolean {
  try {
    return localStorage.getItem(claveProgreso(usuarioId)) !== null;
  } catch {
    return false;
  }
}

/** La guía se autoabre solo la primera vez: sin progreso guardado y sin el "visto" antiguo. */
export function debeAutoabrir(usuarioId: number): boolean {
  if (!autoApertura) return false;
  try {
    if (localStorage.getItem(claveVistoAntiguo(usuarioId)) === "true") return false;
  } catch {
    return false;
  }
  return !hayProgresoGuardado(usuarioId);
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

// --- Reapertura manual desde cualquier parte (p. ej. el botón «Primeros pasos» del inicio) ---
// Pub/sub a nivel de módulo (sin eventos de window): simple, testeable y sin dependencia del DOM.
type Oyente = () => void;
const oyentes = new Set<Oyente>();

/** Pide abrir la guía (opcionalmente por un paso concreto, si algún día hace falta). */
export function abrirPrimerosPasos(): void {
  for (const oyente of oyentes) oyente();
}

export function suscribirAperturaPrimerosPasos(oyente: Oyente): () => void {
  oyentes.add(oyente);
  return () => {
    oyentes.delete(oyente);
  };
}
