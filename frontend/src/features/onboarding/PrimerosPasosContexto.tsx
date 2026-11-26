import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/shared/auth/AuthContext";
import {
  PASOS_GUIA,
  TOTAL_PASOS,
  debeAutoabrir,
  guardarProgreso,
  hayProgresoGuardado,
  leerProgreso,
  suscribirAperturaPrimerosPasos,
  type PasoGuia,
  type ProgresoGuia,
} from "./primerosPasos";
import { detectarPasosAuto, SIN_DETECTAR, type PasosDetectados } from "./deteccionPasos";

export interface PasoEstado {
  paso: PasoGuia;
  indice: number;
  completado: boolean;
}

export interface PrimerosPasosValor {
  /** Hay onboarding en curso (ni terminado ni intacto): la píldora y los coachmarks dependen de esto. */
  activo: boolean;
  abierta: boolean;
  pasos: PasoEstado[];
  indiceActivo: number;
  hechos: number;
  total: number;
  terminada: boolean;
  /** El paso "ovz" (requisito) está hecho. */
  ovzHecho: boolean;
  abrir: () => void;
  cerrar: () => void;
  confirmarPaso: (id: string) => void;
  irAPantalla: (to: string) => void;
  terminar: () => void;
  coachParaPantalla: (pantalla: string) => PasoGuia | null;
  descartarCoach: (id: string) => void;
}

/** Valor por defecto INACTIVO: una pantalla montada sin el proveedor (tests de páginas sueltas) ve
 * el onboarding apagado y no pinta nada ni pide datos. */
const VALOR_INACTIVO: PrimerosPasosValor = {
  activo: false,
  abierta: false,
  pasos: PASOS_GUIA.map((paso, indice) => ({ paso, indice, completado: false })),
  indiceActivo: 0,
  hechos: 0,
  total: TOTAL_PASOS,
  terminada: false,
  ovzHecho: false,
  abrir: () => {},
  cerrar: () => {},
  confirmarPaso: () => {},
  irAPantalla: () => {},
  terminar: () => {},
  coachParaPantalla: () => null,
  descartarCoach: () => {},
};

const Contexto = createContext<PrimerosPasosValor>(VALOR_INACTIVO);

export function usePrimerosPasos(): PrimerosPasosValor {
  return useContext(Contexto);
}

