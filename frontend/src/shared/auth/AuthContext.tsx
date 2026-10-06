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
import { httpClient } from "@/shared/api/httpClient";
import {
  getAuthToken,
  restaurarTokenGuardado,
  setAuthToken,
  setUnauthorizedHandler,
} from "@/shared/api/authSession";
import { aErrorApi, type ErrorApi } from "@/shared/api/errores";

export interface UsuarioActual {
  id: number;
  email: string;
  nombre: string;
  gestoriaId: number;
  activo: boolean;
}

/**
 * - `anonima`: no hay sesión (nunca la hubo, caducó o se salió).
 * - `comprobando`: hay un token guardado de antes de la recarga y se está validando con
 *   GET /auth/me. Las rutas protegidas enseñan un estado de carga, nunca el login.
 * - `error-comprobacion`: /auth/me falló por red, 5xx o algo inesperado. El token se conserva
 *   (un corte de red no desloguea) y se ofrece "Reintentar".
 * - `activa`: token validado y usuario cargado.
 */
export type EstadoSesion = "anonima" | "comprobando" | "error-comprobacion" | "activa";

/**
 * Por qué terminó la última sesión, para /login:
 * - `caducada`: un 401 con sesión (al arrancar o en uso). /login enseña el aviso una vez.
 * - `manual`: "Salir". Sin aviso, y RequireAuth no guarda la ruta de vuelta.
 * - `null`: no hay nada que contar (visita normal).
 *
 * Va en el contexto y no en el state del router porque el 401 llega desde httpClient, fuera del
 * router, y porque el state del router vive en history.state: se repetiría al recargar /login o
 * al volver atrás. Aquí es memoria y LoginPage lo consume al montarse, así que sale una sola vez.
 */
export type MotivoCierre = "caducada" | "manual" | null;

