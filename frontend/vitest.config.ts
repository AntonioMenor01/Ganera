import { defineConfig, mergeConfig } from "vitest/config"
import viteConfig from "./vite.config.ts"
import { API_BASE_URL } from "./src/test/apiBaseUrl.ts"

// Reutiliza vite.config.ts (plugins y alias "@") y solo añade la parte de test.
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test/setup.ts"],
      include: ["src/**/*.test.{ts,tsx}"],
      // Fija la baseURL de httpClient en tests, aunque exista un .env local con otro valor.
      env: { VITE_API_BASE_URL: API_BASE_URL },
      restoreMocks: true,
    },
  }),
)