export function PrimerosPasosProvider({ children }: { children: ReactNode }) {
  const { usuario } = useAuth();
  const navigate = useNavigate();
  const usuarioId = usuario?.id ?? null;

  const [progreso, setProgreso] = useState<ProgresoGuia>(() =>
    usuarioId != null ? leerProgreso(usuarioId) : { confirmados: [], coachDescartados: [], terminada: false },
  );
  const [detectados, setDetectados] = useState<PasosDetectados>(SIN_DETECTAR);
  const [abierta, setAbierta] = useState(false);
  const [versionDeteccion, setVersionDeteccion] = useState(0);
  const autoEvaluadaPara = useRef<number | null>(null);

  // Al cambiar de usuario, recarga su progreso.
  useEffect(() => {
    if (usuarioId == null) return;
    setProgreso(leerProgreso(usuarioId));
  }, [usuarioId]);

  // Autoapertura: solo la primera vez de cada usuario (una vez por montaje/usuario).
  useEffect(() => {
    if (usuarioId == null || autoEvaluadaPara.current === usuarioId) return;
    autoEvaluadaPara.current = usuarioId;
    if (debeAutoabrir(usuarioId)) setAbierta(true);
  }, [usuarioId]);

  // Reapertura manual (botón «Primeros pasos» u otros).
  useEffect(() => suscribirAperturaPrimerosPasos(() => setAbierta(true)), []);

  const guardar = useCallback(
    (nuevo: ProgresoGuia) => {
      setProgreso(nuevo);
      if (usuarioId != null) guardarProgreso(usuarioId, nuevo);
    },
    [usuarioId],
  );

  // «En curso»: ni terminado ni intacto. Dispara la detección y enseña píldora/coachmarks.
  const enCurso =
    usuarioId != null && !progreso.terminada && (abierta || hayProgresoGuardado(usuarioId));

  // Detección de los pasos auto con datos reales, mientras el onboarding esté en curso. Se repite al
  // abrir la guía (para ver recién hecho lo que se acaba de configurar) y con refrescos explícitos.
  useEffect(() => {
    if (!enCurso) {
      setDetectados(SIN_DETECTAR);
      return;
    }
    const abort = new AbortController();
    detectarPasosAuto(abort.signal)
      .then((res) => setDetectados(res))
      .catch(() => {});
    return () => abort.abort();
  }, [enCurso, versionDeteccion]);

  // Cada vez que se abre, re-detecta (por si acaba de configurarse algo fuera de la guía).
  useEffect(() => {
    if (abierta) setVersionDeteccion((v) => v + 1);
  }, [abierta]);

  const completado = useCallback(
    (paso: PasoGuia): boolean =>
      paso.deteccion ? detectados[paso.deteccion] : progreso.confirmados.includes(paso.id),
    [detectados, progreso.confirmados],
  );

  const pasos = useMemo<PasoEstado[]>(
    () => PASOS_GUIA.map((paso, indice) => ({ paso, indice, completado: completado(paso) })),
    [completado],
  );

  const hechos = pasos.filter((p) => p.completado).length;
  const indiceActivo = useMemo(() => {
    const i = pasos.findIndex((p) => !p.completado);
    return i === -1 ? TOTAL_PASOS - 1 : i;
  }, [pasos]);
  const ovzHecho = detectados.ovz;

  const abrir = useCallback(() => setAbierta(true), []);

  const cerrar = useCallback(() => {
    setAbierta(false);
    // Guarda el progreso aunque no haya cambiado: deja constancia de que ya interactuó (→ píldora).
    guardar(progreso);
  }, [guardar, progreso]);

  const confirmarPaso = useCallback(
    (id: string) => {
      if (progreso.confirmados.includes(id)) return;
      guardar({ ...progreso, confirmados: [...progreso.confirmados, id] });
    },
    [guardar, progreso],
  );

  const irAPantalla = useCallback(
    (to: string) => {
      setAbierta(false);
      guardar(progreso); // persiste el "en curso" para que la píldora ofrezca continuar
      navigate(to);
    },
    [guardar, navigate, progreso],
  );

  const terminar = useCallback(() => {
    if (!ovzHecho) return; // OVZ es requisito; la guía ya lo impide, esto es la última red.
    setAbierta(false);
    guardar({ ...progreso, terminada: true });
  }, [guardar, ovzHecho, progreso]);

  const descartarCoach = useCallback(
    (id: string) => {
      if (progreso.coachDescartados.includes(id)) return;
      guardar({ ...progreso, coachDescartados: [...progreso.coachDescartados, id] });
    },
    [guardar, progreso],
  );

  const coachParaPantalla = useCallback(
    (pantalla: string): PasoGuia | null => {
      if (!enCurso) return null;
      const paso = PASOS_GUIA.find(
        (p) =>
          p.coach?.pantalla === pantalla &&
          !completado(p) &&
          !progreso.coachDescartados.includes(p.id),
      );
      return paso ?? null;
    },
    [enCurso, completado, progreso.coachDescartados],
  );

  const valor = useMemo<PrimerosPasosValor>(
    () => ({
      activo: enCurso,
      abierta,
      pasos,
      indiceActivo,
      hechos,
      total: TOTAL_PASOS,
      terminada: progreso.terminada,
      ovzHecho,
      abrir,
      cerrar,
      confirmarPaso,
      irAPantalla,
      terminar,
      coachParaPantalla,
      descartarCoach,
    }),
    [
      enCurso,
      abierta,
      pasos,
      indiceActivo,
      hechos,
      progreso.terminada,
      ovzHecho,
      abrir,
      cerrar,
      confirmarPaso,
      irAPantalla,
      terminar,
      coachParaPantalla,
      descartarCoach,
    ],
  );

  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}