interface AuthContextValue {
  estado: EstadoSesion;
  token: string | null;
  usuario: UsuarioActual | null;
  /** Solo en `error-comprobacion`. Se enseña con mensajeDeError(error, "comprobar-sesion"). */
  errorComprobacion: ErrorApi | null;
  motivoCierre: MotivoCierre;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  reintentarComprobacion: () => void;
  /** LoginPage lo llama tras leer el motivo, para que el aviso no se repita. */
  olvidarMotivoCierre: () => void;
  /**
   * RequireAuth lo llama al montarse (devuelve la función para desmontarse). Solo con una ruta
   * protegida en pantalla un 401 deja el aviso de "caducada": si el 401 llega estando en /login, no
   * hay nada que avisar y el aviso no debe quedar pendiente para una visita posterior.
   */
  registrarRutaProtegida: () => () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => restaurarTokenGuardado());
  const [estado, setEstado] = useState<EstadoSesion>(() =>
    token ? "comprobando" : "anonima",
  );
  const [usuario, setUsuario] = useState<UsuarioActual | null>(null);
  const [errorComprobacion, setErrorComprobacion] = useState<ErrorApi | null>(null);
  const [motivoCierre, setMotivoCierre] = useState<MotivoCierre>(null);

  /**
   * true solo con la sesión validada. El 401 de httpClient cierra la sesión únicamente entonces:
   * el de la comprobación inicial lo trata comprobarSesion y el del /auth/me del login lo trata
   * login (y el del propio /auth/login ni siquiera llega, H11).
   */
  const sesionActivaRef = useRef(false);
  /**
   * Descarta respuestas de una comprobación anterior: un reintento, un cierre mientras comprueba o
   * un login empezado mientras la comprobación del arranque seguía pendiente (M1).
   */
  const comprobacionRef = useRef(0);
  /** Cuántas RequireAuth hay montadas (0 o 1 en la práctica). */
  const rutasProtegidasRef = useRef(0);

  const registrarRutaProtegida = useCallback(() => {
    rutasProtegidasRef.current += 1;
    return () => {
      rutasProtegidasRef.current -= 1;
    };
  }, []);

  /** Motivo de un cierre por 401: "caducada" solo si el usuario está en una ruta protegida (M3). */
  const motivoDe401 = useCallback(
    (): MotivoCierre => (rutasProtegidasRef.current > 0 ? "caducada" : null),
    [],
  );

  /** Cierre completo y coherente: memoria, almacenamiento y estado de React a la vez. */
  const cerrarSesion = useCallback((motivo: MotivoCierre) => {
    comprobacionRef.current += 1;
    sesionActivaRef.current = false;
    setAuthToken(null);
    setToken(null);
    setUsuario(null);
    setErrorComprobacion(null);
    setEstado("anonima");
    setMotivoCierre(motivo);
  }, []);

  const logout = useCallback(() => cerrarSesion("manual"), [cerrarSesion]);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      if (sesionActivaRef.current) cerrarSesion(motivoDe401());
    });
    return () => setUnauthorizedHandler(null);
  }, [cerrarSesion, motivoDe401]);

  const comprobarSesion = useCallback(async () => {
    const intento = ++comprobacionRef.current;
    setEstado("comprobando");
    setErrorComprobacion(null);
    try {
      const { data } = await httpClient.get<UsuarioActual>("/auth/me");
      if (intento !== comprobacionRef.current) return;
      sesionActivaRef.current = true;
      setUsuario(data);
      setEstado("activa");
    } catch (err) {
      if (intento !== comprobacionRef.current) return;
      const errorApi = aErrorApi(err);
      if (errorApi.tipo === "no-autorizado") {
        cerrarSesion(motivoDe401());
      } else {
        setErrorComprobacion(errorApi);
        setEstado("error-comprobacion");
      }
    }
  }, [cerrarSesion, motivoDe401]);

  // Una sola comprobación al arrancar, también bajo StrictMode (el ref sobrevive al remontaje).
  const comprobacionInicialHecha = useRef(false);
  useEffect(() => {
    if (comprobacionInicialHecha.current) return;
    comprobacionInicialHecha.current = true;
    if (getAuthToken()) void comprobarSesion();
  }, [comprobarSesion]);

  const reintentarComprobacion = useCallback(() => {
    void comprobarSesion();
  }, [comprobarSesion]);

  const login = useCallback(
    async (email: string, password: string) => {
      // M1: una comprobación del arranque aún pendiente ya no manda; si respondiera 401 después,
      // borraría el token de este login.
      const intento = ++comprobacionRef.current;
      let token: string;
      let usuarioActual: UsuarioActual;
      try {
        ({
          data: { token },
        } = await httpClient.post<{ token: string }>("/auth/login", { email, password }));
        // El token va al módulo antes de /auth/me para que la petición lleve el Bearer.
        setAuthToken(token);
        ({ data: usuarioActual } = await httpClient.get<UsuarioActual>("/auth/me"));
      } catch (err) {
        // M2: un login fallido deja SIEMPRE "sin sesión", haya o no una sesión previa: memoria,
        // almacenamiento y React a la vez, sin aviso (es un error del login, no una caducidad).
        // No se restaura el token anterior: su comprobación puede haber quedado descartada (M1)
        // sin saber si era válido, y quien estaba entrando quizá era otra persona.
        if (intento === comprobacionRef.current) cerrarSesion(null);
        throw err;
      }
      // Un login correcto manda: token (de nuevo, por si algo lo borró mientras esperaba) y estado
      // de React se fijan juntos, y cualquier comprobación anterior queda descartada.
      comprobacionRef.current += 1;
      if (getAuthToken() !== token) setAuthToken(token);
      sesionActivaRef.current = true;
      setToken(token);
      setUsuario(usuarioActual);
      setErrorComprobacion(null);
      setEstado("activa");
      setMotivoCierre(null);
    },
    [cerrarSesion],
  );

  const olvidarMotivoCierre = useCallback(() => setMotivoCierre(null), []);

  const value = useMemo(
    () => ({
      estado,
      token,
      usuario,
      errorComprobacion,
      motivoCierre,
      login,
      logout,
      reintentarComprobacion,
      olvidarMotivoCierre,
      registrarRutaProtegida,
    }),
    [
      estado,
      token,
      usuario,
      errorComprobacion,
      motivoCierre,
      login,
      logout,
      reintentarComprobacion,
      olvidarMotivoCierre,
      registrarRutaProtegida,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth debe usarse dentro de un AuthProvider");
  }
  return context;
}
