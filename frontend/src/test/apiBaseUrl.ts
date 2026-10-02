/** baseURL que ve httpClient en tests (vitest.config.ts la fija vía VITE_API_BASE_URL). */
export const API_BASE_URL = "http://localhost:8080"

/** URL absoluta para un handler de MSW: apiUrl("/auth/me"). */
export function apiUrl(path: string): string {
  return `${API_BASE_URL}${path}`
}
