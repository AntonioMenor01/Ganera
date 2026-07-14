import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { httpClient } from "@/shared/api/httpClient";
import { setAuthToken, setUnauthorizedHandler } from "@/shared/api/authSession";

export interface UsuarioActual {
  id: number;
  email: string;
  nombre: string;
  gestoriaId: number;
  activo: boolean;
}

interface AuthContextValue {
  token: string | null;
  usuario: UsuarioActual | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [usuario, setUsuario] = useState<UsuarioActual | null>(null);

  const logout = useCallback(() => {
    setAuthToken(null);
    setToken(null);
    setUsuario(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(logout);
    return () => setUnauthorizedHandler(null);
  }, [logout]);

  const login = useCallback(async (email: string, password: string) => {
    const { data } = await httpClient.post<{ token: string }>("/auth/login", {
      email,
      password,
    });
    setAuthToken(data.token);
    setToken(data.token);

    const { data: usuarioActual } = await httpClient.get<UsuarioActual>("/auth/me");
    setUsuario(usuarioActual);
  }, []);

  const value = useMemo(
    () => ({ token, usuario, login, logout }),
    [token, usuario, login, logout],
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
